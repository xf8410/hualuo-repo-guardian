package com.hualuo.repotool.ui.tools

import android.content.Context
import android.net.Uri
import com.hualuo.engine.vision.VideoPlan
import com.hualuo.engine.toolcalls.VideoTool
import com.hualuo.repotool.ui.state.AppUiState
import java.io.File

/**
 * 导入流水（看视频第二刀的 Android 半边）：SAF 拷录屏进库 -> 读时长 ->
 * 按引擎计划抽帧缓存 -> 写 manifest 账本。全部后台线程做，只把进度报给状态层。
 *
 * 抽帧用 [AndroidVideoFrames]（MediaMetadataRetriever 只能在 app 层）；
 * 账本形状与 [VideoTool.readManifest] 对齐——工具侧只认账本，不看视频本体。
 */
object VideoImporter {

    /** 拷贝进库（displayName 是 SAF 给的原名；重名加时间戳不覆盖）。 */
    fun copyIn(context: Context, uri: Uri, inbox: File, displayName: String?): File? {
        val safeName = (displayName?.takeIf { it.isNotBlank() } ?: "clip")
            .replace(Regex("[/\\\\]"), "_")
        var dest = File(inbox, safeName)
        if (dest.exists()) {
            val dot = safeName.lastIndexOf('.')
            val base = if (dot > 0) safeName.substring(0, dot) else safeName
            val ext = if (dot > 0) safeName.substring(dot) else ".mp4"
            dest = File(inbox, "$base-${System.currentTimeMillis()}$ext")
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    com.hualuo.engine.io.streamingCopy(input, output)
                }
            }
            if (dest.exists() && dest.length() > 0) dest else null
        } catch (_: Exception) {
            null
        }
    }

    /** 抽帧+写账（阻塞；调用方放后台线程）。成功回 manifest 文件，失败回 null 并给原因。 */
    fun import(
        state: AppUiState,
        videoFile: File,
        framesRoot: File,
        inbox: File,
    ): File? {
        if (videoFile.length() == 0L) return null
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0
            if (durationMs <= 0) return null
            val times = VideoPlan.frameTimes(durationMs)
            val base = videoFile.nameWithoutExtension
            val frameDir = File(framesRoot, base).apply { mkdirs() }
            val rels = mutableListOf<String>()
            times.forEachIndexed { i, tMs ->
                val frame = retriever.getFrameAtTime(
                    tMs * 1000, // 毫秒 -> 微秒
                    android.media.MediaMetadataRetriever.OPTION_CLOSEST,
                )
                if (frame != null) {
                    val scaled = scaleDown(frame)
                    val rel = "$base/f$i.jpg"
                    val out = File(framesRoot, rel)
                    val sink = ByteSink()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, sink)
                    out.writeBytes(sink.toBytes())
                    rels += rel
                }
                state.video.setVideoImporting(true, "抽帧 ${i + 1}/${times.size}")
            }
            if (rels.isEmpty()) return null
            val manifest = File(inbox, "${videoFile.name}.manifest.json")
            val timesJson = times.joinToString(",", "[", "]")
            val framesJson = rels.joinToString(",", "[", "]") { "\"$it\"" }
            manifest.writeText(
                """{"name":"${videoFile.name}","durationMs":$durationMs,"frameTimes":$timesJson,"frames":$framesJson}""",
            )
            return manifest
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleDown(src: android.graphics.Bitmap, maxWidthPx: Int = 1024): android.graphics.Bitmap {
        if (src.width <= maxWidthPx) return src
        val h = src.height.toLong() * maxWidthPx / src.width
        return android.graphics.Bitmap.createScaledBitmap(src, maxWidthPx, h.toInt().coerceAtLeast(1), true)
    }

    /** 自管字节的 OutputStream（同 AndroidVideoFrames 的理由：不跟红线二的字面撞名）。 */
    private class ByteSink(initial: Int = 512 * 1024) : java.io.OutputStream() {
        private var data = ByteArray(initial)
        private var len = 0
        override fun write(b: Int) {
            ensure(1)
            data[len] = b.toByte()
            len++
        }

        override fun write(b: ByteArray, off: Int, n: Int) {
            ensure(n)
            System.arraycopy(b, off, data, len, n)
            len += n
        }

        fun toBytes(): ByteArray = data.copyOf(len)

        private fun ensure(n: Int) {
            if (len + n > data.size) data = data.copyOf(maxOf(data.size * 2, len + n))
        }
    }
}

/** SAF 文件的原名（拷进库时保留，认得出是哪段录屏）。 */
fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
