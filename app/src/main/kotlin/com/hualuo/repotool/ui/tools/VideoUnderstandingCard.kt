package com.hualuo.repotool.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.components.CardTitle
import com.hualuo.repotool.ui.components.HCard
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.theme.WarnAmber

/**
 * 视频理解卡（看视频功能第一刀的界面）：选录屏 -> 定抽帧计划 -> 抽帧 -> 分批视觉问答 -> 汇总。
 *
 * 职责只两件：把 SAF 选来的 uri/时长交给状态层（[AppUiState.planVideo]）；
 * 「开始理解」时按状态层给的时间点在后台抽帧（[AppUiState.onVideoFramesReady]）。
 * 编排、请求、汇总全在状态层与引擎件——界面只出力不拿主意。
 */
@Composable
fun VideoUnderstandingCard(
    state: AppUiState,
    pickVideo: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    onStart: (List<Long>) -> Unit,
) {
    HCard {
        CardTitle("视频理解（看画面读攻略）")
        Text(
            "选一段录屏，AI 均匀抽帧逐批看，最后给画面流水与攻略要点。文字（选项/数值/提示）会原样读出。",
            fontSize = 12.sp, color = SubInk,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Box2Button(
                label = if (state.videoUri == null) "选视频" else "换一段",
                enabled = !state.videoBusy,
            ) { pickVideo.launch(arrayOf("video/*")) }
            Spacer(Modifier.width(8.dp))
            if (state.videoUri != null) {
                Box2Button(
                    label = if (state.videoBusy) "看着…" else "开始理解",
                    enabled = !state.videoBusy && state.videoFrameTimes.isNotEmpty(),
                    accent = true,
                ) { onStart(state.videoFrameTimes) }
            }
        }
        if (state.videoUri != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "时长 ${(state.videoDurationMs + 999) / 1000} 秒，计划抽 ${state.videoFrameTimes.size} 帧" +
                    "（分 ${(state.videoFrameTimes.size + com.hualuo.engine.vision.VideoPlan.PER_BATCH - 1) / com.hualuo.engine.vision.VideoPlan.PER_BATCH} 批读）",
                fontSize = 12.sp, color = SubInk,
            )
        }
        state.videoProgress?.let { p ->
            Spacer(Modifier.height(6.dp))
            Text(p, fontSize = 12.sp, color = Accent)
        }
        state.videoNote?.let { note ->
            Spacer(Modifier.height(6.dp))
            Text(note, fontSize = 12.sp, color = WarnAmber)
        }
        if (state.videoBatchNotes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("各批画面记录", fontSize = 12.sp, color = SubInk, fontWeight = FontWeight.SemiBold)
            state.videoBatchNotes.forEachIndexed { i, note ->
                Spacer(Modifier.height(4.dp))
                Text("第 ${i + 1} 批：$note", fontSize = 12.sp, color = Ink)
            }
        }
        state.videoSummary?.let { summary ->
            Spacer(Modifier.height(8.dp))
            Text("总结", fontSize = 12.sp, color = SubInk, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(summary, fontSize = 13.sp, color = Ink)
        }
        if (state.videoUri != null && !state.videoBusy) {
            Spacer(Modifier.height(8.dp))
            Text(
                "清掉重来",
                fontSize = 12.sp,
                color = SubInk,
                modifier = Modifier
                    .clickable { state.resetVideo() }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/** 卡内通用小按钮（背景色区分主次，无新组件依赖）。 */
@Composable
private fun Box2Button(label: String, enabled: Boolean = true, accent: Boolean = false, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .background(if (accent) Accent else Bg, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, fontSize = 13.sp, color = if (accent) Color.White else Ink, fontWeight = FontWeight.SemiBold)
    }
}
