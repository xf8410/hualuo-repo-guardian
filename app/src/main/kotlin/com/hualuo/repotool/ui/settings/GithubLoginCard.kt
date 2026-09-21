package com.hualuo.repotool.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.state.UiKeys
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.CardBg
import com.hualuo.repotool.ui.theme.ErrRed
import com.hualuo.repotool.ui.theme.Hairline
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk

/**
 * GitHub 登录卡（2026-09-22 补，用户实报「仓库工具页面不太对，token 登录的输入框呢」）。
 *
 * 旧 Agora 的登录形态实读对照（其 GitHubAuthManager + 工作区页）：令牌输入，验证（/user），
 * 「已登录为谁」与权限清单，可退出。新仓原先只在设置里放了一格长得像只读文字的输入框、
 * 没有任何验证与登录态，用户看到的就是「输入框在哪、登录在哪」。
 *
 * 这一卡把那条链补齐：
 *  - **令牌输入框**：带边框浅底的可见输入框（打点显示），值走设置键 github.token（同一把钥匙全 App 共用）；
 *  - **登录钮**：拿这枚令牌打一次 GitHub /user 验证——空令牌、401、403 都有带出路的原话；
 *  - **登录态行**：已登录为谁 + 权限清单（没声明就如实说「没带声明」，不硬编权限）；
 *    未登录就明说「未登录」——绝不拿「填了令牌」冒充登录态；
 *  - **退出登录**：清登录态，令牌本体不动（删不删由用户在输入框里自己定）。
 *
 * 修记（run 35633392605 的 NoEmoji 红）：本文件 KDoc 初稿里画流程用了箭头字符（U+2192），
 * 被闸门逮住 3 处——家规里图形字符连注释一起禁，写流程一律用「、」或「，」分词，
 * 别再往注释里画箭头。
 *
 * 令牌一改，状态舱里那次验证当场作废（AppUiState.setText 调 GithubLoginState.invalidate）：
 * 屏上不会出现「已登录为旧身份、手里其实是新钥匙」的撒谎态。
 */
@Composable
fun GithubLoginCard(state: AppUiState) {
    val login = state.githubLogin
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GitHub 登录", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.weight(1f))
            val (label, tone) = when {
                login.busy -> "验证中…" to SubInk
                login.account != null -> "已登录" to Accent
                else -> "未登录" to SubInk
            }
            Text(label, fontSize = 12.5.sp, color = tone, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "令牌就是全 App 用的那把（仓库浏览、CI 深看、模型改码都认它）。" +
                "填进下面这格点「登录」验证一次；留在设置文件里明文存着（拍板 D-10），别把备份外传。",
            fontSize = 11.5.sp,
            color = SubInk,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(10.dp))

        // 令牌输入框：可见的框（打点显示），值走设置键 github.token
        Text("访问令牌", fontSize = 12.5.sp, color = SubInk)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Bg)
                .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            val token = state.text(UiKeys.GITHUB_TOKEN)
            if (token.isEmpty()) {
                Text(
                    "粘贴 GitHub 访问令牌（classic 勾 repo 与 workflow；细粒度给 Contents 读）",
                    fontSize = 12.5.sp,
                    color = SubInk,
                )
            }
            BasicTextField(
                value = token,
                onValueChange = { state.setText(UiKeys.GITHUB_TOKEN, it) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = TextStyle(fontSize = 13.sp, color = Ink, fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))

        // 登录 / 退出：两个动作各自成钮；验证中禁点（防连点换账）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (login.busy) SubInk else Accent)
                    .clickable(enabled = !login.busy) {
                        login.login(state.text(UiKeys.GITHUB_TOKEN))
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text(
                    if (login.busy) "验证中…" else "登录",
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (login.account != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Bg)
                        .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                        .clickable(enabled = !login.busy) { login.logout() }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text("退出登录", fontSize = 13.sp, color = Ink)
                }
            }
        }

        // 登录态：验过才写；没验过明说未登录
        Spacer(Modifier.height(8.dp))
        if (login.account != null) {
            Text(
                "已登录为 " + login.account,
                fontSize = 13.sp,
                color = Ink,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (login.scopesText.isBlank()) {
                    "这枚令牌没带权限清单声明（细粒度令牌常见）；能用就行"
                } else {
                    "权限：" + login.scopesText
                },
                fontSize = 11.5.sp,
                color = SubInk,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        } else {
            Text(
                "未登录：还没验证过这把令牌（填了不等于验过）",
                fontSize = 12.sp,
                color = SubInk,
            )
        }

        // 最近一次验证/退出的收场话：问题红、进展灰（判词来自状态舱，界面不猜）
        login.note?.let { note ->
            Spacer(Modifier.height(6.dp))
            Text(
                note,
                fontSize = 12.sp,
                color = if (login.noteIsProblem) ErrRed else SubInk,
                lineHeight = 17.sp,
            )
        }
    }
}
