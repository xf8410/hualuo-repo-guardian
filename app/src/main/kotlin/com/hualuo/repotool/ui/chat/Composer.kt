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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.data.DemoAttachMenu
import com.hualuo.repotool.ui.data.DemoLoopBar
import com.hualuo.repotool.ui.data.DemoLoopIcon
import com.hualuo.repotool.ui.data.DemoQueueBar
import com.hualuo.repotool.ui.data.DemoQueueIcon
import com.hualuo.repotool.ui.model.IconKey
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
 * 原型拍板的三条都在此：语音在聊天框（不在设置）、模型切换在输入区原位弹层、生成中发送钮变红方块。
 *
 * 发送钮接的是真电（09-15 拍板「不要是摆设」）：空闲时把草稿交给 ChatRuntime 真发，
 * 生成中变红方块、按下掐这条自己的连接；忙灯从 state.busy 读，而 busy 就是 runtime 的事实。
 *
 * 图形字符一律走资源（`stringResource(IconKey.X.resId)`）—— 家规，闸门 NoEmojiInSourceTest。
 * 缩略列表存的是 IconKey 键名字符串，解不出键就照原样显示并染成警告色，不许悄悄换成别的图标。
 */
@Composable
fun Composer(state: AppUiState) {
    val loopGlyph = stringResource(DemoLoopIcon.resId)
    val queueGlyph = stringResource(DemoQueueIcon.resId)
    val stopGlyph = stringResource(IconKey.ActionStop.resId)
    val crossGlyph = stringResource(IconKey.Cross.resId)
    val plusGlyph = stringResource(IconKey.Plus.resId)
    val dotsGlyph = stringResource(IconKey.DotsV.resId)
    val dotGlyph = stringResource(IconKey.Dot.resId)
    val micGlyph = stringResource(IconKey.Mic.resId)
    val sendGlyph = stringResource(IconKey.SendArrow.resId)
    val observeGlyph = stringResource(IconKey.ActionObserve.resId)
    val caretDownGlyph = stringResource(IconKey.CaretDown.resId)
    val expandScreenGlyph = stringResource(IconKey.ExpandScreen.resId)

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
                Text("$loopGlyph $DemoLoopBar", fontSize = 12.sp, color = LoopInk)
                Spacer(Modifier.weight(1f))
                Text(
                    "$stopGlyph 停",
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
                Text("$queueGlyph $DemoQueueBar", fontSize = 12.sp, color = Accent)
                Spacer(Modifier.weight(1f))
                Text(
                    "移除 $crossGlyph",
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
                state.thumbs.forEachIndexed { i, keyName ->
                    val iconKey = IconKey.fromKey(keyName)
                    Box(modifier = Modifier.size(44.dp)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ThumbBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (iconKey == null) {
                                // 键名解不出来：照原样显示并染警告色，不猜图标
                                Text(keyName, fontSize = 10.sp, color = WarnAmber)
                            } else {
                                Text(
                                    stringResource(iconKey.resId),
                                    fontSize = 19.sp,
                                    color = if (iconKey == IconKey.Question) WarnAmber else Ink,
                                )
                            }
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
                            Text(crossGlyph, color = Color.White, fontSize = 10.sp)
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
                expandScreenGlyph,
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
                DemoAttachMenu.forEach { (iconKey, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .clickable {
                                state.addThumb(iconKey.name)
                                state.addMenuOpen = false
                                state.toast("已添加附件（演示）：" + label)
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(iconKey.resId), fontSize = 13.5.sp, color = Ink)
                        Spacer(Modifier.width(6.dp))
                        Text(label, fontSize = 13.5.sp, color = Ink)
                    }
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
            Pill("$plusGlyph 附件") { state.addMenuOpen = !state.addMenuOpen }
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
                    "$observeGlyph " + state.currentModel + " $caretDownGlyph",
                    fontSize = 12.5.sp,
                    color = Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Pill("$dotsGlyph 工具") {
                state.addMenuOpen = false
                state.modelSheetOpen = false
                state.taskSheetKey = null
                state.toolSheetOpen = !state.toolSheetOpen
            }
            if (state.micOn) {
                Pill("$dotGlyph 松开发送", red = true) { state.micOn = false }
            } else {
                Pill(micGlyph) {
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
                    .clickable {
                        if (state.busy) {
                            // 生成中的红方块=停止：掐这一条自己的连接，已收的半截留在卡上
                            state.chat.stop()
                        } else if (state.input.isBlank()) {
                            state.toast("没内容可发")
                        } else {
                            state.chat.send(state.input, state.currentModel)
                            // 发出去草稿就该清（清这个动作本身也会记进设置文件，防重开又冒出来）
                            state.input = ""
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (state.busy) stopGlyph else sendGlyph, color = Color.White, fontSize = 16.sp)
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
