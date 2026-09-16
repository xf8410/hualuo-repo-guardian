package com.hualuo.repotool.ui.repo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.components.CardTitle
import com.hualuo.repotool.ui.components.HCard
import com.hualuo.repotool.ui.components.LRow
import com.hualuo.repotool.ui.model.Tone
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.theme.WarnAmber

/**
 * 仓库CI页（v13 #p-repo）：**真数据**——GitHub Actions 最近几条 run + 检查更新。
 *
 * 数据通道：进页拉一次（已有数据不重复拉），「刷新」行手动重拉；
 * 失败（403 提示去填令牌 / 404 提示核仓库名 / 连不上）与坏条目都摆在明面上，
 * 不拿演示卡冒充 CI 状态——那正是旧版「演示卡撒谎」的同款病。
 */
@Composable
fun RepoScreen(state: AppUiState) {
    // 进页拉一次；LaunchedEffect(Unit) 每次进这个 tab 只跑一遍，不刷屏
    LaunchedEffect(Unit) { state.refreshRepoCiIfStale() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        HCard {
            CardTitle(state.ciRepoLabel)
            when {
                state.ciBusy && state.ciRuns.isEmpty() -> Text(
                    "正在拉 CI 记录…",
                    fontSize = 13.sp,
                    color = SubInk,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                state.ciRuns.isEmpty() && state.ciError != null -> Text(
                    state.ciError ?: "",
                    fontSize = 13.sp,
                    color = WarnAmber,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                state.ciRuns.isEmpty() -> Text(
                    "还没拉到记录：点下面「刷新」从 GitHub 重拉",
                    fontSize = 13.sp,
                    color = SubInk,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                else -> state.ciRuns.forEach { run ->
                    // 绿灯=成功；红=失败/超时/取消；黄=还在跑（conclusion 还没给）
                    val dot = when (run.conclusion) {
                        "success" -> Tone.Ok
                        "failure", "timed_out", "cancelled" -> Tone.Err
                        else -> Tone.Warn
                    }
                    LRow(
                        "run ${run.id}",
                        "${run.conclusion ?: run.status} · ${run.headSha.take(7)}",
                        dot = dot,
                    )
                }
            }
            if (state.ciBadEntries > 0) {
                Text(
                    "有 ${state.ciBadEntries} 条记录读不懂，已跳过（其余照常显示）",
                    fontSize = 11.5.sp,
                    color = WarnAmber,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            LRow("刷新", chevron = true) { state.refreshRepoCi() }
        }
        Spacer(Modifier.height(10.dp))
        HCard {
            CardTitle("版本")
            Text(
                state.updateNote ?: "拿当前版本对 GitHub 最新发布版；还没发布过会直说",
                fontSize = 12.5.sp,
                color = SubInk,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            LRow("检查更新", chevron = true) { state.checkUpdate() }
        }
    }
}
