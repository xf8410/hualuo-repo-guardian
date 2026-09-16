package com.hualuo.repotool.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.components.LRow
import com.hualuo.repotool.ui.components.SegRow
import com.hualuo.repotool.ui.components.SheetScaffold
import com.hualuo.repotool.ui.components.SwitchPill
import com.hualuo.repotool.ui.data.DemoModels
import com.hualuo.repotool.ui.data.DemoTasks
import com.hualuo.repotool.ui.model.ModelRow
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.ChevGray
import com.hualuo.repotool.ui.theme.ErrRed
import com.hualuo.repotool.ui.theme.Hairline
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk

/**
 * 三个原位弹层的总闸：模型 / 本回合工具 / 任务详情（互斥，同原型 closeAll 语义）。
 *
 * 图形字符清零（用户规矩：表情连转义写法都不许进源码）：工具行标签全用文字，
 * 能力徽章用「可/不可」说清楚；将来要配图走 vector drawable，键名在 IconKey。
 *
 * 模型弹层的清单换成真电（2026-09-15）：顶部「从端点拉清单」走 ChatRuntime.refreshModels
 * （免费 GET，不占生成槽）；端点真回过的名字摆在最上层「端点回话」组里，点击即选。
 * listModels 只给名字——上下文与能力徽章一律标「未声明」，不拿演示值冒充真能力；
 * 没拉取过时照旧摆演示表，但明标「演示」。
 */
@Composable
fun SheetsLayer(state: AppUiState) {
    when {
        state.modelSheetOpen -> ModelSheet(state)
        state.toolSheetOpen -> ToolSheet(state)
        state.taskSheetKey != null -> TaskDetailSheet(state, state.taskSheetKey ?: "")
    }
}

@Composable
private fun ModelSheet(state: AppUiState) {
    val chat = state.chat
    val q = state.modelQuery.trim().lowercase()
    val remote = chat.remoteModels.filter { q.isEmpty() || it.lowercase().contains(q) }
    val hit = DemoModels.filter { q.isEmpty() || it.name.lowercase().contains(q) }
    SheetScaffold("切换模型", onDismiss = { state.modelSheetOpen = false }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Bg)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            if (state.modelQuery.isEmpty()) {
                Text("搜索模型…", fontSize = 13.sp, color = SubInk)
            }
            BasicTextField(
                value = state.modelQuery,
                onValueChange = { state.modelQuery = it },
                textStyle = TextStyle(fontSize = 13.sp, color = Ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        // 拉清单钮：忙时变字、错时红字，成功静默（下面清单自己会说话）
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (chat.modelsBusy) Color(0xFFE9EDF3) else Color(0xFFE3ECFF))
                    .clickable(enabled = !chat.modelsBusy) { chat.refreshModels() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    if (chat.modelsBusy) "拉取中…" else "从端点拉清单",
                    fontSize = 12.5.sp,
                    color = if (chat.modelsBusy) SubInk else Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(8.dp))
            chat.modelsError?.let {
                Text(it, fontSize = 11.5.sp, color = ErrRed, modifier = Modifier.weight(1f))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (remote.isNotEmpty()) {
                Text(
                    "端点回话（能力未声明）",
                    fontSize = 11.sp,
                    color = SubInk,
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 3.dp),
                )
                remote.forEach { name ->
                    RemoteModelRow(state, name, selected = name == state.currentModel)
                }
            }
            if (chat.remoteModels.isNotEmpty()) {
                Text(
                    "以下为演示表（端点清单在上面）",
                    fontSize = 11.sp,
                    color = SubInk,
                    modifier = Modifier.padding(start = 2.dp, top = 7.dp, bottom = 3.dp),
                )
            }
            hit.groupBy { it.group }.forEach { (group, rows) ->
                Text(group, fontSize = 11.sp, color = SubInk, modifier = Modifier.padding(start = 2.dp, top = 7.dp, bottom = 3.dp))
                rows.forEach { m -> ModelRowView(state, m) }
            }
            if (hit.isEmpty() && remote.isEmpty()) {
                Text("无匹配", fontSize = 12.sp, color = SubInk, modifier = Modifier.padding(8.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Checkbox(
                checked = state.lockToConversation,
                onCheckedChange = { state.lockToConversation = it },
                colors = CheckboxDefaults.colors(checkedColor = Accent),
                modifier = Modifier.scaleSmall(),
            )
            Text("锁定到本会话（不勾仅影响下一条）", fontSize = 12.5.sp, color = SubInk)
        }
    }
}

/** 端点回来的模型只有名字：徽章如实标「未声明」，不猜 ctx 也不猜工具视觉。 */
@Composable
private fun RemoteModelRow(state: AppUiState, name: String, selected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 7.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFFF0F5FF) else Color(0xFFFAFBFC))
            .border(1.dp, if (selected) Accent else Hairline, RoundedCornerShape(14.dp))
            .clickable {
                state.currentModel = name
                state.modelSheetOpen = false
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
        Spacer(Modifier.weight(1f))
        CapChip("ctx 未声明", bad = false)
        Spacer(Modifier.width(4.dp))
        CapChip("能力未声明", bad = false)
    }
}

/** 带图守门（v13.1 新增）：附件行有东西且模型无视觉，置灰不可选，点它出声说明。 */
private fun ModelRow.visionBlocked(state: AppUiState): Boolean =
    state.thumbs.isNotEmpty() && !hasVision

@Composable
private fun ModelRowView(state: AppUiState, m: ModelRow) {
    val dis = m.visionBlocked(state)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 7.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (m.isDefault) Color(0xFFF0F5FF) else Color(0xFFFAFBFC))
            .border(
                1.dp,
                if (m.isDefault) Accent else Hairline,
                RoundedCornerShape(14.dp),
            )
            .alpha(if (dis) 0.45f else 1f)
            .clickable {
                if (dis) {
                    state.toast("这个模型看不了图：先移除图片附件，或换支持视觉的模型")
                } else {
                    state.currentModel = m.name
                    state.modelSheetOpen = false
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
        Spacer(Modifier.width(4.dp))
        Text(m.group, fontSize = 11.sp, color = SubInk)
        Spacer(Modifier.weight(1f))
        Text("ctx " + m.ctx, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = SubInk)
        Spacer(Modifier.width(6.dp))
        CapChip(if (m.hasTools) "工具可" else "工具不可", bad = !m.hasTools)
        Spacer(Modifier.width(4.dp))
        if (dis) {
            CapChip("视觉不可，带图附件", bad = true)
        } else {
            CapChip(if (m.hasVision) "视觉可" else "视觉不可", bad = !m.hasVision)
        }
    }
}

@Composable
private fun CapChip(text: String, bad: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (bad) Color(0xFFFDECEC) else Color(0xFFEEF1F6))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text, fontSize = 10.sp, color = if (bad) ErrRed else SubInk)
    }
}

@Composable
private fun ToolSheet(state: AppUiState) {
    SheetScaffold("本回合工具", onDismiss = { state.toolSheetOpen = false }) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("思考", fontSize = 14.sp, color = Ink)
                Spacer(Modifier.width(8.dp))
                Text(if (state.thinkOn) "开，档位" + listOf("低", "中", "高", "最高")[state.thinkLevel] else "关", fontSize = 12.5.sp, color = SubInk)
                Spacer(Modifier.weight(1f))
                SwitchPill(state.thinkOn) { state.thinkOn = !state.thinkOn }
            }
            if (state.thinkOn) {
                SegRow(listOf("低", "中", "高", "最高"), state.thinkLevel) { state.thinkLevel = it }
            }
            ToolSwitchRow("网页搜索", state.webSearchOn) { state.webSearchOn = !state.webSearchOn }
            ToolSwitchRow("终端 Shell", state.shellOn) { state.shellOn = !state.shellOn }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("代码执行", fontSize = 14.sp, color = Ink)
                Spacer(Modifier.width(8.dp))
                CapChip("仅 Gemini", bad = false)
                Spacer(Modifier.weight(1f))
                SwitchPill(state.codeExecOn) { state.codeExecOn = !state.codeExecOn }
            }
            ToolSwitchRow("多智能体接力", state.relayOn) { state.relayOn = !state.relayOn }
        }
    }
}

@Composable
private fun ToolSwitchRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = Ink)
        Spacer(Modifier.weight(1f))
        Text(if (on) "开" else "关", fontSize = 12.5.sp, color = SubInk)
        Spacer(Modifier.width(8.dp))
        SwitchPill(on, onToggle)
    }
}

@Composable
private fun TaskDetailSheet(state: AppUiState, key: String) {
    val t = DemoTasks.firstOrNull { it.name == key }
    SheetScaffold(key, onDismiss = { state.taskSheetKey = null }) {
        if (t != null) {
            LRow("状态", t.status)
            LRow("上次运行", t.last)
            LRow("说明", t.note, monoValue = false)
        }
    }
}

private fun Modifier.scaleSmall(): Modifier = this.width(28.dp)
