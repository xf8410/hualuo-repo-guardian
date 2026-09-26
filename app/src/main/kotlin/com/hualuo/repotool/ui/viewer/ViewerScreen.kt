package com.hualuo.repotool.ui.viewer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.hualuo.engine.language.Highlight
import com.hualuo.engine.language.HexDump
import com.hualuo.engine.language.LangRegistry
import com.hualuo.repotool.ui.components.BadgeChip
import com.hualuo.repotool.ui.components.CardTitle
import com.hualuo.repotool.ui.components.HCard
import com.hualuo.repotool.ui.components.LRow
import com.hualuo.repotool.ui.model.Tone
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.theme.OkGreen
import com.hualuo.repotool.ui.theme.WarnAmber
import com.hualuo.repotool.ui.theme.ErrRed

/**
 * 查看器卡（工具页内嵌）：全语言查看器 + 进制查看器 + 全格式上传。
 *
 * 「所有语言」的落点：扩展名全量映射（认不得的也按纯文本照显，永不报不支持）；
 * 「所有格式」的落点：文本按行块看、二进制按页 hex 看、上传分卷流式（无大小上限、
 * 断点续传、进度条实时）。
 */
@Composable
fun ViewerCard(state: AppUiState) {
    val v = state.viewer
    val ctx = LocalContext.current
    var fontSize by remember { mutableStateOf(12f) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = queryName(ctx, uri) ?: "未命名"
            val size = querySize(ctx, uri) ?: 0L
            v.openStream(name, size) {
                ctx.contentResolver.openInputStream(uri) ?: throw RuntimeException("打不开：授权失效")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))

        // ── 打开文件 ──
        HCard {
            CardTitle("查看器（所有语言 / 二进制 / 进制）")
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("选一个文件（任意格式）") }
            if (v.openedName != null) {
                LRow("文件", v.openedName)
                LRow("大小", HexDump.humanBytes(v.openedSizeBytes))
                Row {
                    BadgeChip(v.langName ?: "未知", tone = Tone.Ok)
                    Spacer(Modifier.width(8.dp))
                    if (v.busy) BadgeChip("读取中", tone = Tone.Neutral)
                }
            }
            if (v.note != null) Text(v.note!!, fontSize = 12.sp, color = ErrRed)
        }

        // ── 代码/文本查看（所有语言染色） ──
        if (v.openedName != null && !v.isBinary && v.textLines.isNotEmpty()) {
            HCard {
                CardTitle("${v.langName ?: "文本"} 内容（染色查看）")
                Row {
                    TextButton(onClick = { if (fontSize > 8f) fontSize -= 1f }) { Text("A-") }
                    TextButton(onClick = { if (fontSize < 20f) fontSize += 1f }) { Text("A+") }
                    if (v.hasMoreText) {
                        TextButton(onClick = { v.loadMoreText() }) { Text("加载更多") }
                    }
                }
                LazyColumn(modifier = Modifier.height(420.dp)) {
                    items(v.textLines.size) { i ->
                        val line = v.textLines[i]
                        Text(
                            text = colorized(line, v),
                            fontSize = fontSize.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = (fontSize * 1.4f).sp,
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        )
                    }
                }
                if (v.textNote != null) Text(v.textNote!!, fontSize = 11.5.sp, color = SubInk)
            }
        }

        // ── 进制查看（hex dump 翻页） ──
        if (v.openedName != null && v.isBinary && v.hexRows.isNotEmpty()) {
            HCard {
                CardTitle("十六进制查看（进制查看器）")
                Row {
                    TextButton(onClick = { v.loadHexPage((v.hexOffset - 4096).coerceAtLeast(0)) }) { Text("上一页") }
                    TextButton(onClick = { v.loadHexPage(v.hexOffset + 4096) }) { Text("下一页") }
                }
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(v.hexRows.size) { i ->
                        val r = v.hexRows[i]
                        Text(
                            text = buildAnnotatedString {
                                append(String.format("%08x  ", r.offset))
                                append(r.hex)
                                append("  ")
                                append(r.ascii)
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        )
                    }
                }
                if (v.hexNote != null) Text(v.hexNote!!, fontSize = 11.5.sp, color = SubInk)
            }
        }

        // ── 进制换算卡 ──
        HCard {
            CardTitle("进制换算（2/8/10/16 + 浮点）")
            FieldLine("数字（255 / 0xff / 0b1010 / 0o377）", v.radixInput) { v.radixInput = it }
            TextButton(onClick = { v.computeRadix() }) { Text("换算") }
            val rv = v.radixView
            if (rv != null) {
                LRow("十进制", rv.dec)
                LRow("十六进制", rv.hex)
                LRow("二进制", rv.bin)
                LRow("八进制", rv.oct)
                Text(rv.bits, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = SubInk)
            }
            if (v.radixNote != null) Text(v.radixNote!!, fontSize = 11.5.sp, color = ErrRed)
            Spacer(Modifier.height(6.dp))
            FieldLine("浮点（如 1.5）", v.floatInput) { v.floatInput = it }
            TextButton(onClick = { v.computeFloat() }) { Text("分解") }
            val fv = v.floatView
            if (fv != null) {
                LRow("值", fv.value.toString())
                LRow("符号位", fv.sign.toString())
                LRow("指数位", fv.exponentBits)
                Text("尾数 ${fv.mantissaBits}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = SubInk)
                Text("位账 ${fv.hexBits}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = SubInk)
            }
        }

        // ── 上传（全格式分卷 + 进度条） ──
        if (v.openedName != null) {
            HCard {
                CardTitle("上传到 GitHub（任意大小，分卷流式）")
                FieldLine("仓库 owner/name", v.uploadRepo) { v.uploadRepo = it }
                FieldLine("分支", v.uploadBranch) { v.uploadBranch = it }
                FieldLine("目标路径（空=文件名）", v.uploadPath) { v.uploadPath = it }
                FieldLine("commit 说明", v.uploadMessage) { v.uploadMessage = it }
                if (v.uploading) {
                    val frac = if (v.uploadTotal > 0) (v.uploadDone.toFloat() / v.uploadTotal) else 0f
                    LinearProgressIndicator(
                        progress = { frac.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                    Text(
                        "${HexDump.humanBytes(v.uploadDone)} / ${HexDump.humanBytes(v.uploadTotal)} · ${v.uploadProgressNote ?: ""}",
                        fontSize = 11.5.sp, color = SubInk,
                    )
                    TextButton(onClick = {}) { Text("传完后自动对账，中途断了直接重传（已传卷会跳过）") }
                } else {
                    TextButton(onClick = { v.startUploadCurrent() }) { Text("开始上传") }
                }
                if (v.uploadResult != null) {
                    Text(v.uploadResult!!, fontSize = 12.sp, color = if (v.uploadResult!!.startsWith("上传完成")) OkGreen else ErrRed)
                }
            }
        }

        HCard {
            CardTitle("说明")
            LRow("语言映射", "${LangRegistry.extCount()} 个扩展名，认不得的照样显")
            LRow("大文件", "文本分块续读，二进制按页 hex，都不整载内存")
            LRow("大上传", "90MB 一卷自动切，断点续传，无大小上限")
        }
    }
}

@Composable
private fun FieldLine(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 11.5.sp, color = SubInk)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(fontSize = 13.sp, color = Ink, fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 一行文本的染色账（行内现算，行数多时只染可见行——LazyColumn 天然惰性）。 */
private fun colorized(line: String, v: com.hualuo.repotool.ui.state.ViewerUiState): AnnotatedString {
    return buildAnnotatedString {
        append(line)
        // 染色账按当前语言现算单行（跨行块注释的状态在整文染色里，单行退化可接受：
        // 查看器要的是看得清，不是编译器）
        val lang = LangRegistry.allLangs().firstOrNull { it.name == v.langName }
        if (lang != null && line.length < 2000) {
            val spans = Highlight.highlight(line, lang).firstOrNull()?.spans ?: emptyList()
            for (s in spans) {
                val color = when (s.kind) {
                    Highlight.Kind.COMMENT -> SubInk
                    Highlight.Kind.STRING -> OkGreen
                    Highlight.Kind.NUMBER -> WarnAmber
                    Highlight.Kind.KEYWORD -> ErrRed
                    Highlight.Kind.PLAIN -> Ink
                }
                if (s.start < line.length && s.end <= line.length && s.end > s.start) {
                    addStyle(SpanStyle(color = color), s.start, s.end)
                }
            }
        }
    }
}

private fun queryName(ctx: android.content.Context, uri: android.net.Uri): String? = runCatching {
    ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

private fun querySize(ctx: android.content.Context, uri: android.net.Uri): Long? = runCatching {
    ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getLong(0) else null
    }
}.getOrNull()
