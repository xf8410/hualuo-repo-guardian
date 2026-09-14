package com.hualuo.repotool.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.data.DemoAttachMenu
import com.hualuo.repotool.ui.data.DemoLoopBar
import com.hualuo.repotool.ui.data.DemoQueueBar
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.CardBg
import com.hualuo.repotool.ui.theme.ErrRed
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.LoopInk
import com.hualuo.repotool.ui.theme.LoopTint
import com.hualuo.repotool.ui.theme.QueueTint
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.theme.ThumbBg
import com.hualuo.repotool.ui.theme.WarnAmber

/**
 * 输入区（v13 .composer）：loop/queue 横幅 + 附件缩略行 + 输入框 + 第二排按钮。
 * 原型拍板的三条都在此：语音在聊天框（不在设置）、模型切换在输入区原位弹层、生成中发送钮变红 ■。
 */
@Composable
fun Composer(state: AppUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(CardBg)
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
    ) {
        if (state.loopBarOn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LoopTint)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(DemoLoopBar, fontSize = 12.sp, color = LoopInk)
                Spacer(Modifier.weight(1f))
                Text(
                    "\u25A0 \u505C",
                    fontSize = 12.sp,
                    color = ErrRed,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        state.loopBarOn = false
                        state.toast("已停止本会话循环（演示）")
                    },
                )
            }
        }
        if (state.queueBarOn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(QueueTint)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(DemoQueueBar, fontSize = 12.sp, color = Accent)
                Spacer(Modifier.weight(1f))
                Text(
                    "移除 \u00D7",
                    fontSize = 12.sp,
                    color = Accent,
                    modifier = Modifier.clickable { state.queueBarOn = false },
                )
            }
        }

        if (state.thumbs.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.thumbs.forEachIndexed { i, icon ->
                    Box(modifier = Modifier.size(44.dp)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ThumbBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(icon, fontSize = 19.sp, color = if (icon == "\u2753") WarnAmber else Ink)
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0x73000000))
                                .clickable { state.removeThumb(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("\u00D7", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Bg)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                if (state.input.isEmpty()) {
                    Text("发消息…（草稿自动存）", fontSize = 14.sp, color = SubInk)
                }
                BasicTextField(
                    value = state.input,
                    onValueChange = { state.input = it },
                    textStyle = TextStyle(fontSize = 14.sp, color = Ink),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "\u26F6",
                fontSize = 15.sp,
                color = Accent,
                modifier = Modifier
                    .padding(start = 6.dp, top = 10.dp)
                    .clickable { state.toast("全屏编辑（接线后开）") },
            )
        }

        if (state.addMenuOpen) {
            Column(
                modifier = Modifier
                    .padding(start = 2.dp, top = 4.dp)
                    .width(150.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardBg)
                    .border(1.dp, Bg, RoundedCornerShape(14.dp))
                    .padding(6.dp),
            ) {
                DemoAttachMenu.forEach { item ->
                    Text(
                        item,
                        fontSize = 13.5.sp,
                        color = Ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .clickable {
                                val icon = item.substringBefore(' ')
                                state.addThumb(icon)
                                state.addMenuOpen = false
                                state.toast("已添加附件（演示）：" + item.substringAfter(' '))
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill("\uFF0B 附件") { state.addMenuOpen = !state.addMenuOpen }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFE3ECFF))
                    .clickable {
                        state.addMenuOpen = false
                        state.toolSheetOpen = false
                        state.taskSheetKey = null
                        state.modelSheetOpen = !state.modelSheetOpen
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "\u25C9 " + state.currentModel + " \u25BE",
                    fontSize = 12.5.sp,
                    color = Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Pill("\u22EE 工具") {
                state.addMenuOpen = false
                state.modelSheetOpen = false
                state.taskSheetKey = null
                state.toolSheetOpen = !state.toolSheetOpen
            }
            if (state.micOn) {
                Pill("\u25CF 松开发送", red = true) { state.micOn = false }
            } else {
                Pill("\uD83C\uDFA4") {
                    state.micOn = true
                    state.toast("录音中…（松开发送为演示态）")
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (state.busy) ErrRed else Accent)
                    .clickable { state.busy = !state.busy },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (state.busy) "\u25A0" else "\u27A4", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Pill(text: String, red: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 13.sp, color = if (red) ErrRed else SubInk)
    }
}
