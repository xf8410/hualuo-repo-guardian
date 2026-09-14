package com.hualuo.repotool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.CardBg
import com.hualuo.repotool.ui.theme.ChevGray
import com.hualuo.repotool.ui.theme.DangerTint
import com.hualuo.repotool.ui.theme.ErrRed
import com.hualuo.repotool.ui.theme.Hairline
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.OkGreen
import com.hualuo.repotool.ui.theme.PageShadow
import com.hualuo.repotool.ui.theme.Scrim
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.theme.SwitchOff
import com.hualuo.repotool.ui.theme.ToneColors
import com.hualuo.repotool.ui.theme.WarnAmber
import com.hualuo.repotool.ui.model.Tone

/** 四态灯/徽标共用色板：n=中性灰（原型 .dot.n / .badge 默认色）。 */
fun toneColor(tone: Tone): Color = when (tone) {
    Tone.Ok -> OkGreen
    Tone.Warn -> WarnAmber
    Tone.Err -> ErrRed
    Tone.Neutral -> ToneColors.NeutralDot
}

@Composable
fun Dot(tone: Tone, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(toneColor(tone)),
    )
}

/** 白圆角卡（v13 .card：18px 圆角 + 13/15 内边距 + 浅投影）。 */
@Composable
fun HCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 11.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        content = content,
    )
}

@Composable
fun CardTitle(text: String) {
    Text(text, fontSize = 12.5.sp, color = SubInk, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 7.dp))
}

/** 徽标（.badge）：等宽小字，语义色只染文字。 */
@Composable
fun BadgeChip(text: String, tone: Tone = Tone.Neutral) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF4F6FA))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    ) {
        Text(text, fontSize = 11.sp, color = toneColor(tone).takeIf { tone != Tone.Neutral } ?: SubInk)
    }
}

/** 通用「左标题 右等宽值 + 可选灯/箭头」行（.lrow / .frow 共用骨架）。 */
@Composable
fun LRow(
    label: String,
    value: String? = null,
    dot: Tone? = null,
    chevron: Boolean = false,
    monoValue: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Dot(dot)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 14.sp, color = Ink)
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                fontSize = 12.5.sp,
                color = SubInk,
                fontFamily = if (monoValue) FontFamily.Monospace else FontFamily.Default,
                textAlign = TextAlign.End,
            )
        }
        if (chevron) {
            Text("\u203A", color = ChevGray, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** 40x24 胶囊开关（v13 .switch：关=浅灰底白钮靠左，开=主色底白钮靠右）。 */
@Composable
fun SwitchPill(on: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) Accent else SwitchOff)
            .clickable(onClick = onToggle),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(3.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** 分段选择（v13 .lv）：一行等宽块，选中=主色描边浅蓝底。 */
@Composable
fun SegRow(options: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == sel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Color(0xFFE3ECFF) else CardBg)
                    .border(1.dp, if (on) Accent else Hairline, RoundedCornerShape(10.dp))
                    .clickable { onSel(i) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    o,
                    fontSize = 12.5.sp,
                    color = if (on) Accent else SubInk,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 标题 + 滑条 + 当前值（v13 .frow 里的 range）。 */
@Composable
fun SliderRow(label: String, min: Float, max: Float, value: Float, onValue: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, color = Ink)
            Spacer(Modifier.weight(1f))
            Text(
                if (max <= 2f) String.format("%.2f", value) else value.toInt().toString(),
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
                color = Ink,
            )
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onValue,
            valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
        )
    }
}

/** 底部弹层骨架：遮罩 + 从输入区升起的白卡（原型 .sheet：max-height 70%，grab 条）。 */
@Composable
fun SheetScaffold(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Scrim)
                .clickable(onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(CardBg)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Hairline),
            )
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** 确认框（v13 .confirm：280 宽居中卡，红字删除钮）。 */
@Composable
fun ConfirmDialog(state: AppUiState) {
    if (!state.confirmOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Scrim)
            .clickable(onClick = { state.confirmOpen = false }),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardBg)
                .clickable(enabled = false) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.confirmText, fontSize = 15.sp, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "消息与附件引用一并删除，导出过的备份不受影响",
                fontSize = 12.sp,
                color = SubInk,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF3F4F6))
                        .clickable { state.confirmOpen = false },
                    contentAlignment = Alignment.Center,
                ) { Text("取消", fontSize = 14.sp, color = Ink) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DangerTint)
                        .clickable { state.confirmAction?.invoke() },
                    contentAlignment = Alignment.Center,
                ) { Text("删除", fontSize = 14.sp, color = ErrRed, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
