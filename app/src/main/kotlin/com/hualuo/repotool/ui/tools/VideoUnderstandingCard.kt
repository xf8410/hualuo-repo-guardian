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
import com.hualuo.repotool.ui.state.VideoUnderstandingState
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.theme.WarnAmber

/**
 * 视频理解卡（看视频功能的界面）：选录屏 -> 定抽帧计划 -> 开始（后台整链：抽帧+分批+汇总）。
 * 主线程零视频工作；所有编排在 [VideoUnderstandingState]（纯 JVM 可测）。
 */
@Composable
fun VideoUnderstandingCard(v: VideoUnderstandingState) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val duration = AndroidVideoFrames.durationMs(context, uri)
            v.planVideo(uri.toString(), duration)
            if (duration <= 0) v.resetVideoNoteTo("读不到时长：这个文件可能不是视频（或已失效）")
        }
    }

    HCard {
        CardTitle("视频理解（看画面读攻略）")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box2Button("选视频", enabled = !v.videoBusy) { picker.launch(arrayOf("video/*")) }
            Spacer(Modifier.width(8.dp))
            // 时长没读出来（=0）不许开跑：单帧瞎抽不是降级是浪费（审查第 1 条的另一半）
            Box2Button(
                if (v.videoBusy) (v.videoProgress ?: "跑着…") else "开始理解",
                enabled = !v.videoBusy && v.videoDurationMs > 0 && v.videoUri != null,
                accent = !v.videoBusy,
            ) {
                val uri = v.videoUri ?: return@Box2Button
                v.startVideoUnderstanding { times -> AndroidVideoFrames.extractFrames(context, Uri.parse(uri), times) }
            }
            if (v.videoBusy) {
                Spacer(Modifier.width(8.dp))
                Box2Button("停止", enabled = true) { v.stopVideo() }
            }
        }
        if (v.videoUri != null && v.videoDurationMs > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "已选 ${v.videoFrameTimes.size} 帧 · 时长 ${v.videoDurationMs / 1000}s" +
                    if (v.videoBusy) "" else "（当前视觉模型：聊天选定的那个）",
                fontSize = 12.sp, color = SubInk,
            )
        }
        v.videoNote?.let { note ->
            Spacer(Modifier.height(6.dp))
            Text(note, fontSize = 12.sp, color = WarnAmber)
        }
        if (v.videoBatchNotes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("各批画面描述：", fontSize = 12.sp, color = SubInk)
            v.videoBatchNotes.forEachIndexed { i, note ->
                Text("第 ${i + 1} 批：$note", fontSize = 11.sp, color = SubInk)
            }
        }
        v.videoSummary?.let { summary ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("总结", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                CopyButton(summary)
            }
            Spacer(Modifier.height(4.dp))
            Text(summary, fontSize = 13.sp, color = Ink)
        }
        if (v.videoUri != null && !v.videoBusy) {
            Spacer(Modifier.height(8.dp))
            Text(
                "清掉重来",
                fontSize = 12.sp,
                color = SubInk,
                modifier = Modifier
                    .clickable { v.resetVideo() }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/** 复制按钮（读写系统剪贴板，读侧不用）。 */
@Composable
private fun CopyButton(text: String) {
    val context = LocalContext.current
    Text(
        "复制",
        fontSize = 12.sp,
        color = Accent,
        modifier = Modifier
            .clickable {
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("video-summary", text))
            }
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
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
