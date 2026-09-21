package com.hualuo.repotool.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.engine.github.GitHubAuthClient
import com.hualuo.engine.github.GitHubAuthOutcome

/**
 * GitHub 登录状态舱（2026-09-22 补，用户实报「仓库工具页面不太对，token 登录的输入框呢」）。
 *
 * 干什么：拿令牌打一次 GitHub 官方 /user 接口验证（[GitHubAuthClient]），把「已登录为谁、
 * 这枚钥匙有什么权限」记下来；退出登录把这张账清掉。令牌本体不在这里——它住设置键
 * github.token，由登录卡那格编辑；这里只管「验过没有、验出的是谁」。
 *
 * 三条纪律（对齐全仓写法）：
 *  - **没验证过就是未登录**：登录名只由一次成功的验证写入，绝不拿「填了令牌」冒充登录态；
 *  - **令牌一改，登录当场作废**（[invalidate]）：防「屏上显示已登录为旧身份、手里其实是新钥匙」
 *    的撒谎态——这是用户实报这条时点名的关切；
 *  - 失败原因原话直通（401/403 带出路），验证在后台线程跑（大会计 IO 不进主线程的老规矩）。
 *
 * 挂进程不挂界面：状态舱由 AppUiState 持有（应用进程单例），转屏/切出重进不丢登录态。
 */
class GithubLoginState(
    private val persist: UiPersistence,
    private val toast: (String) -> Unit,
    private val bumpRevision: () -> Unit = {},
    private val authClient: GitHubAuthClient = GitHubAuthClient(),
) {

    /** 已登录的 GitHub 登录名；null = 未登录（从没验过，或令牌改了、退出了）。 */
    var account by mutableStateOf(persist.load(UiKeys.GITHUB_LOGIN)?.trim()?.takeIf { it.isNotEmpty() })
        private set

    /** 权限清单文本（X-OAuth-Scopes 响应头逗号连接）；空串 = 没声明或没验过。 */
    var scopesText by mutableStateOf(persist.load(UiKeys.GITHUB_SCOPES).orEmpty())
        private set

    /** 验证是否在跑（登录钮的禁点与文案看它）。 */
    var busy by mutableStateOf(false)
        private set

    /** 最近一次验证或退出的收场话；null = 本进程里还没动过。 */
    var note by mutableStateOf<String?>(null)
        private set

    /** 拿一枚令牌去验证并落账。空令牌在发网之前拦下（出声，不静默）。 */
    fun login(token: String) {
        if (busy) return
        val clean = token.trim()
        if (clean.isEmpty()) {
            note = "令牌是空的：把 GitHub 访问令牌粘进上面那格再点登录"
            return
        }
        busy = true
        note = null
        Thread({
            val outcome = try {
                authClient.verify(clean)
            } catch (e: Exception) {
                GitHubAuthOutcome.Failed("验证没跑完（" + (e.message ?: "内部出错") + "）：稍后再试")
            }
            busy = false
            when (outcome) {
                is GitHubAuthOutcome.Ok -> {
                    account = outcome.identity.login
                    scopesText = outcome.identity.scopes.joinToString(", ")
                    persist.save(UiKeys.GITHUB_LOGIN, outcome.identity.login)
                    persist.save(UiKeys.GITHUB_SCOPES, scopesText)
                    bumpRevision()
                    note = buildString {
                        append("已登录为 ").append(outcome.identity.login)
                        when {
                            !outcome.identity.scopesDeclared ->
                                append("；这枚令牌没带权限清单声明（细粒度令牌常见），能用就行")
                            outcome.identity.scopes.isEmpty() ->
                                append("；权限清单是空的：多半只够读公开内容")
                            else -> append("；权限：").append(scopesText)
                        }
                    }
                    toast("GitHub 登录成功：" + outcome.identity.login)
                }
                is GitHubAuthOutcome.Failed -> {
                    note = outcome.reason
                    toast("GitHub 登录失败：原因见登录卡那行（带出路，照着改就行）")
                }
            }
        }, "hualuo-github-auth").start()
    }

    /** 退出登录：清登录名与权限清单（设置里的令牌不动——删不删用户定）。 */
    fun logout() {
        if (account == null && scopesText.isEmpty()) {
            note = "现在本来就是未登录"
            return
        }
        account = null
        scopesText = ""
        persist.save(UiKeys.GITHUB_LOGIN, "")
        persist.save(UiKeys.GITHUB_SCOPES, "")
        bumpRevision()
        note = "已退出登录（访问令牌还留在上面那格；要清掉就把它删了）"
        toast("已退出 GitHub 登录（令牌没动）")
    }

    /**
     * 令牌改动时作废登录态（由 AppUiState.setText 在 github.token 变更时调）。
     * 幂等：本来就没登录时什么都不做，不刷提示。
     */
    fun invalidate() {
        if (account == null && scopesText.isEmpty()) return
        account = null
        scopesText = ""
        persist.save(UiKeys.GITHUB_LOGIN, "")
        persist.save(UiKeys.GITHUB_SCOPES, "")
        note = "令牌改过了：之前那次验证作废，重新点「登录」验证这把新钥匙"
    }
}
