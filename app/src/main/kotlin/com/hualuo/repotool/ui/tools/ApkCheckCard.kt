package com.hualuo.repotool.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.engine.apk.ApkInspector
import com.hualuo.repotool.ui.components.CardTitle
import com.hualuo.repotool.ui.components.HCard
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.theme.WarnAmber
import java.io.File

/**
 * APK 检查卡（560 清单 121-160 域的界面）：SAF 选 APK -> 拷临时文件（流式）->
 * 引擎检查（魔数/条目清单/ABI/重复文件/SHA-256）-> 多行报告。
 * 引擎件全测过；这里只是文件搬运与展示。
 */
@Composable
fun ApkCheckCard(state: AppUiState) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !state.apkChecking) {
            state.apkChecking = true
            state.apkReport = null
            Thread({
                val report = runCatching {
                    val name = queryDisplayName(context, uri) ?: "picked.apk"
                    val temp = File(context.cacheDir, "apk_check_${System.currentTimeMillis()}.bin")
                    try {
                        // SAF 流式拷到临时文件（ZipFile 需要真文件；streamingCopy 不把整包读进内存）
                        context.contentResolver.openInputStream(uri)!!.use { input ->
                            temp.outputStream().use { output ->
                                com.hualuo.engine.io.streamingCopy(input, output)
                            }
                        }
                        com.hualuo.engine.apk.render(ApkInspector.inspect(temp))
                    } finally {
                        temp.delete()
                    }
                }.getOrElse { e -> "检查失败：${e.message ?: e.javaClass.simpleName}" }
                state.apkChecking = false
                state.apkReport = report
            }, "hualuo-apk-check").start()
        }
    }

    HCard {
        CardTitle("APK 检查（魔数/条目/ABI/重复/SHA-256）")
        Row {
            Box2Button(
                label = if (state.apkChecking) "查着…" else "选 APK 检查",
                enabled = !state.apkChecking,
                accent = true,
            ) { picker.launch(arrayOf("application/vnd.android.package-archive", "application/zip", "*/*")) }
        }
        Spacer(Modifier.height(6.dp))
        state.apkReport?.let { report ->
            if (report.startsWith("检查失败")) {
                Text(report, fontSize = 12.sp, color = WarnAmber)
            } else {
                Text(report, fontSize = 12.sp, color = Ink)
            }
        } ?: run {
            if (!state.apkChecking) {
                Text("选一个 APK / Split APK / XAPK 看清单账。", fontSize = 12.sp, color = SubInk)
            }
        }
    }
}
