package com.hualuo.repotool.ui.observe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hualuo.repotool.ui.components.CardTitle
import com.hualuo.repotool.ui.components.HCard
import com.hualuo.repotool.ui.components.LRow
import com.hualuo.repotool.ui.data.DemoObsRows
import com.hualuo.repotool.ui.state.AppUiState

/** 观测页（v13 #p-obs）：赛马娘观测桥端口状态。红线：全只读，18767 冻结。 */
@Composable
fun ObserveScreen(state: AppUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        HCard {
            CardTitle("赛马娘观测桥（只读门禁）")
            DemoObsRows.forEach { r ->
                LRow(r.label, r.value, dot = r.tone)
            }
        }
    }
}
