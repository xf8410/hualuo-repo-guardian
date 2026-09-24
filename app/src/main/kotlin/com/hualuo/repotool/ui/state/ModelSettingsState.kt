package com.hualuo.repotool.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.engine.api.ModelRef
import com.hualuo.engine.api.ProviderCatalog
import com.hualuo.engine.api.ProviderClient
import com.hualuo.engine.api.ProviderProfile
import com.hualuo.engine.api.ProviderProtocol
import com.hualuo.engine.api.ProviderSession
import com.hualuo.engine.api.UrlConnTransport
import com.hualuo.engine.api.WireTransport
import com.hualuo.engine.generation.GenerationSlot
import com.hualuo.engine.generation.IdleWatchdog
import com.hualuo.engine.settings.CustomModel
import com.hualuo.engine.settings.ModelSettings
import com.hualuo.engine.settings.ModelSettingsCodec
import com.hualuo.engine.settings.ProviderSettings

data class AvailableModel(
    val id: String,
    val providerId: String,
    val providerName: String,
    val modelName: String,
    val alias: String?,
    val enabled: Boolean,
    val custom: Boolean = false,
)

class ModelSettingsState(
    private val persist: UiPersistence,
    private val transportFactory: () -> WireTransport = ::UrlConnTransport,
    private val worker: (Thread) -> Unit = { it.start() },
    private val changed: () -> Unit = {},
) {
    var settings: ModelSettings by mutableStateOf(loadInitial())
        private set
    var busyProviderId: String? by mutableStateOf(null)
        private set
    var errors: Map<String, String> by mutableStateOf(emptyMap())
        private set
    private val refreshQueue = ArrayDeque<String>()

    val activeProvider: ProviderSettings?
        get() = settings.provider(settings.activeProviderId)

    fun selectProvider(id: String) {
        if (settings.provider(id) != null) update { it.copy(activeProviderId = id) }
    }

    fun configureProvider(id: String, baseUrl: String, apiKey: String): Boolean {
        if (settings.provider(id) == null) return false
        update { current -> current.copy(providers = current.providers.map { if (it.id == id) it.copy(baseUrl = baseUrl.trim(), apiKey = apiKey) else it }) }
        return true
    }

    fun addCustomProvider(name: String, baseUrl: String, apiKey: String): String? {
        val id = name.trim()
        if (id.isEmpty()) return "提供商名称不能为空"
        if (id.contains(':')) return "提供商名称不能含冒号，冒号留给模型分隔"
        if (ProviderCatalog.byId(id) != null || settings.provider(id) != null) return "已经有同名的提供商"
        if (baseUrl.isBlank()) return "base URL 不能为空"
        update { it.copy(providers = it.providers + ProviderSettings(id, true, baseUrl.trim(), apiKey), activeProviderId = id) }
        return null
    }

    fun deleteCustomProvider(id: String): String? {
        val provider = settings.provider(id) ?: return "这家提供商已经不在了"
        if (!provider.custom) return "内置提供商不能删，只能清空地址和密钥"
        val fallback = settings.providers.firstOrNull { it.id != id }?.id.orEmpty()
        update {
            it.copy(
                providers = it.providers.filterNot { item -> item.id == id },
                activeProviderId = if (it.activeProviderId == id) fallback else it.activeProviderId,
                availableModels = it.availableModels - id,
                enabledModels = it.enabledModels.filterNot { model -> ModelRef.parse(model).providerId == id }.toSet(),
                aliases = it.aliases.filterKeys { ModelRef.parse(it).providerId != id },
                customModels = it.customModels.filterNot { model -> model.providerId == id },
            )
        }
        return null
    }

    /** Agora 式自定义模型：名字 + 模型 id + 挂靠提供商，请求走该提供商的 base 与密钥。 */
    fun addCustomModel(providerId: String, modelName: String, alias: String): String? {
        val provider = settings.provider(providerId.trim()) ?: return "先选一家已有的提供商"
        val name = modelName.trim()
        if (name.isEmpty()) return "模型 id 不能为空"
        if (name.contains(':')) return "模型 id 不能含冒号"
        if (name.length > 128) return "模型 id 太长"
        val cleanAlias = alias.trim()
        if (cleanAlias.length > 64) return "别名太长"
        val id = "${provider.id}:$name"
        if (settings.customModels.any { it.id == id }) return "这个模型已经加过了"
        update { current ->
            val merged = (current.availableModels[provider.id].orEmpty() + id).distinct()
            current.copy(
                customModels = current.customModels + CustomModel(id, provider.id, name, cleanAlias),
                availableModels = current.availableModels + (provider.id to merged),
                enabledModels = current.enabledModels + id,
                aliases = if (cleanAlias.isBlank()) current.aliases else current.aliases + (id to cleanAlias),
            )
        }
        return null
    }

    fun deleteCustomModel(modelId: String): String? {
        val target = settings.customModels.firstOrNull { it.id == modelId } ?: return "这个自定义模型已经不在了"
        update { current ->
            current.copy(
                customModels = current.customModels.filterNot { it.id == modelId },
                availableModels = current.availableModels.mapValues { (providerId, rows) ->
                    if (providerId == target.providerId) rows.filterNot { it == modelId } else rows
                }.filterValues { it.isNotEmpty() },
                enabledModels = current.enabledModels - modelId,
                aliases = current.aliases - modelId,
            )
        }
        return null
    }

    fun setModelEnabled(modelId: String, enabled: Boolean) {
        update { current -> current.copy(enabledModels = if (enabled) current.enabledModels + modelId else current.enabledModels - modelId) }
    }

    fun setAlias(modelId: String, alias: String) {
        update { current ->
            val next = current.aliases.toMutableMap()
            if (alias.isBlank()) next.remove(modelId) else next[modelId] = alias.trim()
            current.copy(aliases = next)
        }
    }

    fun availableModels(): List<AvailableModel> = buildList {
        val customIds = settings.customModels.map { it.id }.toSet()
        settings.availableModels.toSortedMap().forEach { (providerId, ids) ->
            ids.sorted().forEach { id ->
                val parsed = ModelRef.parse(id)
                add(AvailableModel(id, providerId, displayProviderName(providerId), parsed.model, settings.aliases[id], id in settings.enabledModels, id in customIds))
            }
        }
    }.sortedWith(compareBy({ it.providerName }, { it.modelName }))

    fun selectedModels(): List<AvailableModel> = availableModels().filter { it.enabled }
    fun displayProviderName(id: String): String = ProviderCatalog.byId(id)?.displayName ?: id

    fun isConfigured(id: String): Boolean {
        val provider = settings.provider(id) ?: return false
        val definition = ProviderCatalog.byId(id)
        val base = provider.baseUrl.ifBlank { definition?.defaultBaseUrl.orEmpty() }
        if (base.isBlank()) return false
        return definition?.keyRequired != true || provider.apiKey.isNotBlank()
    }

    fun sessionFor(rawModel: String): ProviderSession? {
        val parsed = ModelRef.parse(rawModel)
        val providerId = parsed.providerId.ifBlank { settings.activeProviderId }
        val provider = settings.provider(providerId) ?: return null
        val definition = ProviderCatalog.byId(providerId)
        val baseUrl = provider.baseUrl.ifBlank { definition?.defaultBaseUrl.orEmpty() }
        if (baseUrl.isBlank()) return null
        return ProviderSession(
            ProviderProfile(displayProviderName(providerId), baseUrl, provider.apiKey, parsed.model.ifBlank { rawModel }),
            definition?.protocol ?: ProviderProtocol.OPENAI_COMPAT,
        )
    }

    fun refreshProvider(providerId: String) {
        if (busyProviderId != null || refreshQueue.isNotEmpty()) {
            if (providerId !in refreshQueue) refreshQueue.addLast(providerId)
            return
        }
        refreshQueue.addLast(providerId)
        drainQueue()
    }

    fun refreshAll() {
        if (busyProviderId != null || refreshQueue.isNotEmpty()) return
        settings.providers.map { it.id }.forEach { refreshQueue.addLast(it) }
        drainQueue()
    }

    private fun drainQueue() {
        val providerId = refreshQueue.removeFirstOrNull() ?: return
        val session = sessionFor("$providerId:__hualuo_model_list__")
        if (session == null) {
            errors = errors + (providerId to "先在提供商页填 base URL")
            drainQueue()
            return
        }
        if (!isConfigured(providerId)) {
            errors = errors + (providerId to "这家还没配好：内置提供商需要密钥，本地端点需要地址")
            drainQueue()
            return
        }
        busyProviderId = providerId
        errors = errors - providerId
        val body = Runnable {
            val client = ProviderClient(transportFactory(), GenerationSlot(), IdleWatchdog(IdleWatchdog.TRANSFER_IDLE_MS))
            val listing = client.listModels(session)
            val error = listing.error
            busyProviderId = null
            if (error != null) errors = errors + (providerId to error.userMessage())
            else if (listing.models.isEmpty()) errors = errors + (providerId to "端点回话正常，但没有认出任何模型名")
            else {
                val customIds = settings.customModels.filter { it.providerId == providerId }.map { it.id }
                val prefixed = (listing.models.map { "$providerId:${it.removePrefix("models/")}" } + customIds).distinct()
                update { current -> current.copy(availableModels = current.availableModels + (providerId to prefixed)) }
            }
            drainQueue()
        }
        worker(Thread(body).apply { name = "hualuo-model-sync" })
    }

    private fun update(transform: (ModelSettings) -> ModelSettings) {
        val next = transform(settings)
        if (next == settings) return
        settings = next
        persist.save(ModelSettingsCodec.KEY, ModelSettingsCodec.encode(next))
        changed()
    }

    private fun loadInitial(): ModelSettings {
        val stored = persist.load(ModelSettingsCodec.KEY)
        if (!stored.isNullOrBlank()) return ModelSettingsCodec.decode(stored)
        return ModelSettingsCodec.migrateLegacy(persist.load(ChatRuntime.KEY_NAME), persist.load(ChatRuntime.KEY_BASE_URL), persist.load(ChatRuntime.KEY_API_KEY))
    }
}
