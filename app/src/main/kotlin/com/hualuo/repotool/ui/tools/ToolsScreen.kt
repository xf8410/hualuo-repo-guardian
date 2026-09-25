package com.hualuo.repotool.ui.tools

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.components.CardTitle
import com.hualuo.repotool.ui.components.Dot
import com.hualuo.repotool.ui.components.HCard
import com.hualuo.repotool.ui.data.DemoToolStates
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.theme.WarnAmber

/**
 * 工具页（v13 #p-tool）。
 *
 * **网页搜索已接真电（0.7.0）**：免费档 DuckDuckGo，输入、按钮、结果列表都是真数据；
 * 失败出声不冒充（引擎件说「没有搜到结果」就是没有，屏上照实摆失败理由，不装成功）。
 * 点一条结果用系统浏览器打开（设备上没有能接的 App 就出声，不静默）。
 * 四态分列仍是演示板——逐工具真电等「模型可调用工具」那刀（tool_calls 协议）一起进，
 * 到时这张卡换成从真实工具注册表读，别提前画饼。
 */
@Composable
fun ToolsScreen(state: AppUiState) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))

        VideoUnderstandingCard(state)

        Spacer(Modifier.height(10.dp))

        ApkCheckCard(state)

        Spacer(Modifier.height(10.dp))

        HCard {
            CardTitle("网页搜索（免费档 DuckDuckGo）")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Bg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (state.searchQuery.isEmpty()) {
                        Text("要搜什么…", fontSize = 13.sp, color = SubInk)
                    }
                    BasicTextField(
                        value = state.searchQuery,
                        onValueChange = { state.searchQuery = it },
                        textStyle = TextStyle(fontSize = 13.sp, color = Ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (state.searchBusy) SubInk else Accent)
                        .clickable(enabled = !state.searchBusy) { state.runWebSearch() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        if (state.searchBusy) "搜着…" else "搜一下",
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            state.searchNote?.let { note ->
                Spacer(Modifier.height(6.dp))
                Text(note, fontSize = 12.sp, color = if (state.searchResults.isEmpty()) WarnAmber else SubInk)
            }
            state.searchResults.forEach { result ->
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url)))
                            }.onFailure {
                                state.toast("打不开这个链接（设备上没有能接的浏览器？）")
                            }
                        },
                ) {
                    Text(result.title, fontSize = 13.5.sp, color = Accent, fontWeight = FontWeight.Medium)
                    Text(result.url, fontSize = 10.5.sp, color = SubInk)
                    if (result.snippet.isNotEmpty()) {
                        Text(result.snippet, fontSize = 12.sp, color = Ink)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        HCard {
            CardTitle("四态分列：注册 / 接线 / 开关 / 可执行")
            DemoToolStates.forEach { t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.name, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Normal)
                    Spacer(Modifier.weight(1f))
                    t.states.forEachIndexed { i, tone ->
                        Dot(tone)
                        if (i != t.states.lastIndex) Spacer(Modifier.width(6.dp))
                    }
                }
            }
        }
    }
}
