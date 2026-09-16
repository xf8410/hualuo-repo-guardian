package com.hualuo.repotool.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.components.SegRow
import com.hualuo.repotool.ui.components.SliderRow
import com.hualuo.repotool.ui.components.SwitchPill
import com.hualuo.repotool.ui.data.DefaultSearchProviderId
import com.hualuo.repotool.ui.data.mergedSettingsSections
import com.hualuo.repotool.ui.data.orphanAdditions
import com.hualuo.repotool.ui.data.subPage
import com.hualuo.repotool.ui.model.SettingsItem
import com.hualuo.repotool.ui.model.SubField
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.CardBg
import com.hualuo.repotool.ui.theme.ChevGray
import com.hualuo.repotool.ui.theme.ErrRed
import com.hualuo.repotool.ui.theme.Hairline
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk

/**
 * 设置层（#settings + #sub）：主页分组 + 子页栈，**全部用文字，不画图形字符**。
 *
 * 为什么去掉图标（用户 2026-09-15 拍板）：原版界面无表情；一排小图标对认路没用，
 * 反而挤掉正事 —— 每一项那句大白话。撤掉图标之后标题与说明能占满整行，读起来省劲。
 * 数据模型里的 iconKey 先留着不渲染，将来要图标走 vector drawable，那是独立一次设计。
 *
 * 主页搜索框实时过滤（组内无命中则整组隐藏）；「返回」两个字逐级返回（对应子页栈 pop）。
 *
 * 两类开关要分清：演示态 `SubField.Switch` 的状态只活在本次 remember 里（照原型搬来的行，
 * 退出即丢）；真设置 `SubField.PersistedSwitch` 走 `AppUiState` 的按键名通道，改完立刻落盘，
 * 关掉 App 再开还在。文本框同理：演示态 `Input` 记住本地，真设置 `PersistedText` 走
 * text/setText——编辑即生效，落盘由界面按修订号去抖（不一个字写一次盘）。
 * 挂不上组的真设置项由 `orphanAdditions()` 在这一页顶部喊出来。
 *
 * 按钮（SubField.Button）同分两态：actionKey 为空是演示态（点了只说明「接线后生效」）；
 * 非空就经 state.requestDataAction 发动作请求，由根界面开系统选择器/执行真动作。
 *
 * %VERSION% 占位在渲染时替换为注入的版本串（单源链的最后一环）。
 *
 * 家规提醒：单选圆环的 Modifier.size 之前缺 layout.size 的 import，红在 app 编译段
 * （run 34969369167 抓到）。engine 一直先红导致这颗雷压了十几轮没人看见 —— 谁改了布局
 * 调用就当场把 import 带上，别赌"反正后面模块编译不到"。
 */
@Composable
fun SettingsOverlay(state: AppUiState) {
    val top = state.subStack.lastOrNull()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardBg)
                    .border(1.dp, Hairline, RoundedCornerShape(10.dp))
                    .clickable { state.backFromSettings() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("返回", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                top?.let { subPage(it)?.title } ?: "设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            )
        }

        if (top == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (state.settingsQuery.isEmpty()) {
                        Text("搜设置，比如「代理」「备份」", fontSize = 14.sp, color = SubInk)
                    }
                    BasicTextField(
                        value = state.settingsQuery,
                        onValueChange = { state.settingsQuery = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = Ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 26.dp),
            ) {
                // 挂不上组的真设置项：宁可在这里红字喊出来，也不许悄悄少一项设置
                val orphans = orphanAdditions()
                if (orphans.isNotEmpty()) {
                    Text(
                        "有 ${orphans.size} 项设置挂不到任何组（组 id 写错）：" +
                            orphans.joinToString("、") { it.title },
                        fontSize = 12.sp,
                        color = ErrRed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                    )
                }
                val q = state.settingsQuery.trim().lowercase()
                mergedSettingsSections().forEach { sec ->
                    val hit = sec.items.filter {
                        q.isEmpty() ||
                            it.title.lowercase().contains(q) ||
                            it.desc.lowercase().contains(q)
                    }
                    if (hit.isNotEmpty()) {
                        Text(
                            sec.title,
                            fontSize = 13.sp,
                            color = Accent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp, top = 13.dp, bottom = 7.dp),
                        )
                        hit.forEach { item -> SettingsItemRow(state, item) }
                    }
                }
                Text(
                    "每一项都能点开，点开才是真设置",
                    fontSize = 11.sp,
                    color = Color(0xFFA8AFBA),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        } else {
            // 跨函数边界拿不到 ColumnScope 的 weight——CI 实锤过一次（run 34804570780），
            // 所以这里由父级把 Modifier.weight(1f) 传进去，子页自己不再凭空 weight。
            SubPageView(state, top, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SettingsItemRow(state: AppUiState, item: SettingsItem) {
    var sw by remember { mutableStateOf(true) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .clickable { state.subStack = state.subStack + item.subKey }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 原来这里是一枚 36dp 的图标方块：撤掉，标题与说明占满整行
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(item.desc, fontSize = 12.sp, color = SubInk, lineHeight = 17.sp)
        }
        if (item.subKey == "title") {
            SwitchPill(sw) { sw = !sw }
        } else {
            item.value?.let {
                Text(
                    it,
                    fontSize = 11.5.sp,
                    color = SubInk,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(92.dp),
                )
            }
        }
    }
}

@Composable
private fun SubPageView(state: AppUiState, key: String, modifier: Modifier = Modifier) {
    val page = subPage(key) ?: return
    val switches = remember(key) { mutableStateMapOf<String, Boolean>() }
    val segs = remember(key) { mutableStateMapOf<String, Int>() }
    val sliders = remember(key) { mutableStateMapOf<String, Float>() }
    val radioSel = remember(key) { mutableStateOf(DefaultSearchProviderId) }
    val inputs = remember(key) { mutableStateMapOf<String, String>() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(bottom = 26.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        page.fields.forEach { f ->
            when (f) {
                is SubField.Sec -> Text(
                    f.text,
                    fontSize = 13.sp,
                    color = Accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp, top = 13.dp, bottom = 7.dp),
                )
                is SubField.Row -> FRow {
                    Text(f.label, fontSize = 14.sp, color = Ink)
                    Spacer(Modifier.weight(1f))
                    // 版本占位在这里落地：数据文件不写死号，渲染时注入
                    val shown = f.value.replace("%VERSION%", state.versionLabel)
                    if (shown.isNotEmpty()) {
                        Text(shown, fontSize = 12.5.sp, color = SubInk, fontFamily = FontFamily.Monospace)
                    }
                    if (f.gotoKey != null) {
                        val goto = f.gotoKey
                        Text(
                            "打开",
                            fontSize = 12.sp,
                            color = Accent,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { state.subStack = state.subStack + goto },
                        )
                    }
                }
                is SubField.Switch -> FRow {
                    Text(f.label, fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
                    val on = switches[f.label] ?: f.on
                    SwitchPill(on) { switches[f.label] = !on }
                }
                // 真设置开关：值走 AppUiState 的按键名通道，改完立刻落盘并给反馈
                is SubField.PersistedSwitch -> FRow {
                    Text(f.label, fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
                    val on = state.flag(f.key, f.defaultOn)
                    SwitchPill(on) {
                        val next = !on
                        state.setFlag(f.key, next)
                        val failure = state.flushPersistence()
                        when {
                            failure != null -> state.toast("设置没存上：" + failure)
                            next -> state.toast("已打开：网关失败会自动重发一次，这次请求会再花一遍 token")
                            else -> state.toast("已关掉：不替你重发，失败时把原因摊开、重试按钮在你手上")
                        }
                    }
                }
                // 真设置文本框：值走 text/setText 通道。编辑即上屏，落盘由界面按
                // settingsRevision 去抖攒批——打字期间绝一个字写一次盘。
                // secret 只打点显示；「密钥明文进盘」是 D-10 的总决定，不归这里管。
                is SubField.PersistedText -> FRow {
                    Text(f.label, fontSize = 14.sp, color = Ink)
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        val v = state.text(f.key)
                        if (v.isEmpty() && f.placeholder.isNotEmpty()) {
                            Text(
                                f.placeholder,
                                fontSize = 13.sp,
                                color = SubInk,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        BasicTextField(
                            value = v,
                            onValueChange = { state.setText(f.key, it) },
                            singleLine = true,
                            visualTransformation =
                                if (f.secret) PasswordVisualTransformation() else VisualTransformation.None,
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                color = Ink,
                                textAlign = TextAlign.End,
                                fontFamily = if (f.secret) FontFamily.Monospace else FontFamily.Default,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                is SubField.Seg -> Column(Modifier.padding(horizontal = 6.dp)) {
                    Text(f.label, fontSize = 12.5.sp, color = SubInk, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                    val sel = segs[f.label] ?: f.sel
                    SegRow(f.options, sel) { segs[f.label] = it }
                }
                is SubField.Input -> FRow {
                    Text(f.label, fontSize = 14.sp, color = Ink)
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        val v = inputs[f.label] ?: ""
                        if (v.isEmpty() && f.placeholder.isNotEmpty()) {
                            Text(
                                f.placeholder,
                                fontSize = 13.sp,
                                color = SubInk,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        BasicTextField(
                            value = v,
                            onValueChange = { inputs[f.label] = it },
                            textStyle = TextStyle(fontSize = 13.sp, color = Ink, textAlign = TextAlign.End),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                is SubField.Slider -> Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    val v = sliders[f.label] ?: f.value.toFloat()
                    SliderRow(f.label, f.min.toFloat(), f.max.toFloat(), v) { sliders[f.label] = it }
                }
                is SubField.Note -> Text(
                    f.text,
                    fontSize = 12.sp,
                    color = SubInk,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                )
                is SubField.Action -> FRow {
                    // 动作卡原先左侧那枚图形不画了：标题与描述本来就说得清是什么动作
                    Column(modifier = Modifier.weight(1f)) {
                        Text(f.title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(f.desc, fontSize = 12.5.sp, color = SubInk, lineHeight = 19.sp)
                    }
                }
                is SubField.Head -> Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(f.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(f.desc, fontSize = 12.5.sp, color = SubInk)
                }
                is SubField.BigInput -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    val v = inputs[f.placeholder] ?: ""
                    if (v.isEmpty()) Text(f.placeholder, fontSize = 14.sp, color = SubInk)
                    BasicTextField(
                        value = v,
                        onValueChange = { inputs[f.placeholder] = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = Ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is SubField.Button -> Box(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Accent)
                        .clickable {
                            val action = f.actionKey
                            if (action == null) state.toast("已提交（演示，接线后生效）")
                            else state.requestDataAction(action)
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(f.text, color = Color.White, fontSize = 13.5.sp)
                }
                is SubField.Radio -> Column(Modifier.fillMaxWidth()) {
                    Text(
                        f.label,
                        fontSize = 13.sp,
                        color = SubInk,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 6.dp),
                    )
                    f.options.forEach { c ->
                        val on = radioSel.value == c.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 9.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardBg)
                                .clickable { radioSel.value = c.id }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 单选指示：用圆环加文字，不靠颜色也不靠图形字符
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .border(
                                        width = if (on) 5.dp else 1.5.dp,
                                        color = if (on) Accent else ChevGray,
                                        shape = RoundedCornerShape(9.dp),
                                    ),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text(c.desc, fontSize = 12.5.sp, color = SubInk)
                            }
                            if (c.needsKey) Text("需密钥", fontSize = 11.sp, color = SubInk)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
