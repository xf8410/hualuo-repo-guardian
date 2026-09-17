package com.hualuo.repotool.courier

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.hualuo.engine.transfer.CourierFile
import com.hualuo.engine.transfer.CourierOutcome
import com.hualuo.engine.transfer.FileAdmission
import com.hualuo.engine.transfer.FileCandidate
import com.hualuo.engine.transfer.FileCourierClient
import com.hualuo.repotool.HualuoApplication
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.state.CourierPick

/**
 * 文件投递的接线层（0.7.0 第三刀 B 段）：把长任务页选中的文件/目录真投到私有仓。
 *
 * 分工照备份那套桥：状态层只发动作请求（纯 JVM 可测），这里认 ContentResolver 与
 * DocumentFile（安卓件不进状态层），RootScreen 在后台线程里调 [run]——大会计 IO 不进主线程。
 *
 * 流程：选中的 URI 收集成候选（单文件 stat、目录树 DocumentFile 递归）-> 准入
 * （FileAdmission：没有类型白名单，一条都没有，拒的只有「读不到/不是文件/名字放不进」这类硬事实）
 * -> FileCourierClient 按文件边界打 zip 分卷逐卷投递 -> manifest.json 全账 ->
 * 结果连同收集报告一律出声（收了几个、跳过几个、落在哪里），不许装糊涂。
 *
 * 内存纪律（全仓红线）：从头到尾不把整卷或整文件读进内存——文件内容只在引擎打卷与
 * 上传时按流过路；这里最多拿的是每个文件的 stat（名字/大小）。
 */
object CourierDelivery {

    /** 目录树递归的候选数上限：到数就停并明说（不静默吞掉多出来的）。 */
    private const val MAX_COLLECT_FILES = 2_000

    /** 目录树递归的深度上限：防自指与超深树把收集拖死。 */
    private const val MAX_DEPTH = 32

    /**
     * 一轮完整投递（同步，调用方负责丢到后台线程）。
     * 配置在 requestCourierDeliver 里已经闸过，这里再核一遍是防桥上串门的兜底；
     * 无论怎么收场，进度行写 null 收行、动作桥清空、结论出声，三样一样不许少。
     */
    fun run(context: Context, kernel: HualuoApplication, state: AppUiState) {
        val picks = state.courierPicks
        val repo = state.courierRepo()
        val token = state.courierToken()
        if (picks.isEmpty() || repo == null || token.isEmpty()) {
            state.finishCourier("投递没跑起来：批是空的或目标没配全（仓/令牌在设置「文件投递」里）")
            state.toast(state.courierNote ?: "")
            state.clearPendingDataAction()
            return
        }
        val slash = repo.indexOf('/')
        val owner = repo.substring(0, slash)
        val name = repo.substring(slash + 1)
        val branch = state.courierBranch()
        val prefix = AppUiState.buildCourierPrefix(System.currentTimeMillis())
        state.beginCourier()
        kernel.courierProgress = "正在收集文件……"
        val outcome = deliverAll(context, kernel, picks, owner, name, branch, token, prefix, repo)
        kernel.courierProgress = null
        state.finishCourier(outcome)
        state.toast(outcome)
        state.clearPendingDataAction()
    }

    /** 收集 + 准入 + 投递。返回给 [AppUiState.courierNote] 与 toast 的收场话。 */
    private fun deliverAll(
        context: Context,
        kernel: HualuoApplication,
        picks: List<CourierPick>,
        owner: String,
        name: String,
        branch: String,
        token: String,
        prefix: String,
        repo: String,
    ): String {
        val uriByCandidate = HashMap<FileCandidate, String>()
        val candidates = ArrayList<FileCandidate>()
        var truncated = false
        for ((index, pick) in picks.withIndex()) {
            val uri = Uri.parse(pick.uri)
            if (pick.isTree) {
                val root = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
                if (root == null) {
                    return "选的目录读不进来（授权没拿到或已被系统收回）：换个目录重选一次"
                }
                truncated = truncated or collectTree(root, "", candidates, uriByCandidate, "p$index")
            } else {
                val single = runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull()
                if (single == null || !single.canRead()) {
                    return "选的文件读不进来（授权没拿到或已被系统收回）：重选一次"
                }
                val label = single.name ?: ""
                val candidate = FileCandidate(
                    path = label,
                    name = label,
                    sizeBytes = single.length(),
                    readable = single.canRead(),
                    isRegularFile = true,
                    insideGrant = true,
                )
                candidates += candidate
                uriByCandidate[candidate] = single.uri.toString()
            }
            if (candidates.size >= MAX_COLLECT_FILES) {
                truncated = true
                break
            }
        }

        if (candidates.isEmpty()) {
            return "目录里没收集到文件：要么是空目录，要么授权已被系统收回——重选一次试试"
        }

        val report = FileAdmission().collect(candidates)
        if (report.admitted.isEmpty()) {
            return buildString {
                append("没有一个文件能收进来")
                if (report.skipped.isNotEmpty()) {
                    append("：").append(
                        report.skipped.take(3).joinToString("；") { "${it.reason.name}（${it.path}）" },
                    )
                }
            }
        }

        // 内容只在引擎打卷/上传时按流过路：这里把「候选对应哪条开流地址」交给引擎件
        val files = report.admitted.map { candidate ->
            val uri = uriByCandidate[candidate]
                ?: return "内部账对不上（候选没有对应的 URI），这批没投：重选一次再试"
            CourierFile(candidate.name, candidate.sizeBytes) {
                context.contentResolver.openInputStream(Uri.parse(uri))
                    ?: throw RuntimeException("打不开（授权可能已失效）：${candidate.path}")
            }
        }

        val client = FileCourierClient(
            uploader = FileCourierClient.defaultUploader(owner, name, branch, token),
            onProgress = { done, total -> kernel.courierProgress = "正在投递卷 $done/$total" },
        )
        val outcome = runCatching { client.deliver(files, prefix) }.getOrElse {
            return "投递没跑成（${it.javaClass.simpleName}：${it.message ?: "出错"}）：原因已如实在上，稍后整批重投即可"
        }
        return when (outcome) {
            is CourierOutcome.Ok -> buildString {
                append("投递完成：")
                append(report.summary())
                append("；打成 ").append(outcome.volumeCount).append(" 卷（原始 ")
                append(outcome.totalRawBytes).append(" 字节）")
                append("；落在 ").append(repo).append(" 的 ").append(prefix)
                if (truncated) {
                    append("；注意：目录太大，只收了前 ").append(MAX_COLLECT_FILES)
                    append(" 个，多出来的没收（要全量请分小目录再投）")
                }
            }
            is CourierOutcome.Failed -> buildString {
                append("投递失败：").append(outcome.reason)
                append("；收集账：").append(report.summary())
            }
        }
    }

    /**
     * 递归收集一棵目录树：目录下逐个 stat（名字/大小），文件收进 [out]、开流地址记进
     * [uriByCandidate]。返回这棵树有没有被上限截断。子路径只用来给人看与防重名记账，
     * 开流走的是每个文件自己的 document URI（不拼路径字符串去赌）。
     */
    private fun collectTree(
        dir: DocumentFile,
        prefix: String,
        out: MutableList<FileCandidate>,
        uriByCandidate: MutableMap<FileCandidate, String>,
        pickKey: String,
    ): Boolean {
        if (out.size >= MAX_COLLECT_FILES) return true
        if (prefix.count { it == '/' } + 1 > MAX_DEPTH) return true
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return false
        var truncated = false
        for (child in children) {
            if (out.size >= MAX_COLLECT_FILES) return true
            val label = child.name ?: ""
            if (child.isDirectory) {
                val next = if (prefix.isEmpty()) label else "$prefix/$label"
                truncated = truncated or collectTree(child, next, out, uriByCandidate, pickKey)
            } else if (child.isFile) {
                val path = if (prefix.isEmpty()) "$pickKey/$label" else "$pickKey/$prefix/$label"
                val readable = child.canRead()
                val candidate = FileCandidate(
                    path = path,
                    name = label,
                    sizeBytes = child.length(),
                    readable = readable,
                    isRegularFile = true,
                    insideGrant = true,
                )
                out += candidate
                uriByCandidate[candidate] = child.uri.toString()
            }
        }
        return truncated
    }
}
