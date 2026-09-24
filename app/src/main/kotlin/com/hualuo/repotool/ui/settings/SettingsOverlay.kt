package com.hualuo.repotool.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.components.SwitchPill
import com.hualuo.repotool.ui.data.mergedSettingsSections
import com.hualuo.repotool.ui.data.orphanAdditions
import com.hualuo.repotool.ui.data.subPage
import com.hualuo.repotool.ui.model.SubField
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.state.ModelSettingsRuntime
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.CardBg
import com.hualuo.repotool.ui.theme.Hairline
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk

@Composable
fun SettingsOverlay(state: AppUiState) {
    BackHandler(enabled = state.settingsOpen) {
        state.backFromSettings()
    }
    val top = state.subStack.lastOrNull()
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(CardBg).border(1.dp, Hairline, RoundedCornerShape(10.dp)).clickable { state.backFromSettings() }.padding(horizontal = 10.dp, vertical = 6.dp)) { Text("返回", fontSize = 13.sp, color = Ink) }
            Spacer(Modifier.width(12.dp))
            Text(top?.let { subPage(it)?.title } ?: "设置", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
        if (top == null) SettingsHome(state, Modifier.weight(1f)) else SubPageView(state, top, Modifier.weight(1f))
    }
}

@Composable
private fun SettingsHome(state: AppUiState, modifier: Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp).clip(RoundedCornerShape(16.dp)).background(CardBg).padding(horizontal = 14.dp, vertical = 9.dp)) {
            BasicTextField(value = state.settingsQuery, onValueChange = { state.settingsQuery = it }, textStyle = TextStyle(fontSize = 14.sp, color = Ink), modifier = Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
            val q = state.settingsQuery.trim().lowercase()
            val orphans = orphanAdditions()
            if (orphans.isNotEmpty()) {
                Text(
                    "有 ${orphans.size} 项设置挂不到任何组：" + orphans.joinToString("、") { it.title },
                    fontSize = 12.sp,
                    color = Ink,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
            mergedSettingsSections().forEach { section ->
                val hit = section.items.filter { q.isEmpty() || it.title.contains(q, true) || it.desc.contains(q, true) }
                if (hit.isNotEmpty()) {
                    Text(section.title, fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp))
                    hit.forEach { item ->
                        val runtime = ModelSettingsRuntime.current()
                        val value = when (item.subKey) {
                            "provider" -> runtime?.settings?.providers?.count { it.custom || runtime.isConfigured(it.id) }?.toString() ?: item.value
                            "model" -> runtime?.settings?.enabledModels?.size?.let { "启用 $it" } ?: item.value
                            else -> item.value
                        }
                        Row(Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(RoundedCornerShape(18.dp)).background(CardBg).clickable { state.subStack = state.subStack + item.subKey }.padding(14.dp)) {
                            Column(Modifier.weight(1f)) { Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink); Text(item.desc, fontSize = 12.sp, color = SubInk) }
                            value?.let { Text(it, fontSize = 11.sp, color = SubInk, textAlign = TextAlign.End) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubPageView(state: AppUiState, key: String, modifier: Modifier) {
    BackHandler(enabled = state.settingsOpen) {
        state.backFromSettings()
    }
    val page = subPage(key) ?: return
    val switches = remember(key) { mutableStateMapOf<String, Boolean>() }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        page.fields.forEach { f ->
            when (f) {
                is SubField.Sec -> Text(f.text, fontSize = 13.sp, color = Accent, modifier = Modifier.padding(6.dp))
                is SubField.PersistedText -> PersistedTextField(state, f)
                is SubField.PersistedSwitch -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(CardBg).padding(14.dp)) { Text(f.label, modifier = Modifier.weight(1f)); val on = state.flag(f.key, f.defaultOn); SwitchPill(on) { state.setFlag(f.key, !on) } }
                is SubField.Switch -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(CardBg).padding(14.dp)) { Text(f.label, modifier = Modifier.weight(1f)); val on = switches[f.label] ?: f.on; SwitchPill(on) { switches[f.label] = !on } }
                SubField.GithubLogin -> GithubLoginCard(state)
                SubField.ProviderSettings -> ProviderSettingsPanel(state)
                SubField.ModelSettings -> ModelSettingsPanel(state)
                is SubField.Note -> Text(f.text, fontSize = 12.sp, color = SubInk, modifier = Modifier.padding(6.dp))
                is SubField.Button -> Text(f.text, color = Accent, modifier = Modifier.fillMaxWidth().clickable { f.actionKey?.let(state::requestDataAction) ?: state.toast("已提交（演示，接线后生效）") }.padding(14.dp))
                else -> Text(f.toString(), fontSize = 12.sp, color = SubInk, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun PersistedTextField(state: AppUiState, f: SubField.PersistedText) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(CardBg).padding(14.dp)) {
        Text(f.label, fontSize = 13.sp, color = SubInk)
        val v = state.text(f.key)
        if (v.isEmpty()) Text(f.placeholder, fontSize = 13.sp, color = SubInk)
        BasicTextField(value = v, onValueChange = { state.setText(f.key, it) }, visualTransformation = if (f.secret) PasswordVisualTransformation() else VisualTransformation.None, modifier = Modifier.fillMaxWidth())
    }
}
