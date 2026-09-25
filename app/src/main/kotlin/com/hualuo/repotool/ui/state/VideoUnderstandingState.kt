package com.hualuo.repotool.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 视频库状态舱（看视频第二刀，红线三拆件——AppUiState 只持有一行）：
 * 导入编排的收场与库列表缓存住在这里；拷贝/抽帧/写账的体力活由界面层
 * （VideoImporter，MediaMetadataRetriever 是 Android 类）在后台线程做，
 * 做完把进度与收场报进来。
 *
 * **理解发生在对话里**：对话模型调 list_videos / watch_video 工具拿文字，
 * 眼睛模型（设置-看视频的眼睛）负责读帧，主对话模型不需要自带视觉。
 * 第一刀的手动理解流已被该结构取代（审查 P0/P1 由导入流+眼睛模型配置天然满足）。
 */
class VideoUnderstandingState(
    /** 视频库目录（录屏本体+manifest 账本）；null = 库功能不接。 */
    private val watchInboxDir: java.io.File?,
) {

    var videoImporting by mutableStateOf(false)
        private set
    var videoImportNote by mutableStateOf<String?>(null)
        private set
    /** 库列表缓存（一行概览）；导入完成后 refreshVideoLibrary() 刷。 */
    var videoLibraryCache by mutableStateOf<List<String>>(emptyList())
        private set

    /** 刷库列表（纯 JVM 读 manifest 账本）。 */
    fun refreshVideoLibrary() {
        val dir = watchInboxDir ?: return
        videoLibraryCache = com.hualuo.engine.toolcalls.VideoTool.readManifests(dir).map { m ->
            "${m.name}（${m.durationMs / 1000}s，${m.frames.size} 帧）"
        }
    }

    /** 导入编排的口子：界面层后台线程做拷贝/抽帧/写账，只把进度与收场报进来。 */
    fun setVideoImporting(busy: Boolean, note: String? = null) {
        videoImporting = busy
        if (note != null) videoImportNote = note
        if (!busy) refreshVideoLibrary()
    }
}
