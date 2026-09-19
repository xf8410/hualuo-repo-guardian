package com.hualuo.repotool.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.engine.github.GitHubBranch
import com.hualuo.engine.github.GitHubCommitSummary
import com.hualuo.engine.github.GitHubEntry
import com.hualuo.engine.github.GitHubJob
import com.hualuo.engine.github.GitHubRepoClient
import com.hualuo.engine.github.GitHubRepoSummary
import com.hualuo.engine.github.GitHubRun
import com.hualuo.engine.github.normalizeGitHubRepo

/**
 * 仓库工作台的状态舱（清单 / 浏览 / 分支切换 / 提交历史 / 文件预览与改码提交 /
 * CI 深看三层）。
 *
 * 为什么单独一个类：文件行数红线 999 行（run 35236425632 逮的）——这一块在 AppUiState
 * 里长到 1220 行的元凶。拆出来正好：它自成一个内聚的状态域，只依赖两样外界——
 * 读令牌（设置里的 github.token）和一台 toast 喇叭；其余全部自持。
 * AppUiState 以 `val repo` 持有它；RepoScreen 改从 state.repo.* 读。
 *
 * 家规不变：失败出声不冒充（成功/失败都写 note，绝不静默）；后台线程自己起（大会计 IO
 * 不进主线程）；令牌只进请求头；改码靠 sha 对账，409 冲突原话出声让人重开重改，草稿不丢。
 */
class RepoWorkbenchState(
    private val loadToken: () -> String?,
    private val toast: (String) -> Unit,
) {

    private val repoClient = GitHubRepoClient()

    private fun githubToken(): String? = loadToken()

    // ── 我的仓库清单（含私有，要令牌） ─────────────────────────────────────

    /** 我的仓库清单是否在拉。 */
    var myReposBusy by mutableStateOf(false)
        private set

    /** 我的仓库清单（真数据）。 */
    var myRepos by mutableStateOf(emptyList<GitHubRepoSummary>())
        private set

    /** 清单的一句话收场（成功报条数，失败给理由）；null = 还没拉过。 */
    var myReposNote by mutableStateOf<String?>(null)
        private set

    /** 拉「我的仓库」（含私有，要令牌）。没令牌不出声拉网，一句话指路去设置。 */
    fun refreshMyRepos() {
        if (myReposBusy) return
        val token = githubToken()
        if (token.isNullOrEmpty()) {
            myReposNote = "看自己的仓库要令牌：去设置「GitHub 工作台」填（看别人的不用）"
            return
        }
        myReposBusy = true
        Thread({
            val list = runCatching { repoClient.listMyRepos(token) }.getOrElse {
                myReposBusy = false
                myReposNote = "拉不动 GitHub（${it.message ?: "出错了"}）"
                return@Thread
            }
            myReposBusy = false
            myRepos = list.repos
            myReposNote = when {
                list.error != null -> list.error
                list.badEntries > 0 -> "拉到 ${list.repos.size} 个仓库；另有 ${list.badEntries} 条读不懂已跳过"
                else -> "拉到 ${list.repos.size} 个仓库"
            }
        }, "hualuo-repos").start()
    }

    /** 进页才拉；已有数据或正在拉就不重复。 */
    fun refreshMyReposIfStale() {
        if (myRepos.isEmpty() && !myReposBusy) refreshMyRepos()
    }

    // ── 浏览（contents 逐级目录树） ────────────────────────────────────────

    /** 正在浏览的仓（owner/name）；空串 = 没在浏览。 */
    var browseRepo by mutableStateOf("")
        private set

    /** 浏览用的分支；null = 仓库默认分支。 */
    var browseRef by mutableStateOf<String?>(null)
        private set

    /** 当前目录路径（空 = 根）。 */
    var browsePath by mutableStateOf("")
        private set

    /** 回退栈：进目录前把原路径压进来，「返回上一级」弹出。 */
    var browseTrail by mutableStateOf(emptyList<String>())
        private set

    /** 当前目录条目（真数据，目录在前）。 */
    var browseEntries by mutableStateOf(emptyList<GitHubEntry>())
        private set

    /** 浏览忙灯。 */
    var browseBusy by mutableStateOf(false)
        private set

    /** 浏览的一句话收场；null = 没话。 */
    var browseNote by mutableStateOf<String?>(null)
        private set

    /** 「看别人的仓」输入框的词（不落盘：这是次导航动作，不是设置）。 */
    var otherRepoQuery by mutableStateOf("")

    /** 进一个仓库，从根开始浏览（分支/CI 账本一并清干净：换仓不沾上一个仓的账）。 */
    fun browseInto(repo: String) {
        if (browseBusy) return
        val target = normalizeGitHubRepo(repo) ?: run {
            toast("仓库写法不对：要 owner/name（粘整条链接也认）")
            return
        }
        closeFileView()
        closeBrowseCi()
        browseRepo = target
        browseRef = null
        browsePath = ""
        browseTrail = emptyList()
        browseEntries = emptyList()
        branchPickerOpen = false
        branchList = emptyList()
        branchListNote = null
        commitsOpen = false
        commits = emptyList()
        commitsNote = null
        ciRunsList = emptyList()
        ciRunsNote = null
        loadBrowse()
    }

    /** 点目录条目：压栈进目录。文件条目不吃这套（预览走 openBrowseFile）。 */
    fun browseDown(entry: GitHubEntry) {
        if (browseBusy || !entry.isDir) return
        browseTrail = browseTrail + browsePath
        browsePath = entry.path
        loadBrowse()
    }

    /** 返回上一级；已在根就没有上一级。 */
    fun browseUp() {
        if (browseBusy) return
        val prev = browseTrail.lastOrNull() ?: return
        browseTrail = browseTrail.dropLast(1)
        browsePath = prev
        loadBrowse()
    }

    /** 退出浏览（清单还在，重进不用重拉）。 */
    fun exitBrowse() {
        if (browseBusy) return
        closeFileView()
        closeBrowseCi()
        browseRepo = ""
        browseRef = null
        browsePath = ""
        browseTrail = emptyList()
        browseEntries = emptyList()
        browseNote = null
        branchPickerOpen = false
        commitsOpen = false
    }

    /** 看别人的仓：输入框里的 owner/name（粘整条链接也认），过了写法闸就进浏览。 */
    fun browseOtherRepo() {
        if (browseBusy) return
        browseInto(otherRepoQuery)
    }

    private fun loadBrowse() {
        browseBusy = true
        val repo = browseRepo
        val path = browsePath
        val ref = browseRef
        val token = githubToken()
        Thread({
            val result = runCatching { repoClient.browse(repo, path, ref, token) }.getOrElse {
                browseBusy = false
                browseNote = "拉不动 GitHub（${it.message ?: "出错了"}）"
                return@Thread
            }
            browseBusy = false
            browseEntries = result.entries
            browseNote = when {
                result.error != null -> result.error
                result.badEntries > 0 -> "有 ${result.badEntries} 条读不懂已跳过"
                else -> null
            }
        }, "hualuo-browse").start()
    }

    // ── 分支切换（浏览必配：切分支 = 换一棵树，路径回根重新走） ─────────────

    /** 分支清单弹开没有。 */
    var branchPickerOpen by mutableStateOf(false)
        private set

    /** 分支清单是否在拉。 */
    var branchListBusy by mutableStateOf(false)
        private set

    /** 分支清单（真数据，按名排序）。 */
    var branchList by mutableStateOf(emptyList<GitHubBranch>())
        private set

    /** 分支清单的一句话收场。 */
    var branchListNote by mutableStateOf<String?>(null)
        private set

    /** 开/合分支清单；开的时候清单是空的就顺手拉一次。 */
    fun toggleBranchPicker() {
        if (branchPickerOpen) {
            branchPickerOpen = false
            return
        }
        branchPickerOpen = true
        if (branchList.isEmpty() && !branchListBusy) loadBranches()
    }

    private fun loadBranches() {
        val repo = browseRepo
        if (repo.isEmpty()) {
            branchListNote = "没在浏览仓库，分支清单没得拉"
            return
        }
        branchListBusy = true
        val token = githubToken()
        Thread({
            val result = runCatching { repoClient.listBranches(repo, token) }.getOrElse {
                branchListBusy = false
                branchListNote = "拉不动 GitHub（${it.message ?: "出错了"}）"
                return@Thread
            }
            branchListBusy = false
            branchList = result.branches
            branchListNote = when {
                result.error != null -> result.error
                result.badEntries > 0 -> "有 ${result.badEntries} 条读不懂已跳过"
                else -> null
            }
        }, "hualuo-branches").start()
    }

    /** 切分支：换 ref、路径回根（不同分支的路径没有可比性，别装聪明）、目录与历史重拉。 */
    fun switchBranch(name: String) {
        if (browseBusy || browseRepo.isEmpty()) return
        branchPickerOpen = false
        closeFileView()
        if (browseRef == name) return
        browseRef = name
        browsePath = ""
        browseTrail = emptyList()
        browseEntries = emptyList()
        loadBrowse()
        if (commitsOpen) loadCommits()
    }

    // ── 提交历史（维护记录：这个仓当前分支最近干了什么） ────────────────────

    /** 历史卡开没有。 */
    var commitsOpen by mutableStateOf(false)
        private set

    /** 历史是否在拉。 */
    var commitsBusy by mutableStateOf(false)
        private set

    /** 提交历史（真数据，新在前）。 */
    var commits by mutableStateOf(emptyList<GitHubCommitSummary>())
        private set

    /** 历史的一句话收场。 */
    var commitsNote by mutableStateOf<String?>(null)
        private set

    /** 开/合提交历史；开的时候是空的就顺手拉一次。 */
    fun toggleCommits() {
        if (commitsOpen) {
            commitsOpen = false
            return
        }
        commitsOpen = true
        if (commits.isEmpty() && !commitsBusy) loadCommits()
    }

    private fun loadCommits() {
        val repo = browseRepo
        if (repo.isEmpty()) return
        commitsBusy = true
        val ref = browseRef
        val token = githubToken()
        Thread({
            val result = runCatching { repoClient.listCommits(repo, ref, null, token) }.getOrElse {
                commitsBusy = false
                commitsNote = "拉不动 GitHub（${it.message ?: "出错了"}）"
                return@Thread
            }
            commitsBusy = false
            commits = result.commits
            commitsNote = when {
                result.error != null -> result.error
                result.badEntries > 0 -> "有 ${result.badEntries} 条读不懂已跳过"
                else -> null
            }
        }, "hualuo-commits").start()
    }

    /** 手动刷新提交历史（提交完改动后也会自动刷一次）。 */
    fun refreshCommits() {
        if (!commitsBusy && browseRepo.isNotEmpty()) loadCommits()
    }

    // ── 文件预览（内容 + sha 账） ───────────────────────────────────────────

    /** 预览中的文件路径；空 = 没开预览。 */
    var fileViewPath by mutableStateOf("")
        private set

    /** 文件原文（JSON 档解出来的，有界；null = 没内容，理由在 note）。 */
    var fileViewText by mutableStateOf<String?>(null)
        private set

    /** 预览的一句话收场（字符数/截断/二进制/失败理由）。 */
    var fileViewNote by mutableStateOf<String?>(null)
        private set

    /** 预览忙灯。 */
    var fileViewBusy by mutableStateOf(false)
        private set

    /** 预览文件的当前 blob sha（改码提交的对账凭据）；null = 这份内容不许改。 */
    var fileViewSha by mutableStateOf<String?>(null)
        private set

    /** 预览文件是否超限（超限只给前一段预览，改码在界面拦）。 */
    var fileViewTooBig by mutableStateOf(false)
        private set

    /** 预览内容是否被有界读截断（截断的编辑会丢尾巴，改码在界面拦）。 */
    var fileViewTruncated by mutableStateOf(false)
        private set

    /** 点文件条目：拉内容预览（内容 + sha + 超限账一起回；二进制与截断都明说）。 */
    fun openBrowseFile(entry: GitHubEntry) {
        if (browseBusy || fileViewBusy || entry.isDir) return
        val repo = browseRepo
        if (repo.isEmpty()) return
        fileViewBusy = true
        fileViewPath = entry.path
        fileViewText = null
        fileViewNote = null
        fileViewSha = null
        fileViewTooBig = false
        fileViewTruncated = false
        val ref = browseRef
        val token = githubToken()
        Thread({
            val result = runCatching { repoClient.readFile(repo, entry.path, ref, token) }.getOrElse {
                fileViewBusy = false
                fileViewNote = "拉不动 GitHub（${it.message ?: "出错了"}）"
                return@Thread
            }
            fileViewBusy = false
            fileViewText = result.text
            fileViewSha = result.sha
            fileViewTooBig = result.tooBig
            fileViewTruncated = result.truncated
            fileViewNote = when {
                result.error != null -> result.error
                result.tooBig && result.truncated -> "超限文件：只读了前 ${result.charCount} 字符（不给在 App 里改）"
                result.truncated -> "只读了前 ${result.charCount} 字符（文件太大，有界读封顶）"
                else -> "${result.charCount} 字符"
            }
        }, "hualuo-fileview").start()
    }

    /** 收起文件预览（编辑中的草稿一并作废：预览都没了编辑无处落脚）。 */
    fun closeFileView() {
        fileViewPath = ""
        fileViewText = null
        fileViewNote = null
        fileViewSha = null
        fileViewTooBig = false
        fileViewTruncated = false
        fileViewBusy = false
        editingOpen = false
        editingText = ""
        editingMessage = ""
        editNote = null
    }

    // ── 改码提交（PUT contents；旧 sha 对账，409 冲突出声让人重开重改） ─────

    /** 编辑器开没有。 */
    var editingOpen by mutableStateOf(false)
        private set

    /** 编辑中的内容（从预览灌进来，改的是这份草稿）。 */
    var editingText by mutableStateOf("")

    /** commit message（必填：提交不许没有一句人话说明）。 */
    var editingMessage by mutableStateOf("")

    /** 提交忙灯。 */
    var editBusy by mutableStateOf(false)
        private set

    /** 提交的一句话收场（成功在 toast，失败留在这行给编辑器上方摆着）。 */
    var editNote by mutableStateOf<String?>(null)
        private set

    /** 进编辑器：闸门三道（有内容、没截断/超限、有 sha 账），缺哪道指名道姓出声。 */
    fun startEditing() {
        if (editBusy) return
        val text = fileViewText
        if (text == null) {
            toast("没有可编辑的内容：先把文件打开")
            return
        }
        if (fileViewTooBig || fileViewTruncated) {
            toast("这份只读了前一段（超限或截断），编辑会丢尾巴：不让在 App 里改")
            return
        }
        if (fileViewSha == null) {
            toast("这份内容没有 sha 账（二进制或异常）：不让在 App 里改")
            return
        }
        editingText = text
        editingMessage = ""
        editNote = null
        editingOpen = true
    }

    /** 放弃编辑（草稿直接扔，不留尸）。 */
    fun cancelEditing() {
        if (editBusy) return
        editingOpen = false
        editingText = ""
        editingMessage = ""
        editNote = null
    }

    /**
     * 提交改动：闸门（编辑器开着、挂在打开的文件上、message 非空、令牌在手）全过才发；
     * 成功后自动刷新文件预览与提交历史；409 冲突的原话留在编辑器上方，草稿不丢。
     */
    fun commitEdit() {
        if (editBusy || !editingOpen) return
        val repo = browseRepo
        val path = fileViewPath
        val sha = fileViewSha
        val branch = browseRef
        val content = editingText
        val message = editingMessage.trim()
        if (repo.isEmpty() || path.isEmpty()) {
            toast("编辑没挂在一个打开的文件上：重新打开再试")
            return
        }
        if (message.isEmpty()) {
            toast("commit message 不能空：写一句人话说明改了什么")
            return
        }
        val token = githubToken()
        if (token.isNullOrEmpty()) {
            toast("改码要令牌：去设置「GitHub 工作台」填")
            return
        }
        editBusy = true
        editNote = null
        Thread({
            val written = runCatching { repoClient.updateFile(repo, path, branch, content, message, sha, token) }.getOrElse {
                editBusy = false
                editNote = "提交没发出去（${it.message ?: "出错"}）"
                return@Thread
            }
            editBusy = false
            if (written.error != null) {
                // 冲突/失败的账留给编辑器上方，草稿不丢——人改完还能再提交
                editNote = written.error
                return@Thread
            }
            editingOpen = false
            editingText = ""
            editingMessage = ""
            editNote = null
            toast("已提交（${written.commitSha?.take(7) ?: "?"}）：文件预览与提交历史正在刷新")
            refreshFileViewAfterCommit()
            refreshCommits()
        }, "hualuo-edit").start()
    }

    /** 提交成功后重拉当前文件预览（sha 也换成新的：连续改同一份文件不打架）。 */
    private fun refreshFileViewAfterCommit() {
        val path = fileViewPath
        if (path.isEmpty()) return
        openBrowseFile(GitHubEntry(path.substringAfterLast('/'), path, false, 0L))
    }

    // ── CI 深看（浏览仓的 runs、jobs、日志三层各说各话） ────────────────────

    /** CI 卡开没有。 */
    var browseCiOpen by mutableStateOf(false)
        private set

    /** runs 是否在拉。 */
    var ciRunsBusy by mutableStateOf(false)
        private set

    /** 最近 workflow runs（真数据）。 */
    var ciRunsList by mutableStateOf(emptyList<GitHubRun>())
        private set

    /** runs 的一句话收场。 */
    var ciRunsNote by mutableStateOf<String?>(null)
        private set

    /** 正在看 jobs 的 run id；null = 没展开。 */
    var ciJobsRunId by mutableStateOf<Long?>(null)
        private set

    /** jobs 是否在拉。 */
    var ciJobsBusy by mutableStateOf(false)
        private set

    /** 展开中 run 的 jobs（真数据）。 */
    var ciJobsList by mutableStateOf(emptyList<GitHubJob>())
        private set

    /** jobs 的一句话收场。 */
    var ciJobsNote by mutableStateOf<String?>(null)
        private set

    /** 正在看日志的 job id；null = 没展开。 */
    var ciLogJobId by mutableStateOf<Long?>(null)
        private set

    /** 日志是否在拉。 */
    var ciLogBusy by mutableStateOf(false)
        private set

    /** job 日志（有界读；null = 没内容，理由在 note）。 */
    var ciLogText by mutableStateOf<String?>(null)
        private set

    /** 日志的一句话收场（字符数/截断/失败理由）。 */
    var ciLogNote by mutableStateOf<String?>(null)
        private set

    /** 开/合 CI 卡；开的时候 runs 是空的就顺手拉一次。 */
    fun toggleBrowseCi() {
        if (browseCiOpen) {
            closeBrowseCi()
            return
        }
        browseCiOpen = true
        if (ciRunsList.isEmpty() && !ciRunsBusy) loadBrowseCi()
    }

    /** 合上 CI 卡：三层全收（换仓/退出不留展开的旧账）。 */
    fun closeBrowseCi() {
        browseCiOpen = false
        ciJobsRunId = null
        ciJobsList = emptyList()
        ciJobsNote = null
        ciLogJobId = null
        ciLogText = null
        ciLogNote = null
    }

    private fun loadBrowseCi() {
        val repo = browseRepo
        if (repo.isEmpty()) return
        ciRunsBusy = true
        val token = githubToken()
        Thread({
            val result = runCatching { repoClient.runs(repo, token) }.getOrElse {
                ciRunsBusy = false
                ciRunsNote = "拉不动 GitHub（${it.message ?: "出错了"}）"
                return@Thread
            }
            ciRunsBusy = false
            ciRunsList = result.runs
            ciRunsNote = when {
                result.error != null -> result.error
                result.badEntries > 0 -> "有 ${result.badEntries} 条读不懂已跳过"
                else -> null
            }
        }, "hualuo-ci-deep").start()
    }

    /** 手动刷新 runs（三层里下两层不合：只想刷新最外层账）。 */
    fun refreshBrowseCi() {
        if (!ciRunsBusy && browseRepo.isNotEmpty()) loadBrowseCi()
    }

    /**
     * 展开一个 run 看 jobs。闸门在改账之前：没挂在浏览仓上就什么都不动——
     * 先展开后撞闸会留一截悬空的展开层（run 35282696138 的测试抓的就是这个次序）。
     */
    fun openRunJobs(runId: Long) {
        if (ciJobsBusy || ciLogBusy) return
        val repo = browseRepo
        if (repo.isEmpty()) return
        ciJobsRunId = runId
        ciJobsList = emptyList()
        ciJobsNote = null
        ciJobsBusy = true
        val token = githubToken()
        Thread({
            val result = runCatching { repoClient.runJobs(repo, runId, token) }.getOrElse {
                ciJobsBusy = false
                ciJobsNote = "拉不动 GitHub（${it.message ?: "出错了"}）"
                return@Thread
            }
            ciJobsBusy = false
            ciJobsList = result.jobs
            ciJobsNote = when {
                result.error != null -> result.error
                result.badEntries > 0 -> "有 ${result.badEntries} 条读不懂已跳过"
                else -> null
            }
        }, "hualuo-ci-jobs").start()
    }

    /** 收起 jobs 层（日志层一并收：父层合了子层无处挂）。 */
    fun closeRunJobs() {
        if (ciJobsBusy || ciLogBusy) return
        ciJobsRunId = null
        ciJobsList = emptyList()
        ciJobsNote = null
        ciLogJobId = null
        ciLogText = null
        ciLogNote = null
    }

    /** 展开一个 job 看日志（闸门在改账之前，同 openRunJobs；有界读：超长给前一段并明说）。 */
    fun openJobLog(jobId: Long) {
        if (ciLogBusy) return
        val repo = browseRepo
        if (repo.isEmpty()) return
        ciLogJobId = jobId
        ciLogText = null
        ciLogNote = null
        ciLogBusy = true
        val token = githubToken()
        Thread({
            val result = runCatching { repoClient.jobLog(repo, jobId, token) }.getOrElse {
                ciLogBusy = false
                ciLogNote = "拉不动 GitHub（${it.message ?: "出错了"}）"
                return@Thread
            }
            ciLogBusy = false
            ciLogText = result.text
            ciLogNote = when {
                result.error != null -> result.error
                result.truncated -> "只读了前 ${result.charCount} 字符（日志太长，有界读封顶；全量去 CI 产物里拿）"
                else -> "${result.charCount} 字符"
            }
        }, "hualuo-ci-log").start()
    }

    /** 收起日志层。 */
    fun closeJobLog() {
        if (ciLogBusy) return
        ciLogJobId = null
        ciLogText = null
        ciLogNote = null
    }
}
