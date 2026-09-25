package com.hualuo.repotool.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.engine.apk.ApkInspector
import com.hualuo.engine.apk.render
import com.hualuo.repotool.ui.components.CardTitle
import com.hualuo.repotool.ui.components.HCard
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.theme.WarnAmber
import java.io.File

/**
 * APK 检查卡（560 清单 121-160 域的界面）：SAF 选 APK -> 流式拷临时文件 ->
 * 引擎检查（魔数/条目/ABI/重复/SHA-256）-> 报告展示 -> 临时文件即删。
 * 拷贝与检查都在后台线程；状态层只存文本收场。
 */
@Composable
fun ApkCheckCard(state: AppUiState) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !state.apkChecking) {
            state.apkChecking = true
            state.apkReport = null
            Thread({
                var temp: File? = null
                val report: String = try {
                    temp = File(context.cacheDir, "apk-check-${System.currentTimeMillis()}.apk")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output ->
                            com.hualuo.engine.io.streamingCopy(input, output)
                        }
                    }
                    if (temp.length() == 0L) {
                        "检查失败：文件读不动（可能已失效或不是本地文件）"
                    } else {
                        ApkInspector.inspect(temp).render()
                    }
                } catch (e: Exception) {
                    "检查失败：${e.message ?: e.javaClass.simpleName}"
                } finally {
                    temp?.delete()
                }
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
            ) {
                picker.launch(arrayOf("application/vnd.android.package-archive", "application/zip", "application/octet-stream"))
            }
        }
        Spacer(Modifier.height(6.dp))
        val report = state.apkReport
        if (report != null) {
            if (report.startsWith("检查失败")) {
                Text(report, fontSize = 12.sp, color = WarnAmber)
            } else {
                Text(report, fontSize = 12.sp, color = Ink)
            }
        } else if (!state.apkChecking) {
            Text("选一个 APK / Split APK 看清单账。", fontSize = 12.sp, color = SubInk)
        }
    }
}

@Composable
private fun Box2Button(label: String, enabled: Boolean = true, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (accent) Accent else Bg, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, fontSize = 13.sp, color = if (accent) Color.White else Ink, fontWeight = FontWeight.SemiBold)
    }
}
