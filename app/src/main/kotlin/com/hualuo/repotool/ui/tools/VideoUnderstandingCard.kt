package com.hualuo.repotool.ui.tools

import android.net.Uri
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
 * 视频理解卡（看视频功能第一刀的界面）：选录屏 -> 定抽帧计划 -> 开始理解 -> 汇总。
 *
 * 主线程零视频工作（审查 P0）：点「开始理解」只是把抽帧函数递给状态层，
 * 抽帧/分批/汇总全在状态层自己的后台线程排。
 *
 * 交互事实：
 *  - 时长没读出来 = 「开始理解」禁用（单帧瞎抽不是降级，是浪费 token——审查第 1 条次要项）；
 *  - 跑着的时候只有「停止」能点（置停止位 + 打断卡住的请求，审查第 5 条）；
 *  - 汇总/批描述出来了可复制；清掉重来随时可点（忙时不许）。
 */
@Composable
fun VideoUnderstandingCard(state: AppUiState) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val duration = AndroidVideoFrames.durationMs(context, uri)
            state.planVideo(uri.toString(), duration)
            if (duration <= 0) state.resetVideoNoteTo("读不到时长：这个文件可能不是视频（或已失效）")
        }
    }

    HCard {
        CardTitle("视频理解（选录屏，读画面与文字，出攻略总结）")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box2Button(if (state.videoUri == null) "选视频" else "换视频", enabled = !state.videoBusy) {
                picker.launch(arrayOf("video/*"))
            }
            Spacer(Modifier.width(8.dp))
            val canStart = state.videoUri != null && state.videoDurationMs > 0 && state.videoFrameTimes.isNotEmpty()
            Box2Button(
                if (state.videoBusy) "跑着…" else "开始理解",
                enabled = !state.videoBusy && canStart,
                accent = canStart && !state.videoBusy,
            ) {
                val uri = state.videoUri ?: return@Box2Button
                // 只递抽帧函数；抽帧/分批/汇总全在状态层后台线程（主线程不做视频工作）
                state.startVideoUnderstanding { times ->
                    AndroidVideoFrames.extractFrames(context, Uri.parse(uri), times)
                }
            }
            if (state.videoBusy) {
                Spacer(Modifier.width(8.dp))
                Box2Button("停止") { state.stopVideo() }
            }
        }
        state.videoProgress?.let { p ->
            Spacer(Modifier.height(6.dp))
            Text(p, fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold)
        }
        if (state.videoUri != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "时长 ${(state.videoDurationMs + 999) / 1000}s · 计划 ${state.videoFrameTimes.size} 帧 · " +
                    "用当前模型「${state.currentModel}」（不支持视觉会在第一批就报错，换模型重跑即可）",
                fontSize = 11.5.sp,
                color = SubInk,
            )
        }
        state.videoNote?.let { note ->
            Spacer(Modifier.height(6.dp))
            Text(note, fontSize = 12.sp, color = WarnAmber)
        }
        if (state.videoBatchNotes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("各批画面记录", fontSize = 12.sp, color = SubInk, fontWeight = FontWeight.SemiBold)
            state.videoBatchNotes.forEachIndexed { i, note ->
                Text("— 第 ${i + 1} 批 —", fontSize = 10.5.sp, color = SubInk)
                Text(note, fontSize = 12.sp, color = Ink)
            }
        }
        state.videoSummary?.let { summary ->
            Spacer(Modifier.height(8.dp))
            Text("总结", fontSize = 12.sp, color = SubInk, fontWeight = FontWeight.SemiBold)
            Text(summary, fontSize = 13.sp, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "复制总结",
                fontSize = 12.sp,
                color = Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("视频理解总结", summary))
                },
            )
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
