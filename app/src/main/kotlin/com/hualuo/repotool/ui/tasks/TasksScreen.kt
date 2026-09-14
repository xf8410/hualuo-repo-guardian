package com.hualuo.repotool.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.components.CardTitle
import com.hualuo.repotool.ui.components.HCard
import com.hualuo.repotool.ui.components.LRow
import com.hualuo.repotool.ui.data.DemoTasks
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.CardBg
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk

/** 长任务页（v13 #p-task）：两枚统计卡 + 定时任务列表；点行开详情弹层。 */
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
