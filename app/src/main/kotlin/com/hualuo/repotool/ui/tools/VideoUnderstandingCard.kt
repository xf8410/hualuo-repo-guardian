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

/**
 * 视频库卡（看视频第二刀的界面）：导入录屏 -> 后台抽帧缓存 -> 对话里的 AI
 * 用 list_videos / watch_video 工具看。**理解发生在对话里**——主对话模型
 * 是什么（纯文本也行）都能看，眼睛模型在设置里单独配。
 */
@Composable
fun VideoUnderstandingCard(state: AppUiState) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !state.videoImporting && state.watchInboxDir != null && state.watchFramesDir != null) {
            val inbox = state.watchInboxDir
            val frames = state.watchFramesDir
            state.setVideoImporting(true, "拷贝进库…")
            Thread({
                val copied = VideoImporter.copyIn(context, uri, inbox, queryDisplayName(context, uri))
                if (copied == null) {
                    state.setVideoImporting(false, "拷贝失败：文件读不动")
                    return@Thread
                }
                val manifest = VideoImporter.import(state, copied, frames, inbox)
                if (manifest == null) {
                    copied.delete()
                    state.setVideoImporting(false, "导入失败：抽不出帧（文件损坏或不是视频）")
                } else {
                    state.setVideoImporting(false, "已入库：${copied.name}——现在可以在对话里让 AI 看它了")
                }
            }, "hualuo-video-import").start()
        }
    }

    HCard {
        CardTitle("视频库（对话 AI 可看）")
        Text(
            "导入录屏后，对话里直接说「看一下 XX 视频」——AI 调 watch_video 自己读，主对话模型不用带视觉。",
            fontSize = 12.sp, color = SubInk,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Box2Button(
                if (state.videoImporting) "导入中…" else "导入录屏",
                enabled = !state.videoImporting,
                accent = !state.videoImporting,
            ) {
                picker.launch(arrayOf("video/*"))
            }
        }
        state.videoImportNote?.let { note ->
            Spacer(Modifier.height(6.dp))
            Text(note, fontSize = 12.sp, color = if (state.videoImporting) WarnAmber else SubInk)
        }
        if (state.videoLibraryCache.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            state.videoLibraryCache.forEach { line ->
                Text("· $line", fontSize = 12.sp, color = Ink)
            }
        } else if (!state.videoImporting) {
            Spacer(Modifier.height(6.dp))
            Text("库还是空的", fontSize = 12.sp, color = SubInk)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "眼睛模型在 设置-看视频的眼睛 里配；没配的话对话 AI 看不了视频。",
            fontSize = 10.5.sp, color = SubInk,
        )
    }
}

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
