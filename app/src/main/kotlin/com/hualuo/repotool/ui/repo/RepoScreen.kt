package com.hualuo.repotool.ui.repo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.components.CardTitle
import com.hualuo.repotool.ui.components.HCard
import com.hualuo.repotool.ui.components.LRow
import com.hualuo.repotool.ui.model.Tone
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.theme.WarnAmber

/**
 * 仓库CI页（v13 #p-repo）：**真数据**——GitHub Actions 最近几条 run + 检查更新，
 * 0.7.x 起再加仓库工作台（只读浏览）：自己的仓清单（要令牌）、别人的公开仓、
 * contents 逐级浏览、文件原文预览。改码提交在 B 段另接，这里先不画饼。
 *
 * 数据通道：进页拉一次（已有数据不重复拉），「刷新」行手动重拉；
 * 失败（403 提示去填令牌 / 404 提示核仓库名 / 连不上）与坏条目都摆在明面上，
 * 不拿演示卡冒充 CI 状态——那正是旧版「演示卡撒谎」的同款病。
 */
@Composable
fun RepoScreen(state: AppUiState) {
    // 进页拉一次；LaunchedEffect(Unit) 每次进这个 tab 只跑一遍，不刷屏
    LaunchedEffect(Unit) {
        state.refreshRepoCiIfStale()
        state.refreshMyReposIfStale()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        HCard {
            CardTitle(state.ciRepoLabel)
            when {
                state.ciBusy && state.ciRuns.isEmpty() -> Text(
                    "正在拉 CI 记录…",
                    fontSize = 13.sp,
                    color = SubInk,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                state.ciRuns.isEmpty() && state.ciError != null -> Text(
                    state.ciError ?: "",
                    fontSize = 13.sp,
                    color = WarnAmber,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                state.ciRuns.isEmpty() -> Text(
                    "还没拉到记录：点下面「刷新」从 GitHub 重拉",
                    fontSize = 13.sp,
                    color = SubInk,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                else -> state.ciRuns.forEach { run ->
                    // 绿灯=成功；红=失败/超时/取消；黄=还在跑（conclusion 还没给）
                    val dot = when (run.conclusion) {
                        "success" -> Tone.Ok
                        "failure", "timed_out", "cancelled" -> Tone.Err
                        else -> Tone.Warn
                    }
                    LRow(
                        "run ${run.id}",
                        "${run.conclusion ?: run.status} · ${run.headSha.take(7)}",
                        dot = dot,
                    )
                }
            }
            if (state.ciBadEntries > 0) {
                Text(
                    "有 ${state.ciBadEntries} 条记录读不懂，已跳过（其余照常显示）",
                    fontSize = 11.5.sp,
                    color = WarnAmber,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            LRow("刷新", chevron = true) { state.refreshRepoCi() }
        }

        // 我的仓库（含私有，要令牌）：点一行就进去浏览
        HCard {
            CardTitle("我的仓库（含私有，要令牌）")
            when {
                state.myReposBusy && state.myRepos.isEmpty() -> Text(
                    "正在拉仓库清单…",
                    fontSize = 13.sp,
                    color = SubInk,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                state.myRepos.isEmpty() && state.myReposNote != null -> Text(
                    state.myReposNote ?: "",
                    fontSize = 12.5.sp,
                    color = WarnAmber,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                else -> state.myRepos.forEach { repo ->
                    LRow(
                        repo.fullName,
                        (if (repo.isPrivate) "私有" else "公开") + " · " + repo.updatedAt.take(10),
                        chevron = true,
                    ) {
                        state.closeFileView()
                        state.browseInto(repo.fullName)
                    }
                }
            }
            state.myReposNote?.let { note ->
                if (state.myRepos.isNotEmpty()) {
                    Text(note, fontSize = 11.5.sp, color = SubInk, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            LRow("刷新清单", chevron = true) { state.refreshMyRepos() }
        }

        // 看别人的仓（公开只读）：owner/name，粘整条链接也认
        HCard {
            CardTitle("看别人的仓（公开只读）")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Bg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (state.otherRepoQuery.isEmpty()) {
                        Text("owner/name，粘整条链接也认", fontSize = 13.sp, color = SubInk)
                    }
                    BasicTextField(
                        value = state.otherRepoQuery,
                        onValueChange = { state.otherRepoQuery = it },
                        textStyle = TextStyle(fontSize = 13.sp, color = Ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "打开",
                    fontSize = 13.sp,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Bg)
                        .clickable { state.browseOtherRepo() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        // 浏览卡：进了仓库才出现；目录在前，「返回上一级」走回退栈
        if (state.browseRepo.isNotEmpty()) {
            HCard {
                CardTitle("浏览 " + state.browseRepo + (if (state.browseRef != null) " @" + state.browseRef else ""))
                Text(
                    "/" + state.browsePath,
                    fontSize = 11.5.sp,
                    color = SubInk,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                when {
                    state.browseBusy -> Text(
                        "正在拉目录…",
                        fontSize = 13.sp,
                        color = SubInk,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    state.browseEntries.isEmpty() && state.browseNote != null -> Text(
                        state.browseNote ?: "",
                        fontSize = 12.5.sp,
                        color = WarnAmber,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    state.browseEntries.isEmpty() -> Text(
                        "这个目录是空的",
                        fontSize = 12.5.sp,
                        color = SubInk,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    else -> state.browseEntries.forEach { entry ->
                        LRow(
                            entry.name,
                            if (entry.isDir) "目录" else formatBytes(entry.sizeBytes),
                            chevron = true,
                        ) {
                            if (entry.isDir) state.browseDown(entry) else state.openBrowseFile(entry)
                        }
                    }
                }
                state.browseNote?.let { note ->
                    if (state.browseEntries.isNotEmpty()) {
                        Text(note, fontSize = 11.5.sp, color = WarnAmber, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                if (state.browseTrail.isNotEmpty()) {
                    LRow("返回上一级", chevron = true) { state.browseUp() }
                }
                LRow("退出浏览", chevron = true) { state.exitBrowse() }
            }
        }

        // 文件预览卡：原文，二进制/截断/超限都明说
        if (state.fileViewPath.isNotEmpty()) {
            HCard {
                CardTitle("文件：" + state.fileViewPath)
                when {
                    state.fileViewBusy -> Text(
                        "正在拉文件…",
                        fontSize = 13.sp,
                        color = SubInk,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    state.fileViewText == null -> Text(
                        state.fileViewNote ?: "没有内容可给",
                        fontSize = 12.5.sp,
                        color = WarnAmber,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    else -> {
                        state.fileViewNote?.let { note ->
                            Text(note, fontSize = 11.5.sp, color = SubInk, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text(
                            state.fileViewText ?: "",
                            fontSize = 11.sp,
                            color = Ink,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                    }
                }
                LRow("收起", chevron = true) { state.closeFileView() }
            }
        }

        Spacer(Modifier.height(10.dp))
        HCard {
            CardTitle("版本")
            Text(
                state.updateNote ?: "拿当前版本对 GitHub 最新发布版；还没发布过会直说",
                fontSize = 12.5.sp,
                color = SubInk,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            LRow("检查更新", chevron = true) { state.checkUpdate() }
        }
    }
}

/** 体积给人话：KB 以下按字节，往上到 MB（清单接口给的本来就是字节账）。 */
private fun formatBytes(bytes: Long): String = when {
    bytes < 0L -> "未知大小"
    bytes < 1024L -> "${bytes}B"
    bytes < 1024L * 1024L -> "${bytes / 1024L}KB"
    else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
}
