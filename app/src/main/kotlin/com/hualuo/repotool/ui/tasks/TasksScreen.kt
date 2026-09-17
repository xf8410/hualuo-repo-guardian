package com.hualuo.repotool.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.components.CardTitle
import com.hualuo.repotool.ui.components.HCard
import com.hualuo.repotool.ui.components.LRow
import com.hualuo.repotool.ui.data.DemoTasks
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.CardBg
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.theme.WarnAmber

/**
 * 长任务页（v13 #p-task）：两枚统计卡 + 文件投递（0.7.0 真电）+ 定时任务列表；点行开详情弹层。
 *
 * 文件投递卡是本页第一件真电：选文件/选目录（系统选择器，桥在 RootScreen）、开始投递
 * （三道闸在状态层，收集与分卷投递在后台线程）；目标仓/分支/令牌在设置「文件投递」里配。
 * 收场话（含收集报告与落点）照实摆在卡里，失败出声不冒充。
 */
@Composable
fun TasksScreen(state: AppUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        Row {
            StatCard("运行中", "2", Modifier.weight(1f).padding(end = 5.5.dp))
            StatCard("今日完成", "7", Modifier.weight(1f).padding(start = 5.5.dp))
        }
        HCard {
            CardTitle("文件投递（zip 分卷进私有仓）")
            val repo = state.courierRepo()
            Text(
                if (repo == null) "目标仓未配：去设置「文件投递」填 owner/name" else "目标 $repo @${state.courierBranch()}",
                fontSize = 12.sp,
                color = if (repo == null) WarnAmber else SubInk,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CourierButton("选文件", enabled = !state.courierBusy) { state.requestCourierPick(false) }
                Spacer(Modifier.width(8.dp))
                CourierButton("选目录", enabled = !state.courierBusy) { state.requestCourierPick(true) }
                Spacer(Modifier.weight(1f))
                CourierButton(
                    "开始投递",
                    primary = true,
                    enabled = !state.courierBusy && state.courierPicks.isNotEmpty(),
                ) {
                    state.requestCourierDeliver()
                }
            }
            if (state.courierPicks.isNotEmpty()) {
                val fileCount = state.courierPicks.count { !it.isTree }
                val treeCount = state.courierPicks.size - fileCount
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已选 ${state.courierPicks.size} 项（文件 $fileCount、目录 $treeCount）",
                        fontSize = 12.sp,
                        color = Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "清空",
                        fontSize = 12.sp,
                        color = Accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(enabled = !state.courierBusy) { state.clearCourierPicks() },
                    )
                }
            }
            state.courierNote?.let { note ->
                Spacer(Modifier.height(6.dp))
                Text(
                    note,
                    fontSize = 12.sp,
                    color = if (note.startsWith("投递完成")) SubInk else WarnAmber,
                )
            }
        }
        HCard(modifier = Modifier.padding(top = 11.dp)) {
            CardTitle("定时任务")
            DemoTasks.forEach { t ->
                LRow(
                    label = t.name,
                    value = t.value,
                    dot = t.tone,
                    chevron = true,
                    onClick = {
                        state.modelSheetOpen = false
                        state.toolSheetOpen = false
                        state.taskSheetKey = t.name
                    },
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, big: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(bottom = 11.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Text(label, fontSize = 12.sp, color = SubInk)
        Text(big, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, fontFamily = FontFamily.Monospace)
    }
}

/** 文件投递卡上的按钮（工具页搜索钮同款形状）：主钮主色，禁用就灰掉，不玩哑弹。 */
@Composable
private fun CourierButton(
    text: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(when {
                !enabled -> SubInk
                primary -> Accent
                else -> Bg
            })
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            fontSize = 13.sp,
            color = if (primary || !enabled) Color.White else Ink,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
