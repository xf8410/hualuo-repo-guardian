package com.hualuo.repotool.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.components.CardTitle
import com.hualuo.repotool.ui.components.Dot
import com.hualuo.repotool.ui.components.HCard
import com.hualuo.repotool.ui.data.DemoToolStates
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.Ink

/** 工具页（v13 #p-tool）：四态分列——注册 / 接线 / 开关 / 可执行，一灯一态。 */
@Composable
fun ToolsScreen(state: AppUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
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
