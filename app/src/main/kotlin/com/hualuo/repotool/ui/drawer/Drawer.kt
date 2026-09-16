package com.hualuo.repotool.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.components.LRow
import com.hualuo.repotool.ui.model.Conv
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
 * 抽屉（v13 #drawer）：遮罩 + 284dp 左栏。
 * 会话搜索 / 新建 / 管理、多选、删除（带确认框）/ 底部四快捷行直达设置对应分组。
 * 会话接线后：列表来自真库（AppUiState.convs 由 SessionStore 喂），点行走 openConversation
 * 真切库——读不出就出声，绝不摆空壳（治旧 Agora「白屏/多进几次才出来」那一类）。
 */
@Composable
fun DrawerOverlay(state: AppUiState) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x47000000))
                .clickable { state.drawerOpen = false },
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(284.dp)
                .background(CardBg)
                .verticalScroll(rememberScrollState())
                .padding(start = 13.dp, end = 13.dp, top = 16.dp, bottom = 80.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Bg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (state.convQuery.isEmpty()) {
                    Text("搜索会话与消息…", fontSize = 13.sp, color = SubInk)
                }
                BasicTextField(
                    value = state.convQuery,
                    onValueChange = { state.convQuery = it },
                    textStyle = TextStyle(fontSize = 13.sp, color = Ink),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!state.selecting) {
                    Text("会话", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (state.selecting) "完成" else "管理",
                    fontSize = 13.sp,
                    color = Accent,
                    modifier = Modifier.clickable {
                        state.selecting = !state.selecting
                        state.selectedIds = emptySet()
                    },
                )
            }
            Spacer(Modifier.size(8.dp))
            if (!state.selecting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(width = 258.dp, height = 40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Accent)
                        .clickable { state.newConversation() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("新建会话", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.size(12.dp))
            }

            val q = state.convQuery.trim().lowercase()
            state.convs.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.meta.lowercase().contains(q) }
                .forEach { c ->
                    ConvRow(state, c)
                }

            Spacer(Modifier.size(10.dp))
            LRow("常用网站", chevron = true) { state.openSettings("sites") }
            LRow("导出备份", chevron = true) { state.openSettings("datactl") }
            LRow("模型与参数", chevron = true) { state.openSettings("model") }
            LRow("设置", chevron = true) { state.openSettings(null) }
        }

        if (state.selecting) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(284.dp)
                    .background(CardBg)
                    .border(1.dp, Hairline, RoundedCornerShape(topStart = 0.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "全选",
                    fontSize = 13.sp,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Bg)
                        .clickable {
                            state.selectedIds =
                                if (state.selectedIds.size == state.convs.size) emptySet()
                                else state.convs.map { it.id }.toSet()
                        }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("已选 " + state.selectedIds.size, fontSize = 12.5.sp, color = SubInk)
                Spacer(Modifier.weight(1f))
                Text(
                    "删除所选",
                    fontSize = 13.sp,
                    color = ErrRed,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Bg)
                        .clickable { state.askDeleteSelected() }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "取消",
                    fontSize = 13.sp,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Bg)
                        .clickable {
                            state.selecting = false
                            state.selectedIds = emptySet()
                            state.drawerOpen = false
                        }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ConvRow(state: AppUiState, c: Conv) {
    val sel = c.id in state.selectedIds
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (sel || (!state.selecting && state.currentModel.isNotEmpty() && c.id == state.convs.firstOrNull()?.id)) Color(0xFFE3ECFF) else Color.Transparent)
            .clickable {
                if (state.selecting) {
                    state.toggleSelect(c.id)
                } else {
                    // 真切库：同步读那份 JSONL 摆上屏（openConversation 内部收抽屉、坏文件出声）
                    state.openConversation(c.id)
                }
            }
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.selecting) {
            // 选中态就靠实心圆加描边表达，不再往 19dp 的圆里塞对勾字符
            Box(
                modifier = Modifier
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(if (sel) Accent else Color.Transparent)
                    .border(2.dp, if (sel) Accent else ChevGray, CircleShape),
                contentAlignment = Alignment.Center,
            ) {}
            Spacer(Modifier.width(9.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                c.title,
                fontSize = 13.5.sp,
                color = if (sel) Accent else Ink,
                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(c.meta, fontSize = 11.sp, color = SubInk)
        }
    }
}
