package com.hualuo.engine.api

import com.hualuo.engine.generation.GenerationSlot
import com.hualuo.engine.generation.IdleWatchdog
import com.hualuo.engine.http.FailureClass
import com.hualuo.engine.http.RequestCost
import com.hualuo.engine.http.RetryDecision
import com.hualuo.engine.http.RetryPolicy
import com.hualuo.engine.toolcalls.AssembledToolCall
import com.hualuo.engine.toolcalls.ToolSpec
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 一家提供商的接线所需的最小画像。
 *
 * 密钥就这一个字段、明文（D-10 拍板）：往外带的一切（日志、报错、界面）都必须先过
 * 零脱敏纪律：错误原文一字不改直通，任何一层都不打码。
 */
data class ProviderProfile(
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

/** 历史里一条工具调用的回填形状（assistant 消息里的 tool_calls 条目）。 */
data class WireToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/**
 * 一条对话消息（role 用 OpenAI 词汇：system/user/assistant/tool）。
 *
 * 工具协议的两处扩展（都有默认值，老调用点一字不动）：
 *  - assistant 消息带 [toolCalls]：模型上一轮要调的活，回填历史时原样带上；
 *  - tool 消息带 [toolCallId]：某次工具执行的结果，id 对上才认账。
 */
data class ChatTurn(
    val role: String,
    val content: String,
    val toolCalls: List<WireToolCall> = emptyList(),
    val toolCallId: String? = null,
)

/**
 * 一轮对话的收场（tool_calls 协议下的三态）：
 *  - [Text]：这轮是纯文本收场（文本段在流式过程中已经逐段交给 onText，这里不再重放）；
 *  - [Calls]：模型要调工具，[calls] 装配完整；
 *  - [Failed]：带出路的错。
 */
sealed class ChatOutcome {
    object Text : ChatOutcome()
    data class Calls(val calls: List<AssembledToolCall>) : ChatOutcome()
    data class Failed(val error: GenerationError) : ChatOutcome()
}

/**
 * OpenAI 兼容客户端：**设置里填一家，这里就能真干活的那层**。
 *
 * 站在接线层（[ChatWireRunner]）肩膀上，只补它不管的四件事：
 *  1) URL 怎么拼：base 没版本段补 /v1，带就不重复（BaseUrlResolver 的规矩，
 *     主机名里的 v1 不算版本段那条 bug 的修法在这里生效）；
 *  2) 请求体怎么长：model、messages、stream:true，温度和 max_tokens 只在给了的时候才进体
 *     （没给就别替用户拍对方模型的默认值）；给了工具清单就带 tools 数组；
 *  3) 工具协议消息形状：assistant 带 tool_calls（content 照给，多数网关要这个键在）、
 *     tool 带 tool_call_id——拼错一处，工具回合就会原地打转；
 *  4) 拉模型列表：GET /models 的三种形状（data 数组、裸数组、models 字段）都认，
 *     认不出来的形状如实报空并带错误，不许编一份假列表糊弄界面。
 *
 * 失败一律走 [ChatOutcome.Failed]（装着 GenerationError）：出路话术全在分类那边，这里不重复造句子。
 */
class OpenAiCompatClient(
    private val transport: WireTransport,
    private val slot: GenerationSlot,
    private val policy: RetryPolicy,
    private val watchdog: IdleWatchdog,
    private val sleeper: (Long) -> Unit = ::napQuietly,
) {

    /**
     * 流式对话（带工具协议版）：文本段逐次喂 [onText]；[tools] 非空就带 tools 清单。
     * 返回三态（[ChatOutcome]）——模型要调工具时不是错误，更不许当成功。
     */
    fun chatTurns(
        profile: ProviderProfile,
        history: List<ChatTurn>,
        tools: List<ToolSpec> = emptyList(),
        temperature: Double? = null,
        maxTokens: Int? = null,
        onText: (String) -> Unit,
    ): ChatOutcome {
        if (profile.baseUrl.isBlank()) {
            return ChatOutcome.Failed(GenerationError.Configuration("「${profile.name}」没填 base URL，没东西可发"))
        }
        if (history.none { it.content.isNotBlank() || it.toolCalls.isNotEmpty() || it.role == "tool" }) {
            return ChatOutcome.Failed(GenerationError.Configuration("这条对话是空的，不花这个钱"))
        }
        val request = WireRequest(
            url = BaseUrlResolver.endpoint(BaseUrlResolver.withV1(profile.baseUrl), CHAT_SUFFIX),
            method = "POST",
            headers = headersFor(profile, acceptSse = true),
            body = chatBody(profile, history, temperature, maxTokens, tools),
        )
        val runner = ChatWireRunner(
            transport = transport,
            slot = slot,
            policy = policy,
            watchdog = watchdog,
            providerLabel = profile.name,
            sleeper = sleeper,
        )
        return when (val result = runner.run(request, RequestCost.Costly, onText)) {
            is ChatRunResult.Ok -> ChatOutcome.Text
            is ChatRunResult.ToolCalls -> ChatOutcome.Calls(result.calls)
            is ChatRunResult.Failed -> ChatOutcome.Failed(result.error)
        }
    }

    /**
     * 老形状的流式对话（不带工具清单）：给老调用点与老测试留的窄口。
     * 返回 null 表示完整收场的纯文本；模型回了工具调用就按形状异常报错——
     * 没带工具清单却回工具调用，当成功（null）或当文本都说不通。
     */
    fun chat(
        profile: ProviderProfile,
        history: List<ChatTurn>,
        temperature: Double? = null,
        maxTokens: Int? = null,
        onText: (String) -> Unit,
    ): GenerationError? = when (
        val outcome = chatTurns(profile, history, emptyList(), temperature, maxTokens, onText)
    ) {
        is ChatOutcome.Text -> null
        is ChatOutcome.Calls -> GenerationError.Configuration(
            "模型回了工具调用，但这轮请求没带工具清单（形状异常）：按错误报，别装没看见",
        )
        is ChatOutcome.Failed -> outcome.error
    }

    /** 模型列表的收场：要么有名单，要么有说明；error 为 null 时 models 可能仍为空（对方真的一家没有）。 */
    data class ModelListing(val models: List<String>, val error: GenerationError?)

    /**
     * 拉模型列表（免费请求：按政策的免费档允许自动重来，与花钱请求的克制正好相反）。
     * 不占生成槽：列个表不该把「正在生成」的排队挡在外面。
     */
    fun listModels(profile: ProviderProfile): ModelListing {
        if (profile.baseUrl.isBlank()) {
            return ModelListing(emptyList(), GenerationError.Configuration("「${profile.name}」没填 base URL"))
        }
        val request = WireRequest(
            url = BaseUrlResolver.endpoint(BaseUrlResolver.withV1(profile.baseUrl), MODELS_SUFFIX),
            method = "GET",
            headers = headersFor(profile, acceptSse = false),
        )
        var attempt = 1
        while (true) {
            val body = StringBuilder()
            val outcome = try {
                val response = transport.exchange(request) { line ->
                    body.appendLine(line)
                    true
                }
                if (response.status in 200..299) {
                    return ModelListing(parseModelIds(body.toString()), null)
                }
                RetryPolicy.AttemptOutcome(
                    attempt = attempt,
                    cost = RequestCost.Free,
                    status = response.status,
                    body = response.errorBody ?: "",
                    retryAfterMs = response.retryAfterMs,
                )
            } catch (e: IOException) {
                RetryPolicy.AttemptOutcome(
                    attempt = attempt,
                    cost = RequestCost.Free,
                    status = 0,
                    localFailure = if (transport.isCancelled()) FailureClass.Cancelled else FailureClass.NoConnection,
                )
            }
            when (val decision = policy.decide(outcome)) {
                is RetryDecision.Retry -> {
                    attempt = decision.attempt
                    sleeper(decision.waitMs)
                }
                is RetryDecision.GiveUp ->
                    return ModelListing(emptyList(), listingError(decision, outcome))
                RetryDecision.Done ->
                    return ModelListing(emptyList(), GenerationError.Transport(FailureClass.Unknown, "列表的收场没被正确接住"))
            }
        }
    }

    /** 列表失败的说法：有状态码就让供应商分类器带原话，否则用决策表的出路句。 */
    private fun listingError(
        decision: RetryDecision.GiveUp,
        outcome: RetryPolicy.AttemptOutcome,
    ): GenerationError = when {
        decision.failure == FailureClass.Cancelled -> GenerationError.Cancelled
        outcome.status >= 400 -> providerHttpError(outcome.status, outcome.body)
        else -> GenerationError.Transport(decision.failure, decision.reason)
    }

    private fun headersFor(profile: ProviderProfile, acceptSse: Boolean): List<Pair<String, String>> = buildList {
        add("content-type" to "application/json; charset=utf-8")
        if (acceptSse) add("accept" to "text/event-stream")
        if (profile.apiKey.isNotBlank()) add("authorization" to "Bearer ${profile.apiKey}")
    }

    private fun chatBody(
        profile: ProviderProfile,
        history: List<ChatTurn>,
        temperature: Double?,
        maxTokens: Int?,
        tools: List<ToolSpec>,
    ): String = buildJsonObject {
        put("model", profile.model)
        put("stream", true)
        temperature?.let { put("temperature", it) }
        maxTokens?.let { put("max_tokens", it) }
        putJsonArray("messages") {
            history.forEach { turn ->
                // 一条消息的形状：普通 role/content、assistant 带 tool_calls、tool 带 tool_call_id。
                // 全部用标准 JSON DSL，不另开自定义小工具——形状就在这几行里，一眼能对。
                addJsonObject {
                    put("role", turn.role)
                    if (turn.role == "tool") {
                        put("tool_call_id", turn.toolCallId ?: "")
                        put("content", turn.content)
                    } else if (turn.toolCalls.isNotEmpty()) {
                        // content 照给（空串也给）：多数网关要求这个键在，缺了会 400。
                        put("content", turn.content)
                        putJsonArray("tool_calls") {
                            turn.toolCalls.forEach { call ->
                                addJsonObject {
                                    put("id", call.id)
                                    put("type", "function")
                                    putJsonObject("function") {
                                        put("name", call.name)
                                        put("arguments", call.argumentsJson)
                                    }
                                }
                            }
                        }
                    } else {
                        put("content", turn.content)
                    }
                }
            }
        }
        if (tools.isNotEmpty()) {
            putJsonArray("tools") {
                tools.forEach { spec ->
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", spec.name)
                            put("description", spec.description)
                            // schema 原文直接嵌；读不懂的换成空对象 schema——宁可按「无参数」报给对方，
                            // 也不让一次手误把整个请求弄红（拼错的 schema 是本地病，不该让远端买单）。
                            put("parameters", parseSchemaOrEmpty(spec.parametersJson))
                        }
                    }
                }
            }
        }
    }.toString()

    /** schema 原文解析成 JSON 对象；读不懂退回空对象 schema（形状不对的锅记在本地，不炸请求）。 */
    private fun parseSchemaOrEmpty(schemaJson: String): JsonObject =
        runCatching { json.parseToJsonElement(schemaJson) }.getOrNull() as? JsonObject
            ?: buildJsonObject {
                put("type", "object")
                putJsonObject("properties") { }
            }

    private companion object {
        const val CHAT_SUFFIX = "chat/completions"
        const val MODELS_SUFFIX = "models"

        val json = Json { ignoreUnknownKeys = true }

        /**
         * 三种形状的取 id：{"data":[{"id":...}]}（OpenAI 标准）、{"models":[...]}（部分网关）、
         * 裸数组 [ "id" 或 {"id"/"name":...} ]（本地网关常见）。全都对不上就给空列表，
         * 让调用方看见「没认出来」，不许从垃圾里硬抠名字。
         */
        fun parseModelIds(text: String): List<String> {
            val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
            val array: JsonArray = when (root) {
                is JsonArray -> root
                is JsonObject -> (root["data"] as? JsonArray) ?: (root["models"] as? JsonArray) ?: return emptyList()
                else -> return emptyList()
            }
            return array.mapNotNull { element ->
                when (element) {
                    is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                    is JsonObject -> ((element["id"] as? JsonPrimitive)?.contentOrNull
                        ?: (element["name"] as? JsonPrimitive)?.contentOrNull)
                        ?.trim()?.takeIf { it.isNotEmpty() }
                    else -> null
                }
            }.distinct()
        }
    }
}

private fun napQuietly(ms: Long) {
    if (ms <= 0L) return
    try {
        Thread.sleep(ms)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}
