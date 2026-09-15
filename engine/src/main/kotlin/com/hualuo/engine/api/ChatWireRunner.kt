package com.hualuo.engine.api

import com.hualuo.engine.generation.Cancellable
import com.hualuo.engine.generation.GenerationSlot
import com.hualuo.engine.generation.IdleWatchdog
import com.hualuo.engine.http.FailureClass
import com.hualuo.engine.http.RequestCost
import com.hualuo.engine.http.RetryDecision
import com.hualuo.engine.http.RetryPolicy
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 一次完整运行的结果：成功（文本已逐段交给回调），或者带出路的分类错误。 */
sealed class ChatRunResult {
    object Ok : ChatRunResult()
    data class Failed(val error: GenerationError) : ChatRunResult()
}

/**
 * 流式对话的 HTTP 接线层：**真正碰网络的那一层**。
 *
 * RetryPolicy、GenerationSlot、IdleWatchdog、BoundedWireRead、ProviderHttpError、
 * GenerationError 六件东西之前各自有单元测、各自守一段，在这里第一次合体成完整流程：
 *
 *   占槽，发请求，逐行喂 SSE 解析器（同步喂看门狗、数内容字节），
 *   无论收场是成功、错误、卡死、取消还是行超限，都把事实交给 RetryPolicy，
 *   该等就等再来，该放手就放手翻成「带出路的错」，出门必放槽。
 *
 * 红线（与 RetryPolicy 的文案一一对应，不是口号）：
 *  1) 花钱请求收到过内容字节，绝不自动盲重；
 *  2) 上下文超限永不重发（证据在体里，与状态码无关）；
 *  3) 任何收场都放槽（runGuarded 兜底，被顶替走 stranded 单独出声）。
 *
 * 线程模型：[run] 设计给**后台线程**调用（内部阻塞读流 + sleeper 等待）；
 * 本层不碰协程库（D-03 纯 JVM）。取消靠 [GenerationSlot.stop]：它会调这里的
 * Cancellable，置停旗并掐断 transport。
 */
class ChatWireRunner(
    private val transport: WireTransport,
    private val slot: GenerationSlot,
    private val policy: RetryPolicy,
    private val watchdog: IdleWatchdog,
    /** 出错文案里报「谁的半截话」；接线方按提供商填中文名。 */
    private val providerLabel: String = "对方",
    private val sleeper: (Long) -> Unit = ::sleepQuietly,
) {

    @Volatile private var stopRequested = false

    /**
     * 跑一次完整的「请求，流式交付，失败按策略重来或放弃」。
     * [onText] 会被多次调用（每段内容一次）；返回 [ChatRunResult.Failed] 时一句没交付或半路停。
     */
    fun run(request: WireRequest, cost: RequestCost, onText: (String) -> Unit): ChatRunResult {
        stopRequested = false
        var attempt = 1
        while (true) {
            val claim = slot.tryBegin(
                Cancellable {
                    stopRequested = true
                    transport.cancel()
                },
            ) ?: return ChatRunResult.Failed(
                // 排队策略归调用方：这里先把话说清，不悄悄丢请求也不硬闯并发。
                GenerationError.Configuration("上一条还在生成，槽被占着：这条没发出去。等它收完或先按停止"),
            )
            val guarded = slot.runGuarded(claim) { attemptOnce(request, cost, attempt, onText) }
            val bundle = guarded.value
                ?: return ChatRunResult.Failed(
                    GenerationError.Unknown(guarded.failure ?: IOException("接线层内部异常（不该走到这）")),
                )
            if (guarded.stranded) {
                // 槽已经不归我：说明按过停止或被新任务顶替，这次结果一律不算正常收场。
                return ChatRunResult.Failed(
                    if (stopRequested) GenerationError.Cancelled
                    else GenerationError.IncompleteStream(providerLabel, bundle.stopReason, false, bundle.sawText),
                )
            }
            // 行超限、流中 error 块这类：事实明确，不劳驾决策表，直接出局。
            if (bundle.hardError != null) return ChatRunResult.Failed(bundle.hardError)
            if (stopRequested) return ChatRunResult.Failed(GenerationError.Cancelled)
            when (val decision = policy.decide(bundle.outcome)) {
                is RetryDecision.Retry -> {
                    attempt = decision.attempt
                    sleeper(decision.waitMs)
                    // 新连接从头计：别拿旧连接的静默计时诬陷它（IdleWatchdog.restart 的语义）。
                    watchdog.restart()
                    if (stopRequested) return ChatRunResult.Failed(GenerationError.Cancelled)
                }
                is RetryDecision.GiveUp ->
                    return ChatRunResult.Failed(toError(decision.failure, decision.reason, bundle))
                RetryDecision.Done -> {
                    if (!bundle.finished) {
                        return ChatRunResult.Failed(
                            GenerationError.IncompleteStream(
                                providerLabel, bundle.stopReason, false, bundle.sawText,
                            ),
                        )
                    }
                    // 收尾标记齐全但 finish_reason=length：话说完了是假象，上限吃了后半截。
                    if (bundle.stopReason == "length") {
                        return ChatRunResult.Failed(
                            GenerationError.OutputTruncated(providerLabel, bundle.stopReason),
                        )
                    }
                    return ChatRunResult.Ok
                }
            }
        }
    }

    /**
     * 一次尝试：把所有收场（成功流、非 2xx、卡死、断开、取消、行超限）**都折成事实**，
     * 本方法不抛异常 —— 抛出去的就进不了决策表，等于没分类。
     */
    private fun attemptOnce(
        request: WireRequest,
        cost: RequestCost,
        attempt: Int,
        onText: (String) -> Unit,
    ): AttemptBundle {
        val parser = OpenAiSseParser(onText)
        return try {
            val response = transport.exchange(request) { line ->
                watchdog.beat()
                parser.onLine(line)
            }
            if (response.status in 200..299) {
                AttemptBundle(
                    outcome = RetryPolicy.AttemptOutcome(
                        attempt = attempt,
                        cost = cost,
                        status = response.status,
                        bytesReceived = response.bytesReceived,
                    ),
                    finished = parser.finished,
                    stopReason = parser.finishReason,
                    sawText = parser.sawText,
                    apiError = null,
                    hardError = parser.streamError,
                )
            } else {
                // 非 2xx：bytesReceived 保持 0，错误体不当「内容」触发红线。
                AttemptBundle(
                    outcome = RetryPolicy.AttemptOutcome(
                        attempt = attempt,
                        cost = cost,
                        status = response.status,
                        body = response.errorBody ?: "",
                        retryAfterMs = response.retryAfterMs,
                    ),
                    finished = false,
                    stopReason = null,
                    sawText = false,
                    apiError = providerHttpError(response.status, response.errorBody),
                    hardError = null,
                )
            }
        } catch (e: WireLimitException) {
            // 单行超封顶：对方没按行发或塞了巨型事件。原文不整份带（它就是因太大被停的）。
            AttemptBundle(
                outcome = RetryPolicy.AttemptOutcome(attempt, cost, status = 0, localFailure = FailureClass.Unknown),
                finished = false,
                stopReason = null,
                sawText = parser.sawText,
                apiError = null,
                hardError = GenerationError.SseParse("（一行超过上限，原文没带回来）", e.message ?: "行超限"),
            )
        } catch (e: IOException) {
            val bytes = (e as? WireStreamIOException)?.bytesSoFar ?: 0L
            val timeout = e is SocketTimeoutException ||
                e.cause is SocketTimeoutException ||
                e is InterruptedIOException
            val stopped = stopRequested || transport.isCancelled()
            AttemptBundle(
                outcome = RetryPolicy.AttemptOutcome(
                    attempt = attempt,
                    cost = cost,
                    // 收到过内容说明连接真通过：报 200，让「已花 token」那条规则看见它。
                    status = if (bytes > 0L || parser.sawAnyLine) 200 else 0,
                    bytesReceived = bytes,
                    stalled = timeout && !stopped,
                    localFailure = if (stopped) FailureClass.Cancelled else FailureClass.NoConnection,
                ),
                finished = false,
                stopReason = parser.finishReason,
                sawText = parser.sawText,
                apiError = null,
                hardError = parser.streamError,
            )
        }
    }

    /** 决策表的 GiveUp 翻译成界面能用的错：出路以决策理由为准，供应商原话最多当线索。 */
    private fun toError(klass: FailureClass, reason: String, bundle: AttemptBundle): GenerationError = when {
        klass == FailureClass.Cancelled -> GenerationError.Cancelled
        klass == FailureClass.ContextOverflow -> GenerationError.Api(null, null, reason)
        bundle.outcome.status >= 400 ->
            GenerationError.Network(bundle.outcome.status, bundle.apiError?.message ?: reason)
        else -> GenerationError.Transport(klass, reason)
    }

    private data class AttemptBundle(
        val outcome: RetryPolicy.AttemptOutcome,
        val finished: Boolean,
        val stopReason: String?,
        val sawText: Boolean,
        val apiError: GenerationError.Api?,
        val hardError: GenerationError?,
    )
}

/**
 * OpenAI 兼容的流式行解析：`data: 行` 与裸 JSON 都认（有的网关不写 SSE 前缀），
 * `[DONE]` 或 `finish_reason` 都算语义收尾。只挑 delta.content；reasoning 等别字段将来再加。
 *
 * 中途出现 `{"error":...}` 块（200 外壳里的错误）：记下 [streamError] 并收线 ——
 * 这是「对方 HTTP 说 OK、内容说炸了」的经典形状，当成功返回就是骗人。
 */
class OpenAiSseParser(private val onText: (String) -> Unit) {

    var finished = false
        private set
    var finishReason: String? = null
        private set
    var sawText = false
        private set
    var sawAnyLine = false
        private set
    var streamError: GenerationError? = null
        private set

    private val json = Json { ignoreUnknownKeys = true }

    /** 返回 false 表示别再读了（收尾或流中错误）。 */
    fun onLine(line: String): Boolean {
        sawAnyLine = true
        val t = line.trim()
        if (t.isEmpty() || t.startsWith(":")) return keepGoing()
        if (!t.startsWith("data:")) {
            // 没有 SSE 前缀的裸行：整行当 JSON 试一次（非流式网关就长这样）。
            parsePayload(t)
            return keepGoing()
        }
        val payload = t.substringAfter("data:").trim()
        if (payload == "[DONE]") {
            finished = true
            return false
        }
        parsePayload(payload)
        return keepGoing()
    }

    private fun keepGoing(): Boolean = !finished && streamError == null

    private fun parsePayload(payload: String) {
        if (payload.isEmpty()) return
        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject ?: return
        (root["error"] as? JsonObject)?.let { err ->
            streamError = GenerationError.Api(
                code = (err["code"] as? JsonPrimitive)?.contentOrNull,
                type = (err["type"] as? JsonPrimitive)?.contentOrNull,
                message = (err["message"] as? JsonPrimitive)?.contentOrNull ?: err.toString(),
            )
            return
        }
        val choices = root["choices"] as? JsonArray ?: return
        val choice = choices.firstOrNull() as? JsonObject ?: return
        val delta = (choice["delta"] as? JsonObject) ?: (choice["message"] as? JsonObject)
        val content = delta?.let { (it["content"] as? JsonPrimitive)?.contentOrNull }
        if (!content.isNullOrEmpty()) {
            sawText = true
            onText(content)
        }
        val reason = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
        if (!reason.isNullOrEmpty()) {
            finishReason = reason
            finished = true
        }
    }
}

private fun sleepQuietly(ms: Long) {
    if (ms <= 0L) return
    try {
        Thread.sleep(ms)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}
