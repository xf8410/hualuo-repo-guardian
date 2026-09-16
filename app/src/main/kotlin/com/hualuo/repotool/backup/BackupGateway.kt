package com.hualuo.repotool.backup

import android.content.Context
import android.net.Uri
import com.hualuo.engine.backup.BACKUP_FORMAT
import com.hualuo.engine.backup.BACKUP_VERSION
import com.hualuo.engine.backup.BackupManifest
import com.hualuo.engine.backup.BackupSessionSource
import com.hualuo.engine.backup.MAX_SETTINGS_CHARS
import com.hualuo.engine.backup.readBackup
import com.hualuo.engine.backup.writeBackup
import com.hualuo.engine.io.streamingCopy
import com.hualuo.repotool.ui.state.SESSIONS_DIR_NAME
import com.hualuo.repotool.ui.state.SETTINGS_RELATIVE_PATH
import java.io.File
import java.io.InputStreamReader

/**
 * 备份的安卓侧网关：系统文件选择器（SAF）给 uri，这里负责流进流出。
 * 引擎（BackupArchive）管包格式，这里管「跟安卓要流」——两件事不混在一个文件里。
 *
 * 设置的**导入**刻意不在这里落盘：界面上活着的 SettingsStore 是另一份实例，
 * 这里绕过它直接写文件，活通道一次 flush 就会把新值盖掉（两份事实的老病）。
 * 所以设置原文由 [readImport] 带回，交给 AppUiState 的活通道去进。
 *
 * 两个函数都用**块体**：体里有 `return` 提前退场，表达式体（= try {...}）禁止 return——
 * CI 编译段连抓两次（run 35097435110 抓 engine，紧接着这轮抓这里）。谁要改成表达式体，
 * 先把里面的 return 全拔掉，别赌编译器放行。
 */
object BackupGateway {

    /** 导入回执：formatOk=false 时 settings/sessions 必为零——不是本家的包一个字节不收。 */
    data class ImportedBackup(
        val formatOk: Boolean,
        val settingsText: String?,
        val sessionsImported: Int,
        val sessionsSkipped: Int,
        val warnings: List<String>,
    )

    /** 导出：设置文件原文 + 会话仓全部 jsonl，zip 流式写进系统给的输出流。返回 null = 成。 */
    fun exportTo(context: Context, uri: Uri, appVersion: String): String? {
        return try {
            val settingsFile = File(context.filesDir, SETTINGS_RELATIVE_PATH)
            val settingsText = if (settingsFile.isFile) readTextBounded(settingsFile, MAX_SETTINGS_CHARS) else ""
            val sources = File(context.filesDir, SESSIONS_DIR_NAME)
                .listFiles()
                ?.filter { it.isFile && it.name.endsWith(".jsonl") }
                ?.sortedBy { it.name }
                ?.map { file -> BackupSessionSource(file.name.removeSuffix(".jsonl")) { file.inputStream() } }
                ?: emptyList()
            val out = context.contentResolver.openOutputStream(uri)
                ?: return "备份写不进去（系统没给输出流）：换个位置再试一次"
            out.use { writeBackup(it, settingsText, sources, appVersion) }
            null
        } catch (e: Exception) {
            "导出失败：${e.message ?: "写出错了"}"
        }
    }

    /**
     * 读一个包：会话现场落盘（重名跳过、原文件保留）；设置只带回原文。
     * manifest 条目在包里排最前（写方钉死的顺序），所以会话回调时身份一定已判完。
     */
    fun readImport(context: Context, uri: Uri): ImportedBackup {
        return try {
            val sessionsDir = File(context.filesDir, SESSIONS_DIR_NAME).apply { mkdirs() }
            var manifest: BackupManifest? = null
            var settingsText: String? = null
            var imported = 0
            var skipped = 0
            val stream = context.contentResolver.openInputStream(uri)
                ?: return ImportedBackup(false, null, 0, 0, listOf("读不到这个文件（系统没给输入流）"))
            val result = stream.use { input ->
                readBackup(input) { id, sessionStream ->
                    val known = manifest
                    if (known == null || known.format != BACKUP_FORMAT) {
                        // 身份还没判过或对不上：这条会话不收（引擎会把剩余字节排干）
                        skipped += 1
                        return@readBackup
                    }
                    val target = File(sessionsDir, "$id.jsonl")
                    if (target.exists()) {
                        skipped += 1
                    } else {
                        target.outputStream().use { streamingCopy(sessionStream, it) }
                        imported += 1
                    }
                }
            }
            manifest = result.manifest
            settingsText = result.settingsText

            val warnings = ArrayList<String>()
            val known = manifest
            val formatOk = known != null && known.format == BACKUP_FORMAT && known.version == BACKUP_VERSION
            if (known != null && known.format == BACKUP_FORMAT && known.version != BACKUP_VERSION) {
                warnings += "备份版本是 ${known.version}，本机只认 1：没有导入任何东西"
            }
            if (result.skippedEntries.isNotEmpty()) {
                warnings += "包里有 ${result.skippedEntries.size} 个条目认不出，没动"
            }
            ImportedBackup(
                formatOk = formatOk,
                settingsText = if (formatOk) settingsText else null,
                sessionsImported = if (formatOk) imported else 0,
                sessionsSkipped = if (formatOk) skipped else 0,
                warnings = warnings,
            )
        } catch (e: Exception) {
            ImportedBackup(false, null, 0, 0, listOf("导入失败：${e.message ?: "读不了这个文件"}"))
        }
    }

    private fun readTextBounded(file: File, maxChars: Int): String =
        file.inputStream().use { input ->
            val reader = InputStreamReader(input, Charsets.UTF_8)
            val out = StringBuilder()
            val buf = CharArray(8 * 1024)
            while (out.length < maxChars) {
                val n = reader.read(buf, 0, minOf(buf.size, maxChars - out.length))
                if (n < 0) break
                out.append(buf, 0, n)
            }
            out.toString()
        }
}
