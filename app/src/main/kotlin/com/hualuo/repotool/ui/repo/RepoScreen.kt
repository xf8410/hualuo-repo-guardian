package com.hualuo.repotool.ui.repo

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
import com.hualuo.repotool.ui.data.DemoRepoRows
import com.hualuo.repotool.ui.state.AppUiState

/** 仓库CI页（v13 #p-repo）：main 分支的 run 与三道闸门。 */
@Composable
fun RepoScreen(state: AppUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        HCard {
            CardTitle("hualuo-repo-tool · main")
            DemoRepoRows.forEach { r ->
                LRow(r.label, r.value, dot = r.tone)
            }
        }
    }
}
