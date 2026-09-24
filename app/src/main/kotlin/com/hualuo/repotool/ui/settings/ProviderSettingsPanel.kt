package com.hualuo.repotool.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.engine.api.ProviderCatalog
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.state.ModelSettingsRuntime
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.CardBg
import com.hualuo.repotool.ui.theme.ErrRed
import com.hualuo.repotool.ui.theme.Hairline
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk

@Composable
fun ProviderSettingsPanel(state: AppUiState) {
    val models = ModelSettingsRuntime.current() ?: return
    var selectedId by remember { mutableStateOf(models.settings.activeProviderId) }
    var newName by remember { mutableStateOf("") }
    var newBase by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }
    val selected = models.settings.provider(selectedId) ?: models.settings.providers.firstOrNull()
    val definition = selected?.let { ProviderCatalog.byId(it.id) }
    var baseDraft by remember(selectedId) { mutableStateOf(selected?.baseUrl.orEmpty()) }
    var keyDraft by remember(selectedId) { mutableStateOf(selected?.apiKey.orEmpty()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("内置提供商")
        models.settings.providers.filter { !it.custom }.forEach { provider ->
            ProviderRow(models.displayProviderName(provider.id), if (models.isConfigured(provider.id)) "已配置" else "未配置", models.isConfigured(provider.id), provider.id == selectedId) {
                selectedId = provider.id; models.selectProvider(provider.id)
            }
        }
        SectionLabel("自定义提供商")
        models.settings.providers.filter { it.custom }.forEach { provider ->
            ProviderRow(provider.id, provider.baseUrl, provider.baseUrl.isNotBlank(), provider.id == selectedId) {
                selectedId = provider.id; models.selectProvider(provider.id)
            }
        }
        SectionLabel("添加自定义提供商")
        SettingInput("名称", newName, "例如：bai2 网关") { newName = it }
        SettingInput("base URL", newBase, "https://example.com/v1") { newBase = it }
        SettingInput("API 密钥", newKey, "可以留空", secret = true) { newKey = it }
        ActionButton("添加提供商") {
            val error = models.addCustomProvider(newName, newBase, newKey)
            if (error != null) state.toast(error) else {
                selectedId = newName.trim(); newName = ""; newBase = ""; newKey = ""; state.toast("自定义提供商已加入")
            }
        }
        if (selected != null) {
            SectionLabel("当前提供商：${models.displayProviderName(selected.id)}")
            Text(if (definition == null) "OpenAI 兼容端点" else "协议：${definition.protocol.name}", fontSize = 12.sp, color = SubInk, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
            SettingInput("base URL", baseDraft, definition?.defaultBaseUrl.orEmpty()) { baseDraft = it; models.configureProvider(selected.id, it, keyDraft) }
            SettingInput("API 密钥", keyDraft, if (definition?.keyRequired == true) "必填" else "本地端点可以留空", secret = true) { keyDraft = it; models.configureProvider(selected.id, baseDraft, it) }
            ActionButton(if (models.busyProviderId == selected.id) "正在拉模型" else "从这家拉模型") { models.refreshProvider(selected.id) }
            models.errors[selected.id]?.let { Text(it, fontSize = 12.sp, color = ErrRed, modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)) }
            if (selected.custom) ActionButton("删除这家自定义提供商") { models.deleteCustomProvider(selected.id)?.let(state::toast) }
        }
    }
}

@Composable
private fun ProviderRow(name: String, detail: String, configured: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) Color(0xFFF0F5FF) else CardBg).border(1.dp, if (selected) Accent else Hairline, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink); Text(detail, fontSize = 12.sp, color = SubInk) }
        Text(if (configured) "已配置" else "未配置", fontSize = 11.sp, color = if (configured) Accent else SubInk)
    }
}
