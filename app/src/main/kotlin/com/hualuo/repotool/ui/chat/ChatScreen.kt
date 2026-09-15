package com.hualuo.repotool.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.components.BadgeChip
import com.hualuo.repotool.ui.data.DemoMessages
import com.hualuo.repotool.ui.model.ChatMsg
import com.hualuo.repotool.ui.model.IconKey
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.ErrRed
import com.hualuo.repotool.ui.theme.Hairline
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.MeBubble
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.theme.WarnAmber

/**
 * 回合流页（v13 #p-chat)：证据卡列表，滚动只发生在这一列里。
 *
 * 图形字符一律走资源（`stringResource(IconKey.X.resId)`）—— 家规，闸门 NoEmojiInSourceTest。
 * 注意：取值只能发生在组合期，所以点击回调里要用的东西先在外层备好（见 quotePrefix）。
 */
@Composable
fun ChatScreen(state: AppUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
    ) {
        items(DemoMessages) { msg ->
            MessageCard(state, msg)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MessageCard(state: AppUiState, msg: ChatMsg) {
    // 组合期备好：点击回调里不能再调 stringResource
    val quotePrefix = "引用："
    val expandGlyph = stringResource(IconKey.Expand.resId)

    if (msg.fromMe) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 11.dp)
                .padding(start = 36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MeBubble)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(msg.text, fontSize = 14.sp, color = Ink)
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 11.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (msg.isError) Color(0xFFFFF5F5) else Color.White)
            .then(
                if (msg.isError) {
                    Modifier.border(
                        width = 3.dp,
                        color = ErrRed,
                        shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
                    ).padding(start = 3.dp)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (msg.who.isNotEmpty() || msg.time.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                msg.who.forEach { BadgeChip(it.text, it.tone) }
                if (msg.time.isNotEmpty()) Text(msg.time, fontSize = 11.sp, color = SubInk)
            }
            Spacer(Modifier.height(5.dp))
        }
        Text(msg.text, fontSize = 14.sp, color = Ink, lineHeight = 22.sp)

        msg.thinkLabel?.let { label ->
            var open by remember { mutableStateOf(false) }
            Spacer(Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                    .clickable { open = !open }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                // 折叠区前缀的小三角：原型是拼在文案里的字符，这里从资源取，外观不变
                Text("$expandGlyph $label", fontSize = 12.5.sp, color = SubInk)
            }
            if (open && msg.thinkBody != null) {
                Spacer(Modifier.height(6.dp))
                DashedBody(msg.thinkBody)
            }
        }

        msg.toolLine?.let { line ->
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 0.dp))
                    .background(Color(0xFFF7F9FF))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                msg.toolIconKey?.let {
                    Text(stringResource(it.resId), fontSize = 12.5.sp, color = Ink)
                    Spacer(Modifier.width(6.dp))
                }
                Text(line, fontSize = 12.5.sp, color = Ink)
            }
        }

        if (msg.attachments.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                msg.attachments.forEach { a ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                if (a.unknown) Color(0xFFF5D9A8) else Hairline,
                                RoundedCornerShape(10.dp),
                            )
                            .background(Color(0xFFFAFBFC))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Text(
                            stringResource(a.iconKey.resId) + " " + a.label,
                            fontSize = 12.sp,
                            color = if (a.unknown) WarnAmber else SubInk,
                        )
                    }
                }
            }
        }

        msg.dropLabel?.let { label ->
            var open by remember { mutableStateOf(false) }
            Spacer(Modifier.height(7.dp))
            Text(
                label,
                fontSize = 12.sp,
                color = WarnAmber,
                modifier = Modifier.clickable { open = !open },
            )
            if (open && msg.dropBody != null) {
                Spacer(Modifier.height(6.dp))
                DashedBody(msg.dropBody)
            }
        }

        if (msg.ops.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                msg.ops.forEach { op ->
                    Text(
                        op,
                        fontSize = 12.sp,
                        color = SubInk,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                when (op) {
                                    "复制" -> state.toast("已复制原文")
                                    "重新生成" -> state.toast("已按原设置重新生成（演示）")
                                    // 原先往输入框里塞的是个回头箭头字符；那是要发出去的内容，
                                    // 改成文字前缀「引用：」，看得清也发得出去
                                    "引用" -> {
                                        state.input = quotePrefix + msg.text.take(36) + "…"
                                        state.toast("已引用到输入框")
                                    }
                                    "分享" -> state.toast("分享卡片已生成（演示）")
                                }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashedBody(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
            .background(Color(0xFFFAFBFC))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 12.5.sp, color = SubInk, lineHeight = 19.sp)
    }
}
