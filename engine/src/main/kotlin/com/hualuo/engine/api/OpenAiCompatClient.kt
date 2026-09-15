package com.hualuo.engine.api

import com.hualuo.engine.generation.GenerationSlot
import com.hualuo.engine.generation.IdleWatchdog
import com.hualuo.engine.http.FailureClass
import com.hualuo.engine.http.RequestCost
import com.hualuo.engine.http.RetryDecision
import com.hualuo.engine.http.RetryPolicy
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject

/**
 * 一家提供商的接线所需的最小画像。
 *
 * 密钥就这一个字段、明文（D-10 拍板）：往外带的一切（日志、报错、界面）都必须先过
 * maskSecrets，这条由 [ChatWireRunner] 与 GenerationError 那边兜底，这里不做假承诺。
 */
data class ProviderProfile(
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

/** 一条对话消息（role 用 OpenAI 词汇：system/user/assistant）。 */
data class ChatTurn(val role: String, val content: String)

/**
 * OpenAI 兼容客户端：**设置里填一家，这里就能真干活的那层**。
 *
 * 站在接线层（[ChatWireRunner]）肩膀上，只补它不管的三件事：
 *  1) URL 怎么拼：base 没版本段补 /v1，带就不重复（BaseUrlResolver 的规矩，
 *     主机名里的 v1 不算版本段那条 bug 的修法在这里生效）；
 *  2) 请求体怎么长：model、messages、stream:true，温度和 max_tokens 只在给了的时候才进体
 *     （没给就别替用户拍对方模型的默认值）；
 *  3) 拉模型列表：GET /models 的三种形状（data 数组、裸数组、models 字段）都认，
 *     认不出来的形状如实报空并带错误，不许编一份假列表糊弄界面。
 *
 * 失败一律返回 GenerationError（null 就是成功）：出路话术全在分类那边，这里不重复造句子。
 */
class OpenAiCompatClient(
    private val transport: WireTransport,
    private val slot: GenerationSlot,
    private val policy: RetryPolicy,
    private val watchdog: IdleWatchdog,
    private val sleeper: (Long) -> Unit = ::napQuietly,
) {

    /** 流式对话：文本段逐次喂 [onText]；返回 null 表示完整收场，否则是带出路的错。 */
    fun chat(
        profile: ProviderProfile,
        history: List<ChatTurn>,
        temperature: Double? = null,
        maxTokens: Int? = null,
        onText: (String) -> Unit,
    ): GenerationError? {
        if (profile.baseUrl.isBlank()) {
            return GenerationError.Configuration("「${profile.name}」没填 base URL，没东西可发")
        }
        if (history.none { it.content.isNotBlank() }) {
            return GenerationError.Configuration("这条对话是空的，不花这个钱")
        }
        val request = WireRequest(
            url = BaseUrlResolver.endpoint(BaseUrlResolver.withV1(profile.baseUrl), CHAT_SUFFIX),
            method = "POST",
            headers = headersFor(profile, acceptSse = true),
            body = chatBody(profile, history, temperature, maxTokens),
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
            is ChatRunResult.Ok -> null
            is ChatRunResult.Failed -> result.error
        }
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
    ): String = buildJsonObject {
        put("model", profile.model)
        put("stream", true)
        temperature?.let { put("temperature", it) }
        maxTokens?.let { put("max_tokens", it) }
        putJsonArray("messages") {
            history.forEach { turn ->
                addJsonObject {
                    put("role", turn.role)
                    put("content", turn.content)
                }
            }
        }
    }.toString()

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
