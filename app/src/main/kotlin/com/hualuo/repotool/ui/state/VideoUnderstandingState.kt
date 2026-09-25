package com.hualuo.repotool.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.engine.api.UrlConnTransport

/**
 * 视频理解状态舱（看视频刀：抽帧计划+分批问答+汇总的编排，纯 JVM）。
 * 从 AppUiState 拆出来（红线三：单文件不过 999 行）——AppUiState 只持有一行。
 *
 * 编排纪律（审查修复定稿）：
 *  - 整条链（抽帧+分批+汇总）在**一条后台线程**里，主线程零视频工作；
 *  - 视觉会话用**聊天当前选定的模型**（不静默换人）；
 *  - 停止位 + transport.cancel()：批间生效、卡住的请求可打断，已读批描述保留；
 *  - 汇总输入每批带【批号+时间范围】标签。
 *
 * 抽帧函数由界面层注入（MediaMetadataRetriever 是 Android 类，进不了状态层），
 * 但执行时刻由这里定。
 */
class VideoUnderstandingState(
    /** 视觉会话来源：聊天当前选定模型的会话；null = 解析不出（如实报）。 */
    private val visionSession: () -> com.hualuo.engine.api.ProviderSession?,
    private val currentModelName: () -> String,
) {

    /** SAF 选中的视频（uri 字符串；纯 JVM 状态不碰 Android 类）。 */
    var videoUri by mutableStateOf<String?>(null)
        private set
    var videoDurationMs by mutableStateOf(0L)
        private set
    /** 抽帧计划（毫秒时间点，引擎算的）。抽帧动作由 [startVideoUnderstanding] 的注入函数做。 */
    var videoFrameTimes by mutableStateOf<List<Long>>(emptyList())
        private set
    /** 抽成的帧（JPEG base64，顺序对齐成功批次的时间点）。 */
    var videoFrames by mutableStateOf<List<String>>(emptyList())
        private set
    var videoBusy by mutableStateOf(false)
        private set
    var videoProgress by mutableStateOf<String?>(null)
        private set
    var videoBatchNotes by mutableStateOf<List<String>>(emptyList())
        private set
    var videoSummary by mutableStateOf<String?>(null)
        private set
    var videoNote by mutableStateOf<String?>(null)
        private set

    /** 用户点了停止：批与批之间生效；卡住的请求靠 transport.cancel() 打断。 */
    @Volatile
    private var videoCancelRequested = false

    /** 当前跑着的 transport（停止按钮用它打断卡住的请求；空闲时 null）。 */
    @Volatile
    private var videoActiveTransport: UrlConnTransport? = null

    /** 选中视频后先定计划：时长进来，帧时间点出来，等「开始理解」。 */
    fun planVideo(uri: String, durationMs: Long) {
        if (videoBusy) return
        videoUri = uri
        videoDurationMs = durationMs
        videoFrameTimes = com.hualuo.engine.vision.VideoPlan.frameTimes(durationMs)
        videoFrames = emptyList()
        videoBatchNotes = emptyList()
        videoSummary = null
        videoNote = if (durationMs <= 0) "视频时长没读出来：可能不是视频文件（或已失效）"
        else "计划 ${videoFrameTimes.size} 帧，选好后点「开始理解」"
    }

    /**
     * 开始理解：抽帧函数由界面层注入，执行时刻由这里定（后台线程）。
     * 时长无效直接拒：单帧瞎抽不是「降级」是浪费 token，按钮层也要同判禁用。
     */
    fun startVideoUnderstanding(extractFrames: (List<Long>) -> List<String?>) {
        if (videoBusy) return
        if (videoDurationMs <= 0 || videoFrameTimes.isEmpty()) {
            videoNote = "视频时长没读出来，没法定抽帧计划：重新选一个视频"
            return
        }
        videoBusy = true
        videoCancelRequested = false
        videoProgress = "抽帧中（${videoFrameTimes.size} 帧）"
        videoNote = null
        videoBatchNotes = emptyList()
        videoSummary = null
        val plannedTimes = videoFrameTimes
        Thread({
            try {
                // 1) 抽帧（与计划等长同序；个别位 null）
                val raw = runCatching { extractFrames(plannedTimes) }.getOrElse { e ->
                    finishVideo("抽帧失败：${e.message ?: e.javaClass.simpleName}")
                    return@Thread
                }
                val pairs = plannedTimes.mapIndexed { i, t -> t to raw.getOrNull(i) }
                    .filter { it.second != null }
                if (pairs.isEmpty()) {
                    finishVideo("一帧都没抽出来：视频可能损坏或格式不支持")
                    return@Thread
                }
                if (videoCancelRequested) {
                    finishVideo("已停止（抽帧阶段）")
                    return@Thread
                }
                val times = pairs.map { it.first }
                val frames = pairs.map { requireNotNull(it.second) }
                videoFrames = frames

                // 2) 会话
                val session = visionSession()
                if (session == null) {
                    finishVideo("当前模型「${currentModelName()}」解析不出可用的提供商会话：去设置-提供商里配好，或换一个支持视觉的模型")
                    return@Thread
                }
                val transport = UrlConnTransport()
                videoActiveTransport = transport

                // 3) 分批读（批与批之间看停止位；卡住的请求 transport.cancel 打断）
                val batches = com.hualuo.engine.vision.VideoPlan.batches(times)
                val done = mutableListOf<Pair<List<Long>, String>>() // 成功批：时间点+描述
                batches.forEachIndexed { bi, batchTimes ->
                    if (videoCancelRequested) {
                        finishVideo("已停止：读完了 ${done.size}/${batches.size} 批（描述都留着）")
                        return@Thread
                    }
                    videoProgress = "读第 ${bi + 1}/${batches.size} 批画面"
                    val batchFrames = batchTimes.mapNotNull { t ->
                        val idx = times.indexOf(t)
                        frames.getOrNull(idx)
                    }
                    when (val outcome = com.hualuo.engine.vision.VisionExec.ask(
                        session, transport, batchFrames,
                        com.hualuo.engine.vision.VideoPlan.describePrompt(bi, batches.size, batchTimes),
                    )) {
                        is com.hualuo.engine.vision.VisionExec.Outcome.Ok ->
                            done += batchTimes to outcome.text
                        is com.hualuo.engine.vision.VisionExec.Outcome.Failed -> {
                            videoBatchNotes = done.map { it.second }
                            finishVideo("第 ${bi + 1} 批没读出来：${outcome.reason}（前 ${done.size} 批描述已保留）")
                            return@Thread
                        }
                    }
                }
                videoBatchNotes = done.map { it.second }

                // 4) 汇总：每批带【批号+时间范围】标签，汇总模型才建得准时间线
                videoProgress = "汇总中"
                val labeled = done.mapIndexed { di, (ts, note) ->
                    "【第 ${di + 1} 批 · ${ts.first() / 1000.0}s 到 ${ts.last() / 1000.0}s】\n$note"
                }.joinToString("\n\n")
                when (val summary = com.hualuo.engine.vision.VisionExec.askText(
                    session, transport,
                    com.hualuo.engine.vision.VideoPlan.summarizePrompt(labeled, null),
                )) {
                    is com.hualuo.engine.vision.VisionExec.Outcome.Ok -> {
                        videoSummary = summary.text
                        finishVideo(null)
                    }
                    is com.hualuo.engine.vision.VisionExec.Outcome.Failed ->
                        finishVideo("画面都读完了，汇总没成：${summary.reason}")
                }
            } catch (e: Exception) {
                finishVideo("执行中断：${e.message ?: e.javaClass.simpleName}")
            } finally {
                videoActiveTransport = null
            }
        }, "hualuo-video-watch").start()
    }

    /** 停止按钮：置停止位 + 打断卡在网络上的请求。收尾统一走 [finishVideo]。 */
    fun stopVideo() {
        if (!videoBusy) return
        videoCancelRequested = true
        videoActiveTransport?.cancel()
        videoProgress = "停止中…"
    }

    /** 统一收尾：忙灯灭、进度清、给一句人话（null = 不追加说明）。 */
    private fun finishVideo(note: String?) {
        videoBusy = false
        videoProgress = null
        if (note != null) videoNote = note
    }

    /** 选了打不开的文件时，界面层给一句人话说明（计划照立，时长为 0）。 */
    fun resetVideoNoteTo(note: String) {
        if (!videoBusy) videoNote = note
    }

    fun resetVideo() {
        if (videoBusy) return
        videoUri = null
        videoDurationMs = 0
        videoFrameTimes = emptyList()
        videoFrames = emptyList()
        videoBatchNotes = emptyList()
        videoSummary = null
        videoNote = null
    }
}
