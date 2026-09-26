package com.hualuo.repotool.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.engine.github.GitHubCommitWritten
import com.hualuo.engine.github.GitHubRepoClient
import com.hualuo.engine.language.HexDump
import com.hualuo.engine.language.LangRegistry

/**
 * 查看器状态舱（全语言查看器 + 进制查看器 + 全格式上传，红线三拆件）。
 *
 * 内存纪律：文件内容**永远不整载**——文本按行块装（每块 [TEXT_BLOCK_LINES] 行，
 * 「加载更多」续读）；二进制按页 hex（每页 [HEX_PAGE_BYTES] 字节，翻页 seek 重读）。
 * 上传走引擎分卷流式（90MB 卷、断点续传），进度回调画进度条，不让人干等。
 */
class ViewerUiState(private val persist: UiPersistence) {

    // ── 打开的文件 ──
    var openedName by mutableStateOf<String?>(null)
        private set
    var openedSizeBytes by mutableStateOf(0L)
        private set
    var isBinary by mutableStateOf(false)
        private set
    var langName by mutableStateOf<String?>(null)
        private set

    /** 文本查看：已加载的行（每行带染色账在 UI 层现算）。 */
    var textLines by mutableStateOf<List<String>>(emptyList())
        private set
    var textNote by mutableStateOf<String?>(null)
        private set
    var hasMoreText by mutableStateOf(false)
        private set

    /** 二进制查看：当前 hex 页。 */
    var hexRows by mutableStateOf<List<HexDump.DumpRow>>(emptyList())
        private set
    var hexOffset by mutableStateOf(0L)
        private set
    var hexNote by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set
    var note by mutableStateOf<String?>(null)

    // ── 进制换算卡 ──
    var radixInput by mutableStateOf("255")
    var radixView by mutableStateOf<HexDump.RadixView?>(null)
    var radixNote by mutableStateOf<String?>(null)
    var floatInput by mutableStateOf("1.5")
    var floatView by mutableStateOf<HexDump.FloatView?>(null)

    // ── 上传 ──
    var uploadRepo by mutableStateOf(persist.load(UiKeys.GITHUB_REPO) ?: "")
    var uploadBranch by mutableStateOf("main")
    var uploadPath by mutableStateOf("")
    var uploadMessage by mutableStateOf("")
    var uploading by mutableStateOf(false)
        private set
    var uploadDone by mutableStateOf(0L)
        private set
    var uploadTotal by mutableStateOf(0L)
        private set
    var uploadProgressNote by mutableStateOf<String?>(null)
        private set
    var uploadResult by mutableStateOf<String?>(null)
        private set

    /** 当前打开文件的读取口（上传/hex 翻页要重读同一份）。 */
    private var reopen: (() -> java.io.InputStream)? = null

    /** 上传入口：直接用当前打开文件的读取口（页面不再摸 URI）。 */
    fun startUploadCurrent() {
        val open = reopen ?: run { uploadResult = "先选一个文件再传"; return }
        startUpload(open, openedSizeBytes, openedName ?: "未命名")
    }

    /**
     * 打开一个文件：先读 8KB 嗅探（文本/二进制），再分流。
     * 文本：行块加载；二进制：首页 hex。大文件全程流式，不闪退。
     */
    fun openStream(name: String, sizeBytes: Long, open: () -> java.io.InputStream) {
        if (busy) return
        busy = true
        note = null
        Thread {
            try {
                reopen = open
                openedName = name
                openedSizeBytes = sizeBytes
                val head = open().use { input ->
                    val buf = ByteArray(8192)
                    val n = input.read(buf)
                    if (n <= 0) ByteArray(0) else buf.copyOf(n)
                }
                val bin = HexDump.looksBinary(head) || !HexDump.decodableAsText(head)
                isBinary = bin
                if (bin) {
                    langName = "二进制"
                    textLines = emptyList()
                    textNote = null
                    hasMoreText = false
                    loadHexPage(0L)
                } else {
                    langName = LangRegistry.byFileName(name).name
                    hexRows = emptyList()
                    loadTextFrom(open)
                }
            } catch (e: Exception) {
                note = "读不出来（${e.message ?: e::class.java.simpleName}）：授权可能失效，重选一次"
            } finally {
                busy = false
            }
        }.start()
    }

    /** 文本加载：从流头按行读一个块（「加载更多」再读下一块）。 */
    private fun loadTextFrom(open: () -> java.io.InputStream) {
        val lines = ArrayList<String>(TEXT_BLOCK_LINES)
        var more = false
        open().use { input ->
            java.io.BufferedReader(java.io.InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                while (lines.size < TEXT_BLOCK_LINES) {
                    val l = reader.readLine() ?: break
                    lines.add(l.take(MAX_LINE_CHARS))
                }
                more = reader.readLine() != null
            }
        }
        textLines = lines
        hasMoreText = more
        textNote = if (more) "已显示前 ${lines.size} 行（大文件分块加载，点「加载更多」续读）" else "共 ${lines.size} 行"
    }

    /** 「加载更多」：文本继续追一块（从已加载行数往后读，重开流跳行）。 */
    fun loadMoreText() {
        if (busy || !hasMoreText) return
        val already = textLines.size
        busy = true
        Thread {
            try {
                val open = reopen ?: return@Thread
                val lines = ArrayList(textLines)
                open().use { input ->
                    java.io.BufferedReader(java.io.InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                        var skipped = 0
                        while (skipped < already) {
                            reader.readLine() ?: break
                            skipped++
                        }
                        var added = 0
                        while (added < TEXT_BLOCK_LINES) {
                            val l = reader.readLine() ?: break
                            lines.add(l.take(MAX_LINE_CHARS))
                            added++
                        }
                        hasMoreText = reader.readLine() != null
                    }
                }
                textLines = lines
                textNote = if (hasMoreText) "已显示前 ${lines.size} 行（点「加载更多」续读）" else "共 ${lines.size} 行"
            } catch (e: Exception) {
                note = "续读失败（${e.message ?: "出错"}）：重开一次文件即可"
            } finally {
                busy = false
            }
        }.start()
    }

    /** hex 翻页：seek 到 [offset] 读一页 4KB 排 dump（大文件按页看，不整载）。 */
    fun loadHexPage(offset: Long) {
        if (busy) return
        busy = true
        Thread {
            try {
                val open = reopen ?: return@Thread
                val page = open().skipLong(offset).use { input ->
                    val buf = ByteArray(HEX_PAGE_BYTES)
                    var filled = 0
                    while (filled < HEX_PAGE_BYTES) {
                        val n = input.read(buf, filled, HEX_PAGE_BYTES - filled)
                        if (n < 0) break
                        filled += n
                    }
                    buf.copyOf(filled)
                }
                hexOffset = offset
                hexRows = HexDump.dump(page, offset)
                val end = offset + page.size
                hexNote = "第 ${com.hualuo.engine.language.HexDump.humanBytes(offset)} 到 ${HexDump.humanBytes(end)} / 共 ${HexDump.humanBytes(openedSizeBytes)}"
            } catch (e: Exception) {
                note = "hex 读取失败（${e.message ?: "出错"}）"
            } finally {
                busy = false
            }
        }.start()
    }

    // ── 进制换算 ──

    fun computeRadix() {
        radixView = HexDump.radix(radixInput)
        radixNote = if (radixView == null) "读不懂：支持 10/0x 十六/0b 二/0o 八进制，超 Long 范围也不行" else null
    }

    fun computeFloat() {
        val v = floatInput.trim().toDoubleOrNull()
        floatView = if (v == null) null else HexDump.floatView(v)
        if (v == null) radixNote = "浮点输入读不懂（要 1.5 这种十进制数）"
    }

    // ── 上传（引擎分卷流式 + 进度回调） ──

    fun startUpload(open: () -> java.io.InputStream, sizeBytes: Long, fileName: String) {
        if (uploading) return
        val repo = uploadRepo.trim()
        if (!repo.contains('/') || repo.startsWith("http")) {
            uploadResult = "仓库写法不对：要 owner/name（比如 xf8410/uma-data）"
            return
        }
        val path = uploadPath.trim().trim('/').ifBlank { fileName }
        val msg = uploadMessage.trim().ifBlank { "上传 $fileName（查看器投递）" }
        uploading = true
        uploadTotal = sizeBytes
        uploadDone = 0L
        uploadProgressNote = "准备上传…"
        uploadResult = null
        Thread {
            try {
                val client = GitHubRepoClient()
                val token = persist.load(UiKeys.GITHUB_TOKEN)?.trim()?.takeIf { it.isNotEmpty() }
                val src = GitHubRepoClient.UploadSource(fileName, sizeBytes, open)
                val result = client.uploadFile(
                    repo, path, uploadBranch.trim(), msg, src, token,
                ) { done, total, noteLine ->
                    uploadDone = done
                    uploadTotal = total
                    uploadProgressNote = noteLine
                }
                uploadResult = result.error ?: "上传完成：$path（commit ${result.commitSha?.take(7)}）"
            } catch (e: Exception) {
                uploadResult = "上传没跑成（${e.message ?: e::class.java.simpleName}）：直接重传，已传的卷会自动跳过"
            } finally {
                uploading = false
            }
        }.start()
    }

    /** InputStream 跳过 [n] 字节：skip() 不保证跳满的老账在这里兜（循环读丢弃）。 */
    private fun java.io.InputStream.skipLong(n: Long): java.io.InputStream {
        var left = n
        val buf = ByteArray(64 * 1024)
        while (left > 0) {
            val got = read(buf, 0, minOf(left, buf.size.toLong()).toInt())
            if (got < 0) break
            left -= got
        }
        return this
    }

    companion object {
        /** 文本一块的行数：2000 行约几百 KB 内存，两块以内覆盖绝大多数源码文件。 */
        const val TEXT_BLOCK_LINES = 2000

        /** 单行封顶字符（超长行截显，minified 文件保护）。 */
        const val MAX_LINE_CHARS = 2000

        /** hex 一页字节数：4KB=256 行 dump，翻页浏览。 */
        const val HEX_PAGE_BYTES = 4096
    }
}
