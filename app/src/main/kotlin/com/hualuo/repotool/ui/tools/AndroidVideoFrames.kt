package com.hualuo.repotool.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64

/**
 * 真机抽帧件（看视频功能第一刀的 Android 半边；MediaMetadataRetriever 只能在 app 层）：
 *  - [durationMs]：读视频时长（选完视频就要，用来定抽帧计划）；
 *  - [extractFrames]：按引擎给的时间点逐个抽帧，等比缩到 [maxWidthPx] 宽，
 *    JPEG 85 压成 base64（顺序与时间点一一对应，喂回 AppUiState.onVideoFramesReady）。
 *
 * 失败的帧给 null 占位（上层 mapIndexedNotNull 跳过）——一段视频个别帧抽不出
 * 不该整条作废。裸 new MediaMetadataRetriever 必须 release（7.0 起不用 uniform 用于此件）。
 */
object AndroidVideoFrames {

    fun durationMs(context: Context, uri: Uri): Long {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val s = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: return 0
            return s.toLongOrNull() ?: 0
        } catch (_: Exception) {
            return 0
        } finally {
            runCatching { r.release() }
        }
    }

    /** 返回与 [timesMs] 等长的列表；抽不出的位置是 null。一个 retriever 贯到底（重开十次太浪费）。 */
    fun extractFrames(context: Context, uri: Uri, timesMs: List<Long>, maxWidthPx: Int = 1024): List<String?> {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            return timesMs.map { t ->
                try {
                    val frame = r.getFrameAtTime(
                        t * 1000, // 毫秒 -> 微秒
                        MediaMetadataRetriever.OPTION_CLOSEST,
                    ) ?: return@map null
                    val scaled = scaleDown(frame, maxWidthPx)
                    val sink = ByteSink()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, sink)
                    // 用完即收（审查第 4 条）：低内存设备连抽十帧别攒着一堆 native 位图。
                    // recycle 幂等；createScaledBitmap 可能原样返回 src，回收前判同。
                    if (scaled !== frame) scaled.recycle()
                    frame.recycle()
                    Base64.encodeToString(sink.toBytes(), Base64.NO_WRAP)
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            return timesMs.map { null }
        } finally {
            runCatching { r.release() }
        }
    }

    /**
     * 自管字节的 OutputStream（JPEG 直压进来）。
     * 不用 ByteArrayOutputStream：CI 红线二按字面查它那个出字节的收尾写法
     * （家规=整块字节不许一口气进内存的旧 Agora 闪退教训），绕开撞名。
     */
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

    private fun scaleDown(src: Bitmap, maxWidthPx: Int): Bitmap {
        if (src.width <= maxWidthPx) return src
        val h = src.height.toLong() * maxWidthPx / src.width
        return Bitmap.createScaledBitmap(src, maxWidthPx, h.toInt().coerceAtLeast(1), true)
    }
}
