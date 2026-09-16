package com.hualuo.repotool.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.engine.api.ChatTurn
import com.hualuo.engine.api.OpenAiCompatClient
import com.hualuo.engine.api.ProviderProfile
import com.hualuo.engine.api.UrlConnTransport
import com.hualuo.engine.api.WireTransport
import com.hualuo.engine.generation.GenerationSlot
import com.hualuo.engine.generation.IdleWatchdog
import com.hualuo.engine.http.RetryPolicy
import com.hualuo.repotool.ui.data.RETRY_COSTLY_DEFAULT
import com.hualuo.repotool.ui.data.RETRY_COSTLY_KEY
import com.hualuo.repotool.ui.model.Badge
import com.hualuo.repotool.ui.model.ChatMsg
import com.hualuo.repotool.ui.model.Tone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 回合流的真运行层：**发送不再只是把按钮染红**——这条链是真的网络往返。
 *
 *   读设置里的提供商画像（名字/base/密钥，模型名由界面当场给）
 *   组历史（只取真说过话的用户/助手气泡，错误卡不进历史）
 *   开一条后台线程走 engine 的 OpenAiCompatClient（SSE 逐段上屏、卡死/限流/盲重红线
 *   全在 ChatWireRunner 里，这里不重复立法）
 *   收场把最后一张卡改成成品或错误卡，busy 归位。
 *
 * 家规对齐：
 *  - 「生成中」只是徽标文字，不是转圈图形；错误卡走系统红样式，text 就是
 *    GenerationError.userMessage()——出路写在脸上，不弹窗、不静默。
 *  - 每换一条消息新建 transport 与槽：停止键掐的是这一条自己的连接，
 *    不许牵连上一条已完成的。
 *  - [worker] 注入点让 JVM 测试能同步跑完一整条链（单测不 sleep 等线程）。
 *
 * 参数写法钉两条 Kotlin 规矩（都是 CI 抓过的）：
 *  - 尾随 lambda 永远绑**最后一个**参数——本类最后一个是 clock，
 *    调用方传开关必须具名 `autoRetryCostly = {...}`，不许偷懒尾随；
 *  - **跨模块的 public 属性判空后不智能转换**（:engine 的 ModelListing.error 在 :app
 *    眼里随时可能被别的模块改值）——先接进局部变量再用。
 */
class ChatRuntime(
    private val persist: UiPersistence,
    val autoRetryCostly: () -> Boolean = { RETRY_COSTLY_DEFAULT },
    private val transportFactory: () -> WireTransport = ::UrlConnTransport,
    private val worker: (Thread) -> Unit = { it.start() },
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** 真说过的话（含演示兜底由界面决定，这里只有真数据）。 */
    var messages by mutableStateOf(emptyList<ChatMsg>())
        private set

    var busy by mutableStateOf(false)
        private set

    // ── 端点模型清单（客户端 listModels 的真出口，不是又一份演示表） ──────────

    /** 端点上一次成功返回的模型名列表；没拉过就是空，界面据此决定摆不摆「端点」组。 */
    var remoteModels by mutableStateOf(emptyList<String>())
        private set

    var modelsBusy by mutableStateOf(false)
        private set

    /** 上次拉取失败的人话（含出路）；成功一次就清空。空列表不等于错，这话界面分开说。 */
    var modelsError by mutableStateOf<String?>(null)
        private set

    private val lock = Any()

    /** 只在跑的时候有值：停止键按这个槽掐连接，收场后清空。 */
    @Volatile private var activeSlot: GenerationSlot? = null

    /** 发一条：先把「用户气泡 + 生成中的空助手卡」摆上屏，再开线程真发。 */
    fun send(prompt: String, model: String) {
        val text = prompt.trim()
        if (text.isEmpty()) return
        val history = historySnapshot()
        val profile = profileFor(model)
        val historyToSend = history + ChatTurn("user", text)
        synchronized(lock) {
            if (busy) return
            busy = true
            messages = messages +
                ChatMsg(who = emptyList(), time = now(), text = text, fromMe = true) +
                ChatMsg(
                    who = listOf(Badge(profile.name, Tone.Neutral), Badge("生成中", Tone.Warn)),
                    time = now(),
                    text = "",
                )
            activeSlot = GenerationSlot()
        }
        val body = Runnable { runGeneration(profile, historyToSend) }
        worker(Thread(body).apply { name = "hualuo-chat" })
    }

    /** 停止：掐进行中的那一条（幂等，没在跑就是空操作）。 */
    fun stop() {
        activeSlot?.stop()
    }

    /**
     * 从端点拉模型清单（免费 GET，不占生成槽——列表不该把「正在生成」挡在外面）。
     * 结果三态各归各的家：成功进 [remoteModels]、失败进 [modelsError]（带出路）、
     * 空列表单独说明「对方回话正常但没认出模型名」——不拿空名单装「拉取成功」。
     */
    fun refreshModels() {
        if (modelsBusy) return
        val profile = profileFor("")
        modelsBusy = true
        modelsError = null
        val body = Runnable {
            val listing = runCatching {
                OpenAiCompatClient(
                    transport = transportFactory(),
                    slot = GenerationSlot(),
                    // 列表免费：按政策的免费档允许自动重来一次，与花钱请求的克制正好相反。
                    policy = RetryPolicy(maxAutomaticRetries = 1),
                    watchdog = IdleWatchdog(IdleWatchdog.TRANSFER_IDLE_MS),
                ).listModels(profile)
            }.getOrNull()
            modelsBusy = false
            if (listing == null) {
                modelsError = "拉取失败：内部异常（没碰模型清单）"
                return@Runnable
            }
            // 跨模块 public 属性不配智能转换（CI 编译段抓过）：判空先接局部。
            val failure = listing.error
            when {
                failure != null -> modelsError = failure.userMessage()
                listing.models.isEmpty() -> modelsError = "端点回话正常，但没认出任何模型名：清单没更新"
                else -> remoteModels = listing.models
            }
        }
        worker(Thread(body).apply { name = "hualuo-models" })
    }

    private fun runGeneration(profile: ProviderProfile, history: List<ChatTurn>) {
        val slot = synchronized(lock) { activeSlot ?: GenerationSlot().also { activeSlot = it } }
        val error = try {
            OpenAiCompatClient(
                transport = transportFactory(),
                slot = slot,
                // 花钱请求要不要自动重发，读的是设置页那个真开关（唯一通道 retryPolicyFor 的语义）。
                policy = RetryPolicy(retryOnCostlyRequests = autoRetryCostly()),
                watchdog = IdleWatchdog(IdleWatchdog.GENERATION_IDLE_MS),
            ).chat(profile, history) { chunk -> appendStreaming(chunk) }
        } finally {
            synchronized(lock) { activeSlot = null }
        }
        synchronized(lock) {
            val base = messages.dropLast(1)
            val last = messages.lastOrNull()
            val finished = last?.text ?: ""
            messages = when {
                // 有错：错误卡带出路；已有半截就原文留在卡上，标清「不完整」
                error != null -> base + ChatMsg(
                    who = listOf(Badge("系统", Tone.Err)),
                    time = now(),
                    text = if (finished.isEmpty()) error.userMessage()
                    else error.userMessage() + "\n——已收到的半截（不完整，别当成品）——\n" + finished,
                    isError = true,
                )
                // 没错且有字：成品卡（last 在此分支必非空：finished 非空就来自它）
                last != null && finished.isNotEmpty() -> base + last.copy(
                    who = listOf(Badge(profile.name, Tone.Neutral)),
                )
                // 没错但一个字没有：这是链路说谎，替模型认账是假、替它遮掩更糟
                else -> base + ChatMsg(
                    who = listOf(Badge("系统", Tone.Err)),
                    time = now(),
                    text = "连接正常收场，但一个字都没收到：这句是替链路说的，别当模型答的",
                    isError = true,
                )
            }
            busy = false
        }
    }

    /** 流式追加：每段内容落到最后一张卡上。 */
    private fun appendStreaming(chunk: String) {
        synchronized(lock) {
            if (messages.isEmpty()) return
            val base = messages.dropLast(1)
            val last = messages.last()
            messages = base + last.copy(text = last.text + chunk)
        }
    }

    private fun profileFor(model: String): ProviderProfile = ProviderProfile(
        name = persist.load(KEY_NAME)?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_NAME,
        baseUrl = persist.load(KEY_BASE_URL)?.trim() ?: "",
        apiKey = persist.load(KEY_API_KEY) ?: "",
        model = model,
    )

    /** 历史只取真气泡：错误卡是「我方对失败的说明」，喂回模型等于教它复述错误。 */
    private fun historySnapshot(): List<ChatTurn> =
        messages.filter { !it.isError && it.text.isNotBlank() }
            .map { ChatTurn(role = if (it.fromMe) "user" else "assistant", content = it.text) }
            // 上限先钉一个粗护栏（历史裁剪策略是后面一挂的正事），别无声涨到超限。
            .takeLast(MAX_HISTORY_TURNS)

    private fun now(): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date(clock()))

    companion object {
        /** 提供商名字（进过真机不许改键名，下同）。 */
        const val KEY_NAME = "provider.name"
        const val KEY_BASE_URL = "provider.base_url"
        const val KEY_API_KEY = "provider.api_key"

        const val DEFAULT_NAME = "自定义端点"

        /** 临时护栏：只带最近 40 条进上下文。 */
        const val MAX_HISTORY_TURNS = 40
    }
}
