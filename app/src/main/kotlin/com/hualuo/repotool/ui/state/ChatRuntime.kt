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
import com.hualuo.engine.store.SessionStore
import com.hualuo.engine.store.StoredMsg
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
 * **会话落盘（M2 接线）**：store 不为 null 时，每条消息在**收场瞬间**追加一行进
 * SessionStore 的 JSONL——不押「退出那一刻」（系统杀进程根本不走那个路径，旧 Agora
 * 「退回来消息不见」修不好就修在押错了时机）。启动回读也是**同步**的（restoreFromStore，
 * 没有异步首读，就没有白屏和「多进几次才出来」的土壤）。落盘出任何岔子写进
 * [storeIssue] 由界面 toast 出声，不当静默。
 *
 * 家规对齐：
 *  - 「生成中」只是徽标文字，不是转圈图形；错误卡走系统红样式，text 就是
 *    GenerationError.userMessage()——出路写在脸上，不弹窗、不静默。
 *  - 每换一条消息新建 transport 与槽：停止键掐的是这一条自己的连接，
 *    不许牵连上一条已完成的。
 *  - [worker] 注入点让 JVM 测试能同步跑完一整条链（单测不 sleep 等线程）。
 *
 * Kotlin 规矩记牢（都是 CI 抓过的）：
 *  - 尾随 lambda 永远绑**最后一个**参数——本类最后一个是 clock，
 *    调用方传开关必须具名 `autoRetryCostly = {...}`，不许偷懒尾随；
 *  - **跨模块的 public 属性判空后不智能转换**（:engine 的 ModelListing.error 在 :app
 *    眼里随时可能被别的模块改值）——先接进局部变量再用；
 *  - **Result.getOrDefault 只管「失败了」，不管「里面装着 null」**：`runCatching { x?.y() }`
 *    出来的是 Result<Boolean?>，x 为 null 且没抛异常时 getOrDefault(false) 递回来的
 *    还是 null——可空调用先解包成非空再进 runCatching（第五课）；
 *  - **改函数签名要全量过一遍调用点**：appendLineLocked 从带默认参数改成全显式时，
 *    两处调用点一处漏传、一处参数序颠倒——CI 编译段连抓三次才齐（第六课）。
 */
class ChatRuntime(
    private val persist: UiPersistence,
    val autoRetryCostly: () -> Boolean = { RETRY_COSTLY_DEFAULT },
    private val transportFactory: () -> WireTransport = ::UrlConnTransport,
    private val worker: (Thread) -> Unit = { it.start() },
    /** 会话仓；null = 没接库（纯 JVM 测试与降级路径），一切照内存版走。 */
    private val store: SessionStore? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** 真说过的话（含演示兜底由界面决定，这里只有真数据）。 */
    var messages by mutableStateOf(emptyList<ChatMsg>())
        private set

    var busy by mutableStateOf(false)
        private set

    /** 当前挂着的会话文件 id；null = 还没在盘上开过户。 */
    var sessionId: String? = null
        private set

    /** 落盘出岔子时的人话（带原因）；界面 toast 后必须调 [clearStoreIssue] 取走。 */
    var storeIssue by mutableStateOf<String?>(null)
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
            // 用户话当场落一行（不押退出时机）；顺带给没标题的会话补上「首条话截字」当标题
            ensureSessionLocked(model, text)
        }
        val body = Runnable { runGeneration(profile, historyToSend) }
        worker(Thread(body).apply { name = "hualuo-chat" })
    }

    /** 停止：掐进行中的那一条（幂等，没在跑就是空操作）。 */
    fun stop() {
        activeSlot?.stop()
    }

    /**
     * 从盘上接一个会话（同步，无异步首读）。返回 null = 没接成（没库或文件没了）；
     * 接成了返回条数与坏行数，坏行**必须出声**不许悄悄丢。
     */
    fun restoreFromStore(id: String): SessionRestoreNote? {
        val s = store ?: return null
        val loaded = s.load(id) ?: return null
        val head = loaded.head
        val modelBadge = head?.model?.takeIf { it.isNotBlank() } ?: "历史"
        val restored = loaded.messages.map { m ->
            when (m.role) {
                StoredMsg.ROLE_USER ->
                    ChatMsg(who = emptyList(), time = fmt(m.atMs), text = m.text, fromMe = true)
                StoredMsg.ROLE_ERROR ->
                    ChatMsg(who = listOf(Badge("系统", Tone.Err)), time = fmt(m.atMs), text = m.text, isError = true)
                else ->
                    // 半截标记存的是整卡原文（含「别当成品」那行），照原样回摆，不冒充成品
                    ChatMsg(who = listOf(Badge(modelBadge, Tone.Neutral)), time = fmt(m.atMs), text = m.text)
            }
        }
        synchronized(lock) {
            sessionId = id
            messages = restored
        }
        return SessionRestoreNote(restored.size, loaded.badLines, head == null)
    }

    /** 开新会话：内存清空、换户头。id 传 null = 回到「没接库」状态（删掉了当前会话时用）。 */
    fun startFreshSession(id: String?) {
        synchronized(lock) {
            sessionId = id
            messages = emptyList()
        }
    }

    /** 取走落盘岔子（取走即清），配合 RootScreen 的 toast 出声。 */
    fun clearStoreIssue() {
        storeIssue = null
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
            // 收场即落一行：存的是屏上那张卡的原文（半截也照存 + incomplete 标记），
            // 重开摆回来的就是用户当时看见的东西，一字不差。
            persistSettleLocked(finished, isErrorCard = error != null)
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

    /** 没接库就不动盘；接了库就把当前户头补上（首次发话时创建 + 补标题 + 落用户行）。 */
    private fun ensureSessionLocked(model: String, firstUserText: String) {
        val s = store ?: return
        if (sessionId == null) {
            val created = runCatching { s.create(model) }.getOrElse {
                storeIssue = "新会话没建成（${it.message ?: "写盘出错"}）：这轮对话只在屏上，重开会丢"
                return
            }
            sessionId = created
            // 首条话截字当标题（截几个字是调用方的权，家规）；补不了标题不影响聊天
            runCatching { s.rename(created, firstUserText.take(16)) }
        }
        // 走到这 sessionId 必非空（要么本来就有，要么刚建好；建失败早 return 了）
        val sid = sessionId ?: return
        appendLineLocked(StoredMsg(StoredMsg.ROLE_USER, firstUserText, clock()), sid, s)
    }

    /**
     * 收场那行：错误/半截存 error 角色（喂模型时永远剔掉），成品存 assistant。
     * isErrorCard 传「error != null」即可：error 卡落 error 角色；error 为 null 时
     * 最后那张卡必是成品（空收场在上面已兜底成错误卡，但那条走的是 error != null 路径）。
     */
    private fun persistSettleLocked(finished: String, isErrorCard: Boolean) {
        if (!isErrorCard && finished.isEmpty()) return // 双保险：不落空行
        val sid = sessionId ?: return
        val s = store ?: return
        val card = messages.lastOrNull() ?: return
        val stored = StoredMsg(
            role = if (isErrorCard) StoredMsg.ROLE_ERROR else StoredMsg.ROLE_ASSISTANT,
            text = card.text,
            atMs = clock(),
            incomplete = isErrorCard && finished.isNotEmpty(),
        )
        appendLineLocked(stored, sid, s)
    }

    /** 落一行；失败必须出声（storeIssue），不许静默丢字。可空性在调用点已解干净。 */
    private fun appendLineLocked(msg: StoredMsg, sid: String, s: SessionStore) {
        // 可空调用先解包再进 runCatching（第五课）：别让 null 藏在 Result 里骗过 getOrDefault。
        val ok = runCatching { s.append(sid, msg) }.getOrDefault(false)
        if (!ok) storeIssue = "这条没存上（会话文件写不进）：正文还在屏上，但重开就丢"
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

    private fun now(): String = fmt(clock())

    private fun fmt(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

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

/** 一次会话回读的回执：条数照报、坏行照报、头坏了单独说，谁都不许悄悄丢。 */
data class SessionRestoreNote(
    val count: Int,
    val badLines: Int,
    val headMissing: Boolean,
)
