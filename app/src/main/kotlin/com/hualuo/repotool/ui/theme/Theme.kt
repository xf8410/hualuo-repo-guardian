package com.hualuo.repotool.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 设计 token 逐条抄自定稿原型 ui/v13.html 的 :root（v13.1，commit 03c61779）。
// 亮色 ColorOS 卡片风；语义色（成功绿/警告黄/错误红）不随主色变——原型备注「防花」。
val Bg = Color(0xFFF3F4F6)
val CardBg = Color(0xFFFFFFFF)
val Ink = Color(0xFF17181A)
val SubInk = Color(0xFF7D8590)
val Hairline = Color(0xFFECEEF2)
val Accent = Color(0xFF0A5CFF)
val OkGreen = Color(0xFF16A34A)
val WarnAmber = Color(0xFFF59E0B)
val ErrRed = Color(0xFFE5484D)

// 派生色（原型里出现过的固定值）
val MeBubble = Color(0xFFE3ECFF)
val ChipBg = Color(0xFFF4F6FA)
val ToolTint = Color(0xFFF7F9FF)
val QueueTint = Color(0xFFF0F5FF)
val LoopTint = Color(0xFFFFF7E8)
val LoopInk = Color(0xFF9A6B00)
val DangerTint = Color(0xFFFDECEC)
val IconTile = Color(0xFFE9F0FF)
val ThumbBg = Color(0xFFE9EDF3)
val ChevGray = Color(0xFFC6CCD6)
val SwitchOff = Color(0xFFDFE3EA)
val PageShadow = Color(0x0D000000)
val Scrim = Color(0x47000000)
val ToastBg = Color(0xFF22262B)

/** 四态灯的中性档（原型 .dot.n）与徽标默认字色分开摆在这儿。 */
object ToneColors {
    val NeutralDot = Color(0xFFC6CCD6)
}

private val HualuoScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Bg,
    onBackground = Ink,
    surface = CardBg,
    onSurface = Ink,
    surfaceVariant = ChipBg,
    onSurfaceVariant = SubInk,
    outline = Hairline,
    outlineVariant = ChevGray,
    error = ErrRed,
    onError = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
)

private val HualuoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

// 字号对原型：正文 14、卡片标题 12.5、徽标 11、大标题 17；等宽留给数字/SHA（monospace）。
private val HualuoType = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 19.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 11.sp),
)

/** 全 App 唯一入口主题：目前只有亮色（用户拍板「不要黑色要亮色」），暗色留给外观页接线后再开。 */
@Composable
fun HualuoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HualuoScheme,
        shapes = HualuoShapes,
        typography = HualuoType,
        content = content,
    )
}
