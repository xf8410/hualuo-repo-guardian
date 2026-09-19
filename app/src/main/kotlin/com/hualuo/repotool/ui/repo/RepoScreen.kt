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
import androidx.compose.ui.graphics.Color
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
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.theme.WarnAmber

/**
 * 仓库CI页（v13 #p-repo）：**真数据**——GitHub Actions 最近几条 run + 检查更新，
 * 外加仓库工作台（状态舱 state.repo）：自己的仓清单（要令牌）、别人的公开仓、
 * contents 逐级浏览、分支切换、提交历史（维护记录）、文件原文预览、**改码提交**
 * （sha 对账、冲突出声不硬盖）、**CI 深看三层**（runs、jobs、日志，不跳网页）。
 */
@Composable
fun RepoScreen(state: AppUiState) {
    // 进页拉一次；LaunchedEffect(Unit) 每次进这个 tab 只跑一遍，不刷屏
    LaunchedEffect(Unit) {
        state.refreshRepoCiIfStale()
        state.repo.refreshMyReposIfStale()
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
                state.repo.myReposBusy && state.repo.myRepos.isEmpty() -> Text(
                    "正在拉仓库清单…",
                    fontSize = 13.sp,
                    color = SubInk,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                state.repo.myRepos.isEmpty() && state.repo.myReposNote != null -> Text(
                    state.repo.myReposNote ?: "",
                    fontSize = 12.5.sp,
                    color = WarnAmber,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                else -> state.repo.myRepos.forEach { repo ->
                    LRow(
                        repo.fullName,
                        (if (repo.isPrivate) "私有" else "公开") + " · " + repo.updatedAt.take(10),
                        chevron = true,
                    ) {
                        state.repo.closeFileView()
                        state.repo.browseInto(repo.fullName)
                    }
                }
            }
            state.repo.myReposNote?.let { note ->
                if (state.repo.myRepos.isNotEmpty()) {
                    Text(note, fontSize = 11.5.sp, color = SubInk, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            LRow("刷新清单", chevron = true) { state.repo.refreshMyRepos() }
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
                    if (state.repo.otherRepoQuery.isEmpty()) {
                        Text("owner/name，粘整条链接也认", fontSize = 13.sp, color = SubInk)
                    }
                    BasicTextField(
                        value = state.repo.otherRepoQuery,
                        onValueChange = { state.repo.otherRepoQuery = it },
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
                        .clickable { state.repo.browseOtherRepo() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        // 浏览卡：进了仓库才出现；分支切换 + 目录树 + 提交历史 + CI 深看
        if (state.repo.browseRepo.isNotEmpty()) {
            HCard {
                CardTitle(
                    "浏览 " + state.repo.browseRepo +
                        (if (state.repo.browseRef != null) " @" + state.repo.browseRef else ""),
                )
                Text(
                    "/" + state.repo.browsePath,
                    fontSize = 11.5.sp,
                    color = SubInk,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                // 分支切换（官方 App 最欠的一格）：切了路径回根重新走
                LRow("切换分支", state.repo.browseRef ?: "默认分支", chevron = true) {
                    state.repo.toggleBranchPicker()
                }
                if (state.repo.branchPickerOpen) {
                    when {
                        state.repo.branchListBusy -> Text(
                            "正在拉分支…",
                            fontSize = 12.5.sp,
                            color = SubInk,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        state.repo.branchList.isEmpty() && state.repo.branchListNote != null -> Text(
                            state.repo.branchListNote ?: "",
                            fontSize = 12.sp,
                            color = WarnAmber,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        else -> state.repo.branchList.forEach { branch ->
                            LRow(branch.name, branch.commitSha.take(7), chevron = true) {
                                state.repo.switchBranch(branch.name)
                            }
                        }
                    }
                    state.repo.branchListNote?.let { note ->
                        if (state.repo.branchList.isNotEmpty()) {
                            Text(note, fontSize = 11.sp, color = WarnAmber, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
                when {
                    state.repo.browseBusy -> Text(
                        "正在拉目录…",
                        fontSize = 13.sp,
                        color = SubInk,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    state.repo.browseEntries.isEmpty() && state.repo.browseNote != null -> Text(
                        state.repo.browseNote ?: "",
                        fontSize = 12.5.sp,
                        color = WarnAmber,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    state.repo.browseEntries.isEmpty() -> Text(
                        "这个目录是空的",
                        fontSize = 12.5.sp,
                        color = SubInk,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    else -> state.repo.browseEntries.forEach { entry ->
                        LRow(
                            entry.name,
                            if (entry.isDir) "目录" else formatBytes(entry.sizeBytes),
                            chevron = true,
                        ) {
                            if (entry.isDir) state.repo.browseDown(entry) else state.repo.openBrowseFile(entry)
                        }
                    }
                }
                state.repo.browseNote?.let { note ->
                    if (state.repo.browseEntries.isNotEmpty()) {
                        Text(note, fontSize = 11.5.sp, color = WarnAmber, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                if (state.repo.browseTrail.isNotEmpty()) {
                    LRow("返回上一级", chevron = true) { state.repo.browseUp() }
                }
                // 提交历史（维护记录）：当前分支最近干了什么，新在前
                LRow(
                    "提交历史",
                    if (state.repo.commitsOpen) {
                        "收起"
                    } else if (state.repo.commits.isNotEmpty()) {
                        "${state.repo.commits.size} 条"
                    } else {
                        null
                    },
                    chevron = true,
                ) { state.repo.toggleCommits() }
                if (state.repo.commitsOpen) {
                    when {
                        state.repo.commitsBusy -> Text(
                            "正在拉提交历史…",
                            fontSize = 12.5.sp,
                            color = SubInk,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        state.repo.commits.isEmpty() -> Text(
                            state.repo.commitsNote ?: "还没有提交记录",
                            fontSize = 12.sp,
                            color = if (state.repo.commitsNote != null) WarnAmber else SubInk,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        else -> state.repo.commits.forEach { commit ->
                            LRow(
                                commit.sha.take(7),
                                (commit.messageFirstLine.take(28) + " · " + commit.author).trim(),
                                chevron = false,
                            )
                        }
                    }
                    state.repo.commitsNote?.let { note ->
                        if (state.repo.commits.isNotEmpty()) {
                            Text(note, fontSize = 11.sp, color = WarnAmber, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                    LRow("刷新历史", chevron = true) { state.repo.refreshCommits() }
                }
                // CI 深看三层：runs、点 run 看 jobs、点 job 看日志；不跳网页
                LRow(
                    "查看 CI",
                    if (state.repo.browseCiOpen) {
                        "收起"
                    } else if (state.repo.ciRunsList.isNotEmpty()) {
                        "${state.repo.ciRunsList.size} 条"
                    } else {
                        null
                    },
                    chevron = true,
                ) { state.repo.toggleBrowseCi() }
                if (state.repo.browseCiOpen) {
                    when {
                        state.repo.ciRunsBusy -> Text(
                            "正在拉 CI 记录…",
                            fontSize = 12.5.sp,
                            color = SubInk,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        state.repo.ciRunsList.isEmpty() && state.repo.ciRunsNote != null -> Text(
                            state.repo.ciRunsNote ?: "",
                            fontSize = 12.sp,
                            color = WarnAmber,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        state.repo.ciRunsList.isEmpty() -> Text(
                            "还没有 workflow 记录",
                            fontSize = 12.sp,
                            color = SubInk,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        else -> state.repo.ciRunsList.forEach { run ->
                            val dot = when (run.conclusion) {
                                "success" -> Tone.Ok
                                "failure", "timed_out", "cancelled" -> Tone.Err
                                else -> Tone.Warn
                            }
                            LRow(
                                "run ${run.id}",
                                "${run.conclusion ?: run.status} · ${run.headSha.take(7)}",
                                dot = dot,
                                chevron = true,
                            ) { state.repo.openRunJobs(run.id) }
                        }
                    }
                    state.repo.ciRunsNote?.let { note ->
                        if (state.repo.ciRunsList.isNotEmpty()) {
                            Text(note, fontSize = 11.sp, color = WarnAmber, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                    LRow("刷新 CI", chevron = true) { state.repo.refreshBrowseCi() }
                    if (state.repo.ciJobsRunId != null) {
                        Text(
                            "run " + state.repo.ciJobsRunId + " 的 jobs：",
                            fontSize = 12.sp,
                            color = SubInk,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        when {
                            state.repo.ciJobsBusy -> Text(
                                "正在拉 jobs…",
                                fontSize = 12.5.sp,
                                color = SubInk,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                            state.repo.ciJobsList.isEmpty() && state.repo.ciJobsNote != null -> Text(
                                state.repo.ciJobsNote ?: "",
                                fontSize = 12.sp,
                                color = WarnAmber,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                            else -> state.repo.ciJobsList.forEach { job ->
                                val dot = when (job.conclusion) {
                                    "success" -> Tone.Ok
                                    "failure", "timed_out", "cancelled" -> Tone.Err
                                    else -> Tone.Warn
                                }
                                LRow(job.name, job.conclusion ?: job.status, dot = dot, chevron = true) {
                                    state.repo.openJobLog(job.id)
                                }
                            }
                        }
                        state.repo.ciJobsNote?.let { note ->
                            if (state.repo.ciJobsList.isNotEmpty()) {
                                Text(note, fontSize = 11.sp, color = WarnAmber, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                        if (state.repo.ciLogJobId != null) {
                            Text(
                                "job " + state.repo.ciLogJobId + " 的日志：",
                                fontSize = 12.sp,
                                color = SubInk,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            when {
                                state.repo.ciLogBusy -> Text(
                                    "正在拉日志…",
                                    fontSize = 12.5.sp,
                                    color = SubInk,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                                state.repo.ciLogText == null -> Text(
                                    state.repo.ciLogNote ?: "没有日志",
                                    fontSize = 12.sp,
                                    color = WarnAmber,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                                else -> {
                                    state.repo.ciLogNote?.let { note ->
                                        Text(note, fontSize = 11.sp, color = SubInk, modifier = Modifier.padding(bottom = 4.dp))
                                    }
                                    Text(
                                        state.repo.ciLogText ?: "",
                                        fontSize = 10.5.sp,
                                        color = Ink,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                    )
                                }
                            }
                            LRow("收起日志", chevron = true) { state.repo.closeJobLog() }
                        }
                        LRow("收起 jobs", chevron = true) { state.repo.closeRunJobs() }
                    }
                }
                LRow("退出浏览", chevron = true) { state.repo.exitBrowse() }
            }
        }

        // 文件预览 + 改码卡：原文，二进制/截断/超限都明说；编辑提交走 sha 对账
        if (state.repo.fileViewPath.isNotEmpty()) {
            HCard {
                CardTitle("文件：" + state.repo.fileViewPath)
                when {
                    state.repo.fileViewBusy -> Text(
                        "正在拉文件…",
                        fontSize = 13.sp,
                        color = SubInk,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    state.repo.fileViewText == null -> Text(
                        state.repo.fileViewNote ?: "没有内容可给",
                        fontSize = 12.5.sp,
                        color = WarnAmber,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    else -> {
                        state.repo.fileViewNote?.let { note ->
                            Text(note, fontSize = 11.5.sp, color = SubInk, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        if (!state.repo.editingOpen) {
                            Text(
                                state.repo.fileViewText ?: "",
                                fontSize = 11.sp,
                                color = Ink,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            )
                            val editable = state.repo.fileViewSha != null &&
                                !state.repo.fileViewTooBig &&
                                !state.repo.fileViewTruncated
                            if (editable) {
                                LRow("编辑这个文件", chevron = true) { state.repo.startEditing() }
                            } else {
                                Text(
                                    "这份不给在 App 里改（超限/截断/无 sha 账）：去电脑上改",
                                    fontSize = 11.5.sp,
                                    color = SubInk,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        } else {
                            Text(
                                "编辑中（提交后不可撤回，想清楚再发）",
                                fontSize = 11.5.sp,
                                color = WarnAmber,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Bg)
                                    .padding(10.dp),
                            ) {
                                BasicTextField(
                                    value = state.repo.editingText,
                                    onValueChange = { state.repo.editingText = it },
                                    textStyle = TextStyle(
                                        fontSize = 11.sp,
                                        color = Ink,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Bg)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                if (state.repo.editingMessage.isEmpty()) {
                                    Text("commit message：改了什么（必填）", fontSize = 13.sp, color = SubInk)
                                }
                                BasicTextField(
                                    value = state.repo.editingMessage,
                                    onValueChange = { state.repo.editingMessage = it },
                                    textStyle = TextStyle(fontSize = 13.sp, color = Ink),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            state.repo.editNote?.let { note ->
                                Spacer(Modifier.height(6.dp))
                                Text(note, fontSize = 12.sp, color = WarnAmber)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (state.repo.editBusy) SubInk else Accent)
                                        .clickable(enabled = !state.repo.editBusy) { state.repo.commitEdit() }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        if (state.repo.editBusy) "提交中…" else "提交改动",
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Bg)
                                        .clickable(enabled = !state.repo.editBusy) { state.repo.cancelEditing() }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text("放弃", fontSize = 13.sp, color = Ink)
                                }
                            }
                        }
                    }
                }
                LRow("收起", chevron = true) { state.repo.closeFileView() }
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
