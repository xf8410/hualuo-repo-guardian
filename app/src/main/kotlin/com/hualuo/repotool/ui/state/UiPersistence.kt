package com.hualuo.repotool.ui.state

import com.hualuo.engine.settings.SettingsStore

/**
 * 界面状态的持久化出口。
 *
 * 为什么要有这层：`AppUiState` 是纯 Kotlin、CI 里当普通 JVM 类测的（`AppUiStateTest` 11 条），
 * 它不能认识安卓、也不能认识文件。所以持久化从这里以接口进来：
 *  - 测试传 [UiPersistence.None]，行为跟接线前逐字一致（默认关＝不变，家规）；
 *  - 真机上 [SettingsUiPersistence] 把改动交给 engine 的 `SettingsStore`。
 *
 * 写盘时机由界面决定（输入框是每敲一个字都变的，不能次次落盘）：改值走 [save] 只进内存，
 * 攒一会儿再 [flush] 真落盘；退出前也必须 flush 一次。
 */
interface UiPersistence {
    /** 读一个键；没有就返回 null（由调用方决定默认值，这里不藏默认）。 */
    fun load(key: String): String?

    /** 记一个新值到内存（不落盘）。 */
    fun save(key: String, value: String)

    /** 落盘。返回 null 表示没事；返回字符串就是失败原因，界面必须出声，不许静默丢设置。 */
    fun flush(): String?

    /** 取走累计的坏消息（设置文件里有非法值、半个表情之类），取走即清空，免得反复刷屏。 */
    fun drainMessages(): List<String>

    companion object {
        /** 不接后端的实现：单测与预览用。 */
        val None: UiPersistence = object : UiPersistence {
            override fun load(key: String): String? = null
            override fun save(key: String, value: String) = Unit
            override fun flush(): String? = null
            override fun drainMessages(): List<String> = emptyList()
        }
    }
}

/**
 * 把 engine 的 [SettingsStore] 接到界面上。刻意只依赖 `SettingsStore` 而不是 `Context`：
 * 这样这条接线也能在纯 JVM 里测（安卓那一头只剩几行工厂函数，见 `AppSettings.kt`）。
 */
class SettingsUiPersistence(private val store: SettingsStore) : UiPersistence {

    override fun load(key: String): String? = store.raw(key)

    override fun save(key: String, value: String) {
        store.setString(key, value)
    }

    override fun flush(): String? {
        val result = store.saveIfDirty() ?: return null
        return if (result.persisted) null else result.failure ?: "写入失败（原因未给出）"
    }

    override fun drainMessages(): List<String> = store.drainIssues().map { it.detail }
}

/**
 * 界面用到的键名。一律 ASCII 点分小写 —— engine 那边会校验键名，中文键名会被拒。
 * 键名一旦进过真机就不许改（改了老设置读不到），要换就在新版本里做迁移。
 */
object UiKeys {
    /** 底栏停在第几页（枚举名，存字符串是为了改名时能出声而不是错位）。 */
    const val TAB = "ui.tab"
    const val DRAFT = "ui.draft"
    const val MODEL = "ui.model"
    const val THINK_ON = "ui.think_on"
    const val THINK_LEVEL = "ui.think_level"
    const val WEB_SEARCH_ON = "ui.web_search_on"
    const val SHELL_ON = "ui.shell_on"
    const val CODE_EXEC_ON = "ui.code_exec_on"
    const val RELAY_ON = "ui.relay_on"
    const val LOCK_TO_CONVERSATION = "ui.lock_to_conversation"

    /** 一次喂模型的历史上限（条）。存文本数字，读不懂回 40，范围 1-500 在读方收口。 */
    const val MAX_HISTORY = "ui.max_history_turns"

    /** GitHub 只读两格（仓库CI 页与「检查更新」用）。首次上线 2026-09-16。 */
    const val GITHUB_REPO = "github.repo"
    const val GITHUB_TOKEN = "github.token"

    /**
     * GitHub 登录态（2026-09-22）：验证通过后记下的登录名与权限清单文本（X-OAuth-Scopes）。
     * 空串 = 未登录。令牌本体在 [GITHUB_TOKEN]；这里只记「验过的是谁、有什么权限」——
     * 填了令牌不等于登录，验过才算。
     */
    const val GITHUB_LOGIN = "github.login"
    const val GITHUB_SCOPES = "github.scopes"

    /** CI 红绿提醒的开关与记账（后台轮询见 notify/CiNotifyWorker）。首次上线 2026-09-16。 */
    const val CI_NOTIFY = "ui.ci_notify_on"
    const val CI_LAST_RUN_ID = "ci.last_run_id"

    /**
     * 文件投递（courier）三格：目标仓/分支/令牌。首次上线 2026-09-17。
     * 令牌刻意单独留格而不是只靠 github.token：投递仓与 CI 观察仓可以是两个仓；
     * 但投递那格留空时借用 github.token（同一把钥匙不逼人填两遍），借用关系见 AppUiState.courierToken。
     */
    const val COURIER_REPO = "courier.repo"
    const val COURIER_BRANCH = "courier.branch"
    const val COURIER_TOKEN = "courier.token"
}
