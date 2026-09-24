package com.hualuo.repotool.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.engine.api.ModelRef
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.state.ModelSettingsRuntime
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.CardBg
import com.hualuo.repotool.ui.theme.ErrRed
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk

@Composable
fun ModelSettingsPanel(state: AppUiState) {
    val models = ModelSettingsRuntime.current() ?: return
    val drafts = remember { mutableStateMapOf<String, String>() }
    val current = state.currentModel
    val currentLabel = models.settings.aliases[current] ?: ModelRef.parse(current).model.ifBlank { "未选择" }
    val currentProvider = ModelRef.parse(current).providerId
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("默认模型")
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(currentLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(currentProvider.ifBlank { "尚未配置" }, fontSize = 12.sp, color = SubInk)
            }
            Text("聊天输入区使用", fontSize = 11.sp, color = SubInk)
        }
        SectionLabel("可用模型")
        ActionButton(if (models.busyProviderId == null) "从所有提供商同步" else "正在同步") { models.refreshAll() }
        models.errors.forEach { (provider, error) ->
            Text("$provider：$error", fontSize = 12.sp, color = ErrRed, modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp))
        }
        val grouped = models.availableModels().groupBy { it.providerName }
        if (grouped.isEmpty()) Text("还没有拉到模型：先配置提供商，再点上面的同步。", fontSize = 12.5.sp, color = SubInk)
        grouped.forEach { (provider, rows) ->
            SectionLabel("$provider · ${rows.size}")
            rows.forEach { model ->
                val draft = drafts[model.id] ?: model.alias.orEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (model.id == current) Color(0xFFF0F5FF) else CardBg)
                        .clickable {
                            state.currentModel = model.id
                            state.toast("默认模型已切到 ${model.alias ?: model.modelName}")
                        }.padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = model.enabled, onCheckedChange = { models.setModelEnabled(model.id, it) }, colors = CheckboxDefaults.colors(checkedColor = Accent))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model.alias ?: model.modelName, fontSize = 14.sp, color = Ink)
                        BasicTextField(
                            value = draft,
                            onValueChange = { drafts[model.id] = it; models.setAlias(model.id, it) },
                            textStyle = TextStyle(fontSize = 11.sp, color = SubInk),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
