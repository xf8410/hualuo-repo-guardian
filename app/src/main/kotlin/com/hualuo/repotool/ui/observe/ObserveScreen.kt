package com.hualuo.repotool.ui.observe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.engine.observe.ObserveState
import com.hualuo.repotool.ui.components.BadgeChip
import com.hualuo.repotool.ui.components.CardTitle
import com.hualuo.repotool.ui.components.HCard
import com.hualuo.repotool.ui.components.LRow
import com.hualuo.repotool.ui.model.Tone
import com.hualuo.repotool.ui.state.AppUiState

/**
 * 观测页（v13 #p-obs）：SO 观测桥真连接（560 清单 361-400 域）。
 * 状态徽章（六态）+ 地址（可改，持久化）+ 探测（health 到 status）+ 探测原文卡。
 *
 * 红线：全只读——本页只发 GET；18767 端口冻结不碰；写类端点
 * （sniff toggle/clear、update、il2cpp/call）在工具族与本页都不存在。
 */
@Composable
fun ObserveScreen(state: AppUiState) {
    val o = state.observe
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))

        HCard {
            CardTitle("SO 观测桥")
            Row {
                BadgeChip(o.link.link.label, tone = linkTone(o.link.link))
                Spacer(Modifier.width(8.dp))
                if (o.probing) BadgeChip("探测中", tone = Tone.Neutral)
            }
            if (o.link.lastNote.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(o.link.lastNote, fontSize = 12.5.sp, color = com.hualuo.repotool.ui.theme.SubInk)
            }
            if (o.link.cooldownRemainingMs() > 0) {
                LRow("冷却中", "${o.link.cooldownRemainingMs() / 1000}s（连败退避）", dot = Tone.Warn)
            }
        }

        HCard {
            CardTitle("桥地址")
            Row(Modifier.fillMaxWidth()) {
                var draft by remember(o.baseUrl) { mutableStateOf(o.baseUrl) }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TextStyle(fontSize = 13.sp),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { o.baseUrl = draft }) { Text("保存") }
            }
            Text(
                "默认 http://127.0.0.1:18765（游戏与 app 同机，走本机回环，避开 VPN/代理）",
                fontSize = 11.5.sp,
                color = com.hualuo.repotool.ui.theme.SubInk,
            )
            Row {
                TextButton(
                    onClick = { o.probe() },
                    enabled = !o.probing,
                ) { Text(if (o.probing) "探测中…" else "探测") }
            }
            if (o.probeNote != null) {
                Text(o.probeNote!!, fontSize = 12.5.sp)
            }
        }

        if (o.lastHealth != null) {
            HCard {
                CardTitle("/health 原文")
                Text(o.lastHealth!!, fontSize = 11.5.sp, lineHeight = 16.sp)
            }
        }
        if (o.lastStatus != null) {
            HCard {
                CardTitle("/status 原文")
                Text(o.lastStatus!!, fontSize = 11.5.sp, lineHeight = 16.sp)
            }
        }

        HCard {
            CardTitle("对话里怎么用")
            LRow("uma_* 工具", "43 件只读工具，桥不在=清单里消失")
            LRow("抓育成数据", "summary/data/events/log 一句话就能读")
            LRow("拉面规划", "uma_ramen_planner_state 直连决策 AI")
            LRow("端点没列的", "uma_read_endpoint 显式点名任意只读路径")
        }

        HCard {
            CardTitle("红线")
            LRow("只读", "本页与工具族只发 GET，写类端点不存在")
            LRow("18767", "冻结，不碰")
            LRow("全量类扫描", "默认禁止，仅显式点名")
        }
    }
}

/** 六态换徽章色。 */
private fun linkTone(link: ObserveState.Link): Tone = when (link) {
    ObserveState.Link.READY -> Tone.Ok
    ObserveState.Link.CONNECTING -> Tone.Ok
    ObserveState.Link.DEGRADED -> Tone.Warn
    ObserveState.Link.DISCONNECTED -> Tone.Neutral
    ObserveState.Link.OVERLOADED -> Tone.Warn
    ObserveState.Link.INCOMPATIBLE -> Tone.Err
}
