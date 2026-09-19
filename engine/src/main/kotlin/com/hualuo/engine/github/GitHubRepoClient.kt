package com.hualuo.engine.github

import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** 一个仓库的界面字段（就展示这些）。 */
data class GitHubRepoSummary(
    val fullName: String,
    val isPrivate: Boolean,
    val description: String,
    val defaultBranch: String,
    val updatedAt: String,
    val sizeKb: Long,
)

/** 仓库清单回执：error 非 null 时 repos 必为空；读不动的条目在 badEntries 里数出来。 */
data class GitHubRepoList(val repos: List<GitHubRepoSummary>, val badEntries: Int, val error: String?)

/** 浏览目录的一条：目录在前排序由 client 做。 */
data class GitHubEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val sizeBytes: Long,
)

/** 浏览回执：error 非 null 时 entries 必为空。 */
data class GitHubBrowse(val entries: List<GitHubEntry>, val badEntries: Int, val error: String?)

/**
 * 读一个文件的回执：text 为 null 就是没有内容可给（二进制/太大/出错），理由在 error；
 * truncated = 内容被有界读截断（只读了前一段）；sha = 文件当前 blob sha（改码提交要用，
 * null = 这份内容不许改）；tooBig = 超过 contents 接口单文件上限（只给预览不给改）。
 */
data class GitHubFileContent(
    val path: String,
    val text: String?,
    val charCount: Int,
    val truncated: Boolean,
    val sha: String?,
    val tooBig: Boolean,
    val error: String?,
)

/** 一个分支：名字 + 头指针 sha（界面切换分支用）。 */
data class GitHubBranch(val name: String, val commitSha: String)

/** 分支清单回执。 */
data class GitHubBranchList(val branches: List<GitHubBranch>, val badEntries: Int, val error: String?)

/** 一条提交的界面字段：sha + 首行消息 + 作者 + 日期（维护记录看这些就够）。 */
data class GitHubCommitSummary(
    val sha: String,
    val messageFirstLine: String,
    val author: String,
    val date: String,
)

/** 提交历史回执。 */
data class GitHubCommitList(val commits: List<GitHubCommitSummary>, val badEntries: Int, val error: String?)

/** 改码提交的回执：成功带新内容 sha 与提交 sha；失败 error 带人话（绝不静默）。 */
data class GitHubCommitWritten(
    val commitSha: String?,
    val contentSha: String?,
    val error: String?,
)

/** 一次 CI job 的界面字段（run 里点开看的就是这些）。 */
data class GitHubJob(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val startedAt: String,
)

/** run 的 jobs 回执。 */
data class GitHubJobList(val jobs: List<GitHubJob>, val badEntries: Int, val error: String?)

/** 一段 job 日志：有界读（512K 封顶），truncated 明说——日志再长也不许整段吞内存。 */
data class GitHubJobLog(
    val text: String?,
    val charCount: Int,
    val truncated: Boolean,
    val error: String?,
)

/**
 * 仓库工作台客户端（纯 JVM；fetch/putJson 缝隙注入，JVM 测试不碰网络）。
 *
 *  - 清单：自己的仓走 /user/repos（要令牌才看得到私有），别人的公开仓走 /users/{u}/repos；
 *  - 浏览：contents API 逐目录列（路径逐段编码，空格不会变加号）；
 *  - 读文件：先走 JSON 档（拿 sha 与是否超限），没超限再把 base64 解成原文——一次请求
 *    同时拿到「内容 + 改码要用的 sha」；超限（encoding=none）退回 raw 档只给前一段预览；
 *  - 分支/提交历史：branches 与 commits 接口（维护记录与分支切换的账本）；
 *  - 改码提交：PUT contents（message 必填、旧 sha 对账防覆盖别人），409/422 = 有并发改动，
 *    必须原话出声让人重开重改，绝不硬盖；
 *  - CI 深看：任意仓的 runs（解析与 CI 客户端共用一份）/ run 的 jobs / job 日志（有界读）；
 *  - 令牌只进请求头；失败一律人话（状态码，绝无令牌），绝不拿半份清单冒充成功。
 */
class GitHubRepoClient(
    private val fetch: (String, String?, String?, Int) -> GitHubHttpResult = ::githubHttpGet,
    private val putJson: (String, String?, String) -> GitHubHttpResult = ::githubHttpPutJson,
) {

    /** 自己的仓库（含私有）。令牌必填——没令牌就不发请求，先出声。 */
    fun listMyRepos(token: String?, limit: Int = 30): GitHubRepoList {
        if (token.isNullOrBlank()) {
            return GitHubRepoList(emptyList(), 0, "看自己的仓库要令牌：去设置「GitHub 工作台」填（看别人的不用）")
        }
        val result = fetch(
            "$GITHUB_API_ROOT/user/repos?per_page=${limit.coerceIn(1, 100)}&sort=updated&affiliation=owner",
            token,
            null,
            GITHUB_MAX_BODY_CHARS,
        )
        if (result.status != 200) return GitHubRepoList(emptyList(), 0, httpIssue(result))
        return parseRepoList(result)
    }

    /** 别人的公开仓库（令牌可空）。用户名只认一段。 */
    fun listUserRepos(owner: String, token: String?, limit: Int = 30): GitHubRepoList {
        val clean = owner.trim().trimEnd('/')
        if (clean.isEmpty() || clean.any { it == '/' || it == '?' || it == '#' || it.isWhitespace() }) {
            return GitHubRepoList(emptyList(), 0, "用户名写法不对：只要一段（如 xf8410）")
        }
        val result = fetch(
            "$GITHUB_API_ROOT/users/$clean/repos?per_page=${limit.coerceIn(1, 100)}&sort=updated",
            token,
            null,
            GITHUB_MAX_BODY_CHARS,
        )
        if (result.status == 404) return GitHubRepoList(emptyList(), 0, "GitHub 说没这个用户（404）：核对一下用户名")
        if (result.status != 200) return GitHubRepoList(emptyList(), 0, httpIssue(result))
        return parseRepoList(result)
    }

    /** 浏览一个目录：[path] 空串 = 仓库根。目录在前、按名排序。 */
    fun browse(repo: String, path: String, ref: String?, token: String?): GitHubBrowse {
        val full = normalizeGitHubRepo(repo)
            ?: return GitHubBrowse(emptyList(), 0, "仓库写法不对：要 owner/name（现在是「$repo」）")
        val encoded = encodeContentsPath(path)
        val refQuery = if (ref.isNullOrBlank()) "" else "?ref=" + encodeSegment(ref)
        val url = "$GITHUB_API_ROOT/repos/$full/contents" + (if (encoded.isEmpty()) "" else "/$encoded") + refQuery
        val result = fetch(url, token, null, GITHUB_MAX_BODY_CHARS)
        if (result.status == 404) {
            return GitHubBrowse(emptyList(), 0, "GitHub 说没这个目录（404）：路径或分支不对")
        }
        if (result.status != 200) return GitHubBrowse(emptyList(), 0, httpIssue(result))
        val array = runCatching { json.parseToJsonElement(result.body) }.getOrNull() as? JsonArray
            ?: return GitHubBrowse(emptyList(), 0, "GitHub 回的内容读不懂（200 但不是目录清单）")
        val entries = ArrayList<GitHubEntry>()
        var bad = 0
        for (element in array) {
            if (element !is JsonObject) { bad += 1; continue }
            val name = (element["name"] as? JsonPrimitive)?.contentOrNull
            val entryPath = (element["path"] as? JsonPrimitive)?.contentOrNull
            val type = (element["type"] as? JsonPrimitive)?.contentOrNull
            val size = (element["size"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
            if (name.isNullOrEmpty() || entryPath.isNullOrEmpty() || (type != "file" && type != "dir")) {
                bad += 1
                continue
            }
            entries += GitHubEntry(name, entryPath, type == "dir", size)
        }
        val sorted = entries.sortedWith(compareByDescending<GitHubEntry> { it.isDir }.thenBy { it.name.lowercase() })
        return GitHubBrowse(sorted, bad, null)
    }

    /** contents 单文件接口的原文上限：1MB，超过就只给 encoding=none（预览走 raw 档）。 */
    private val contentsFileLimitBytes = 1_000_000L

    /**
     * 读一个文件：JSON 档一次拿「内容 + sha + 是否超限」。没超限把 base64 解成原文；
     * 超限退回 raw 档只给前一段预览（sha 照给，但 tooBig = true，改码闸在界面拦）。
     */
    fun readFile(repo: String, path: String, ref: String?, token: String?): GitHubFileContent {
        val full = normalizeGitHubRepo(repo)
            ?: return GitHubFileContent(path, null, 0, false, null, false, "仓库写法不对：要 owner/name（现在是「$repo」）")
        val cleanPath = path.trim().trim('/')
        if (cleanPath.isEmpty()) return GitHubFileContent(path, null, 0, false, null, false, "文件路径是空的")
        val encoded = encodeContentsPath(cleanPath)
        val refQuery = if (ref.isNullOrBlank()) "" else "?ref=" + encodeSegment(ref)
        val url = "$GITHUB_API_ROOT/repos/$full/contents/$encoded$refQuery"
        // 内容上限 1MB，base64 后约 1.37M 字符，封顶放宽到 1.6M 字符（有界读纪律不破，只是放宽）
        val result = fetch(url, token, null, 1_600_000)
        if (result.status == 404) {
            return GitHubFileContent(cleanPath, null, 0, false, null, false, "GitHub 说没这个文件（404）：路径或分支不对")
        }
        if (result.status != 200) {
            return GitHubFileContent(cleanPath, null, 0, false, null, false, httpIssue(result))
        }
        val obj = runCatching { json.parseToJsonElement(result.body) }.getOrNull() as? JsonObject
            ?: return GitHubFileContent(cleanPath, null, 0, false, null, false, "GitHub 回的内容读不懂（200 但不是文件账）")
        val sha = (obj["sha"] as? JsonPrimitive)?.contentOrNull
        val size = (obj["size"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
        val encoding = (obj["encoding"] as? JsonPrimitive)?.contentOrNull
        val encodedContent = (obj["content"] as? JsonPrimitive)?.contentOrNull ?: ""
        if (encoding == "none" || size > contentsFileLimitBytes) {
            // 超限：raw 档只给前一段预览。sha 照带（B 段如果做整文件替换也认账），改码闸在界面拦
            val raw = fetch(url, token, GITHUB_ACCEPT_RAW, GITHUB_MAX_BODY_CHARS)
            if (raw.status != 200) {
                return GitHubFileContent(cleanPath, null, 0, false, sha, true, httpIssue(raw))
            }
            if (raw.body.contains('\u0000')) {
                return GitHubFileContent(cleanPath, null, 0, raw.truncated, sha, true, "这是二进制文件：App 里不给内容预览")
            }
            return GitHubFileContent(cleanPath, raw.body, raw.body.length, raw.truncated, sha, true, null)
        }
        if (encoding != "base64") {
            return GitHubFileContent(cleanPath, null, 0, false, sha, false, "GitHub 回了认不出的编码（$encoding）：预览不给，免得给你看乱码")
        }
        val decoded = runCatching {
            String(java.util.Base64.getMimeDecoder().decode(encodedContent.replace("\n", "").replace("\r", "")), Charsets.UTF_8)
        }.getOrElse {
            return GitHubFileContent(cleanPath, null, 0, false, sha, false, "文件内容解不出来（base64 账对不上）：${it.message ?: "出错"}")
        }
        if (decoded.contains('\u0000')) {
            return GitHubFileContent(cleanPath, null, 0, false, sha, false, "这是二进制文件：App 里不给内容预览（改码也不收二进制）")
        }
        return GitHubFileContent(cleanPath, decoded, decoded.length, result.truncated, sha, false, null)
    }

    /** 分支清单（分支切换的账本）。 */
    fun listBranches(repo: String, token: String?, limit: Int = 50): GitHubBranchList {
        val full = normalizeGitHubRepo(repo)
            ?: return GitHubBranchList(emptyList(), 0, "仓库写法不对：要 owner/name（现在是「$repo」）")
        val result = fetch("$GITHUB_API_ROOT/repos/$full/branches?per_page=${limit.coerceIn(1, 100)}", token, null, GITHUB_MAX_BODY_CHARS)
        if (result.status == 404) return GitHubBranchList(emptyList(), 0, "GitHub 说没这个仓库（404）：核对仓库名")
        if (result.status != 200) return GitHubBranchList(emptyList(), 0, httpIssue(result))
        val array = runCatching { json.parseToJsonElement(result.body) }.getOrNull() as? JsonArray
            ?: return GitHubBranchList(emptyList(), 0, "GitHub 回的内容读不懂（200 但不是分支清单）")
        val branches = ArrayList<GitHubBranch>()
        var bad = 0
        for (element in array) {
            if (element !is JsonObject) { bad += 1; continue }
            val name = (element["name"] as? JsonPrimitive)?.contentOrNull
            val sha = ((element["commit"] as? JsonObject)?.get("sha") as? JsonPrimitive)?.contentOrNull
            if (name.isNullOrEmpty() || sha.isNullOrEmpty()) { bad += 1; continue }
            branches += GitHubBranch(name, sha)
        }
        val sorted = branches.sortedBy { it.name.lowercase() }
        return GitHubBranchList(sorted, bad, null)
    }

    /** 提交历史（维护记录）：[ref] 空走默认分支；[path] 非空只看这个文件的账。 */
    fun listCommits(repo: String, ref: String?, path: String?, token: String?, limit: Int = 20): GitHubCommitList {
        val full = normalizeGitHubRepo(repo)
            ?: return GitHubCommitList(emptyList(), 0, "仓库写法不对：要 owner/name（现在是「$repo」）")
        val params = ArrayList<String>()
        if (!ref.isNullOrBlank()) params += "sha=" + encodeSegment(ref)
        if (!path.isNullOrBlank()) params += "path=" + encodeContentsPath(path)
        params += "per_page=" + limit.coerceIn(1, 100)
        val url = "$GITHUB_API_ROOT/repos/$full/commits?" + params.joinToString("&")
        val result = fetch(url, token, null, GITHUB_MAX_BODY_CHARS)
        if (result.status == 404) {
            return GitHubCommitList(emptyList(), 0, "GitHub 说没这个仓库或分支（404）：核对一下")
        }
        if (result.status != 200) return GitHubCommitList(emptyList(), 0, httpIssue(result))
        val array = runCatching { json.parseToJsonElement(result.body) }.getOrNull() as? JsonArray
            ?: return GitHubCommitList(emptyList(), 0, "GitHub 回的内容读不懂（200 但不是提交清单）")
        val commits = ArrayList<GitHubCommitSummary>()
        var bad = 0
        for (element in array) {
            if (element !is JsonObject) { bad += 1; continue }
            val sha = (element["sha"] as? JsonPrimitive)?.contentOrNull
            val commit = element["commit"] as? JsonObject
            val message = (commit?.get("message") as? JsonPrimitive)?.contentOrNull
            val author = ((commit?.get("author") as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull
            val date = ((commit?.get("author") as? JsonObject)?.get("date") as? JsonPrimitive)?.contentOrNull
            if (sha.isNullOrEmpty() || message.isNullOrEmpty()) { bad += 1; continue }
            commits += GitHubCommitSummary(
                sha = sha,
                messageFirstLine = message.substringBefore('\n').trim(),
                author = author ?: "",
                date = date ?: "",
            )
        }
        return GitHubCommitList(commits, bad, null)
    }

    /** 任意仓的 workflow runs（CI 深看第一层；解析与 CI 客户端共用一份，防双源坑）。 */
    fun runs(repo: String, token: String?, limit: Int = 10): GitHubCiSnapshot {
        val full = normalizeGitHubRepo(repo)
            ?: return GitHubCiSnapshot(emptyList(), 0, "仓库写法不对：要 owner/name（现在是「$repo」）")
        val result = fetch("$GITHUB_API_ROOT/repos/$full/actions/runs?per_page=${limit.coerceIn(1, 20)}", token, null, GITHUB_MAX_BODY_CHARS)
        if (result.status != 200) return GitHubCiSnapshot(emptyList(), 0, httpIssue(result))
        return parseWorkflowRuns(result.body, limit)
    }

    /** 一次 run 里的 jobs（CI 深看第二层）。 */
    fun runJobs(repo: String, runId: Long, token: String?): GitHubJobList {
        val full = normalizeGitHubRepo(repo)
            ?: return GitHubJobList(emptyList(), 0, "仓库写法不对：要 owner/name（现在是「$repo」）")
        val result = fetch("$GITHUB_API_ROOT/repos/$full/actions/runs/$runId/jobs?per_page=20", token, null, GITHUB_MAX_BODY_CHARS)
        if (result.status == 404) return GitHubJobList(emptyList(), 0, "GitHub 说没这个 run（404）：可能已被清理")
        if (result.status != 200) return GitHubJobList(emptyList(), 0, httpIssue(result))
        val root = runCatching { json.parseToJsonElement(result.body) }.getOrNull() as? JsonObject
            ?: return GitHubJobList(emptyList(), 0, "GitHub 回的内容读不懂（200 但不是 jobs 账）")
        val array = root["jobs"] as? JsonArray
            ?: return GitHubJobList(emptyList(), 0, "GitHub 回的形状变了（没找到 jobs）")
        val jobs = ArrayList<GitHubJob>()
        var bad = 0
        for (element in array) {
            if (element !is JsonObject) { bad += 1; continue }
            val id = (element["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
            val name = (element["name"] as? JsonPrimitive)?.contentOrNull
            val status = (element["status"] as? JsonPrimitive)?.contentOrNull
            if (id == null || name.isNullOrEmpty() || status.isNullOrEmpty()) { bad += 1; continue }
            jobs += GitHubJob(
                id = id,
                name = name,
                status = status,
                conclusion = (element["conclusion"] as? JsonPrimitive)?.contentOrNull,
                startedAt = (element["started_at"] as? JsonPrimitive)?.contentOrNull ?: "",
            )
        }
        return GitHubJobList(jobs, bad, null)
    }

    /**
     * 一段 job 日志（CI 深看第三层）：GitHub 302 到日志文本，GET 跟随重定向直接拿到。
     * 有界读 512K 封顶——超长日志只给前一段并把 truncated 说出来，绝不整段吞内存。
     */
    fun jobLog(repo: String, jobId: Long, token: String?): GitHubJobLog {
        val full = normalizeGitHubRepo(repo)
            ?: return GitHubJobLog(null, 0, false, "仓库写法不对：要 owner/name（现在是「$repo」）")
        val result = fetch("$GITHUB_API_ROOT/repos/$full/actions/jobs/$jobId/logs", token, null, GITHUB_MAX_BODY_CHARS)
        if (result.status == 404) return GitHubJobLog(null, 0, false, "GitHub 说没这段日志（404）：run 太旧或日志已被清")
        if (result.status != 200) return GitHubJobLog(null, 0, false, httpIssue(result))
        if (result.body.isEmpty()) return GitHubJobLog(null, 0, false, "这段日志是空的（job 还没吐字或日志被清了）")
        return GitHubJobLog(result.body, result.body.length, result.truncated, null)
    }

    /**
     * 改码提交：PUT contents 整文件替换。[sha] 是读文件时拿到的 blob sha——
     * GitHub 拿它对账，别人先改过就回 409/422，这里翻译成人话让人重开重改，绝不硬盖。
     * [message] 必填（提交不许没有一句人话说明），[newContent] 只收文本（二进制不进这条通道）。
     */
    fun updateFile(
        repo: String,
        path: String,
        branch: String?,
        newContent: String,
        message: String,
        sha: String?,
        token: String?,
    ): GitHubCommitWritten {
        val full = normalizeGitHubRepo(repo)
            ?: return GitHubCommitWritten(null, null, "仓库写法不对：要 owner/name（现在是「$repo」）")
        val cleanPath = path.trim().trim('/')
        if (cleanPath.isEmpty()) return GitHubCommitWritten(null, null, "文件路径是空的")
        if (newContent.contains('\u0000')) {
            return GitHubCommitWritten(null, null, "改码通道只收文本：这份内容里有二进制字节，不走这条路")
        }
        if (message.isBlank()) {
            return GitHubCommitWritten(null, null, "commit message 不能空：写一句人话说明改了什么")
        }
        if (token.isNullOrBlank()) {
            return GitHubCommitWritten(null, null, "改码要令牌：去设置「GitHub 工作台」填")
        }
        val encoded = encodeContentsPath(cleanPath)
        val body = buildJsonObject {
            put("message", message.trim())
            put("content", java.util.Base64.getEncoder().encodeToString(newContent.toByteArray(Charsets.UTF_8)))
            put("branch", if (branch.isNullOrBlank()) "main" else branch.trim())
            if (!sha.isNullOrBlank()) put("sha", sha)
        }.toString()
        val result = putJson("$GITHUB_API_ROOT/repos/$full/contents/$encoded", token, body)
        if (result.status == 409 || result.status == 422) {
            return GitHubCommitWritten(
                null,
                null,
                "文件在我读到之后被别人改过（GitHub 报 ${result.status}）：重新打开这个文件再改一遍，别硬盖别人的账",
            )
        }
        if (result.status == 404) {
            return GitHubCommitWritten(null, null, "GitHub 说没这个文件或分支（404）：路径或分支不对")
        }
        if (result.status !in 200..299) {
            return GitHubCommitWritten(null, null, httpIssue(result))
        }
        val obj = runCatching { json.parseToJsonElement(result.body) }.getOrNull() as? JsonObject
            ?: return GitHubCommitWritten(null, null, "GitHub 回的内容读不懂（200 但不是提交回执）")
        val commitSha = ((obj["commit"] as? JsonObject)?.get("sha") as? JsonPrimitive)?.contentOrNull
        val contentSha = ((obj["content"] as? JsonObject)?.get("sha") as? JsonPrimitive)?.contentOrNull
        return GitHubCommitWritten(commitSha, contentSha, null)
    }

    private fun parseRepoList(result: GitHubHttpResult): GitHubRepoList {
        val array = runCatching { json.parseToJsonElement(result.body) }.getOrNull() as? JsonArray
            ?: return GitHubRepoList(emptyList(), 0, "GitHub 回的内容读不懂（200 但不是仓库清单）")
        val repos = ArrayList<GitHubRepoSummary>()
        var bad = 0
        for (element in array) {
            if (element !is JsonObject) { bad += 1; continue }
            val fullName = (element["full_name"] as? JsonPrimitive)?.contentOrNull
            if (fullName.isNullOrEmpty()) { bad += 1; continue }
            repos += GitHubRepoSummary(
                fullName = fullName,
                isPrivate = (element["private"] as? JsonPrimitive)?.booleanOrNull ?: false,
                description = (element["description"] as? JsonPrimitive)?.contentOrNull ?: "",
                defaultBranch = (element["default_branch"] as? JsonPrimitive)?.contentOrNull ?: "main",
                updatedAt = (element["updated_at"] as? JsonPrimitive)?.contentOrNull ?: "",
                sizeKb = (element["size"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L,
            )
        }
        return GitHubRepoList(repos, bad, null)
    }

    /** 失败的人话：只说状态码与该干什么，原文不抄。 */
    private fun httpIssue(result: GitHubHttpResult): String = when {
        result.status == 0 -> "连不上 GitHub：${brief(result.body)}"
        result.status == 401 -> "GitHub 不认这把令牌（401）：去设置里核对或换新令牌"
        result.status == 403 -> "GitHub 不给看（403）：私有仓库要令牌，或者令牌权限不够"
        result.status == 404 -> "GitHub 说没这个仓库（404）：去设置里核对仓库名"
        else -> "GitHub 回了 ${result.status}，稍后再试"
    }

    private fun brief(text: String): String {
        val flat = text.replace('\n', ' ').replace('\r', ' ').trim()
        return if (flat.length <= 160) flat else flat.take(160) + "…（已截断）"
    }

    /** contents 路径逐段编码：斜杠保留当分隔符，空格变 %20（不是加号——路径里的 + 是真加号）。 */
    private fun encodeContentsPath(path: String): String =
        path.trim().trim('/').split('/').filter { it.isNotEmpty() }.joinToString("/") { encodeSegment(it) }

    private fun encodeSegment(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
