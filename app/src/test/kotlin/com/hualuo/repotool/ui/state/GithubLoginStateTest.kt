package com.hualuo.repotool.ui.state

import com.hualuo.engine.github.GitHubAuthOutcome
import com.hualuo.engine.github.GitHubHttpResult
import com.hualuo.engine.github.GitHubIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GitHub 登录状态舱的纯 JVM 契约（2026-09-22）：
 *  - 空令牌发网之前拦下；验证成功才记登录名与权限；失败保持未登录；
 *  - 令牌一改（AppUiState.setText 走 github.token）验证当场作废——防「显示旧身份、
 *    手里是新钥匙」的撒谎态；
 *  - 退出登录只清登录态不动令牌；导入备份后从设置重读。
 * 走注入的假验证器与同步执行（runAsync={it()}），绝不碰真网。
 *
 * 修记（run 35660491254 的编译红）：本文件里 GitHubIdentity 第三个具名参数最初写成了
 * `declared`，实际叫 `scopesDeclared`（engine 件里的字段名）——编译段逮住，一处改正。
 * 教训：对我们自己定义的数据类，具名参数名也要读一遍声明再写，别凭印象拼。
 */
class GithubLoginStateTest {

    private class MemPersist(initial: Map<String, String> = emptyMap()) : UiPersistence {
        val map = initial.toMutableMap()
        override fun load(key: String): String? = map[key]
        override fun save(key: String, value: String) { map[key] = value }
        override fun flush(): String? = null
        override fun drainMessages(): List<String> = emptyList()
    }

    private class FakeAuth : (String) -> GitHubAuthOutcome {
        val tokens = ArrayList<String>()
        var next: GitHubAuthOutcome = GitHubAuthOutcome.Ok(
            GitHubIdentity("xf8410", listOf("repo", "workflow"), scopesDeclared = true),
        )

        override fun invoke(token: String): GitHubAuthOutcome {
            tokens += token
            return next
        }
    }

    /** 假验证器：把 (String) 到 Outcome 的函数缝进 GitHubAuthClient 的 fetch 位。 */
    private fun authClientOf(fake: FakeAuth): com.hualuo.engine.github.GitHubAuthClient =
        com.hualuo.engine.github.GitHubAuthClient { _, token, _, _ ->
            when (val outcome = fake.invoke(token.orEmpty())) {
                is GitHubAuthOutcome.Ok -> GitHubHttpResult(
                    200,
                    """{"login":""" + "\"" + outcome.identity.login + "\"}",
                    false,
                    outcome.identity.scopes.joinToString(", "),
                )
                is GitHubAuthOutcome.Failed -> GitHubHttpResult(401, outcome.reason, false, "")
            }
        }

    private fun stateOf(
        persist: MemPersist = MemPersist(),
        fake: FakeAuth = FakeAuth(),
        toasts: MutableList<String> = ArrayList(),
    ): Pair<GithubLoginState, FakeAuth> {
        val state = GithubLoginState(
            persist = persist,
            toast = { msg -> toasts += msg },
            authClient = authClientOf(fake),
            runAsync = { it() },
        )
        return state to fake
    }

    @Test
    fun emptyTokenIsRefusedWithoutCallingTheVerifier() {
        val (state, fake) = stateOf()
        state.login("   ")

        assertNull("空令牌不许记登录态", state.account)
        assertEquals("空令牌不许发网", 0, fake.tokens.size)
        assertTrue("要给怎么填的出路：${state.note}", state.note?.contains("令牌是空的") == true)
        assertTrue(state.noteIsProblem)
    }

    @Test
    fun successfulLoginRecordsAccountScopesAndPersists() {
        val persist = MemPersist()
        val (state, _) = stateOf(persist)
        state.login("tok-abc")

        assertEquals("xf8410", state.account)
        assertEquals("repo, workflow", state.scopesText)
        assertFalse(state.noteIsProblem)
        assertTrue("收场话带登录名：${state.note}", state.note?.contains("已登录为 xf8410") == true)
        assertEquals("登录名落盘（重开还在）", "xf8410", persist.map[UiKeys.GITHUB_LOGIN])
        assertEquals("权限清单落盘", "repo, workflow", persist.map[UiKeys.GITHUB_SCOPES])
    }

    @Test
    fun failedLoginKeepsUnloggedAndShowsGuidance() {
        val persist = MemPersist()
        val (state, fake) = stateOf(persist)
        fake.next = GitHubAuthOutcome.Failed("GitHub 不认这枚令牌（401）：核对或重新生成")
        state.login("bad")

        assertNull("验证失败不许冒充登录", state.account)
        assertTrue("原因要带出路：${state.note}", state.note?.contains("401") == true)
        assertTrue(state.noteIsProblem)
        assertNull("失败不写登录名", persist.map[UiKeys.GITHUB_LOGIN])
    }

    @Test
    fun tokenEditInvalidatesTheVerifiedLoginThroughAppUiState() {
        // 走真接线：AppUiState.setText 改 github.token 时应当场作废验证（防撒谎态）。
        // 预置「已登录」的盘面，构造出的状态舱启动即接上；全程零网络。
        val persist = MemPersist(
            mapOf(
                UiKeys.GITHUB_LOGIN to "xf8410",
                UiKeys.GITHUB_SCOPES to "repo",
                UiKeys.GITHUB_TOKEN to "old-token",
            ),
        )
        val app = AppUiState(persist = persist)
        assertEquals("预置盘面应当接上：${app.githubLogin.account}", "xf8410", app.githubLogin.account)

        app.setText(UiKeys.GITHUB_TOKEN, "new-token")

        assertNull("令牌一改，验证作废（不许显示旧身份）", app.githubLogin.account)
        assertEquals("盘上的登录名也清掉", "", persist.map[UiKeys.GITHUB_LOGIN])
        assertEquals("盘上的权限也清掉", "", persist.map[UiKeys.GITHUB_SCOPES])
        assertTrue("要说清为什么回到未登录：${app.githubLogin.note}", app.githubLogin.note?.contains("令牌改过") == true)
    }

    @Test
    fun logoutClearsLoginButKeepsTheTokenItself() {
        val persist = MemPersist(mapOf(UiKeys.GITHUB_TOKEN to "tok-keep"))
        val (state, _) = stateOf(persist)
        state.login("tok-keep")
        assertEquals("xf8410", state.account)

        state.logout()

        assertNull(state.account)
        assertEquals("", state.scopesText)
        assertEquals("令牌本体不动（删不删用户自己定）", "tok-keep", persist.map[UiKeys.GITHUB_TOKEN])
        assertEquals("登录名清掉", "", persist.map[UiKeys.GITHUB_LOGIN])
    }

    @Test
    fun reloadFromSettingsPicksUpImportedValues() {
        val persist = MemPersist()
        val (state, _) = stateOf(persist)
        assertNull(state.account)

        // 模拟「导入备份把 github.login/scopes 写进活通道」之后的重读
        persist.map[UiKeys.GITHUB_LOGIN] = "octocat"
        persist.map[UiKeys.GITHUB_SCOPES] = "read:user"
        state.reloadFromSettings()

        assertEquals("octocat", state.account)
        assertEquals("read:user", state.scopesText)
        assertNull("重读后旧收场话清掉", state.note)
    }

    @Test
    fun scopesNotDeclaredIsVoicedHonestly() {
        val (state, fake) = stateOf()
        fake.next = GitHubAuthOutcome.Ok(
            GitHubIdentity("xf8410", emptyList(), scopesDeclared = false),
        )
        state.login("tok")

        assertEquals("xf8410", state.account)
        assertTrue("没声明要如实说：${state.note}", state.note?.contains("没带权限清单声明") == true)
        assertFalse(state.noteIsProblem)
    }
}
