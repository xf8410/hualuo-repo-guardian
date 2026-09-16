package com.hualuo.repotool.ui.state

import android.content.Context
import com.hualuo.engine.settings.FileSettingsStorage
import com.hualuo.engine.settings.SettingsStore
import com.hualuo.engine.store.SessionStore
import java.io.File

/** 设置文件在应用私有目录里的相对路径（备份、迁移、出问题去找它，都按这个路径）。 */
const val SETTINGS_RELATIVE_PATH = "settings/ui.properties"

/** 会话仓目录（一个会话一个 JSONL；杀进程重开还聊得下去的那块盘）。 */
const val SESSIONS_DIR_NAME = "sessions"

/**
 * 启动时建好的持久化出口：[persistence] 给 AppUiState 用，[notice] 是给界面出声用的一句话
 * （正常启动为 null），[store] 是会话仓（建不起来就 null，界面按「没接库」降级并出声）。
 */
class UiPersistenceBundle(
    val persistence: UiPersistence,
    val notice: String?,
    val store: SessionStore?,
)

/**
 * 建真机用的持久化出口。文件放在应用私有目录，读写不需要任何运行时权限。
 *
 * **读不懂不许炸界面**：[SettingsStore] 只在两种情况下抛异常——整份文件超过 1 MiB、
 * 或者那个路径被目录占了——两种都是盘上有东西写坏了。这时候退化成
 * [UiPersistence.None]（按空设置启动，且本次改动**不落盘**，免得把坏文件覆盖掉），
 * 把原因和路径写进 [UiPersistenceBundle.notice] 让界面 toast 出来。
 * 原文件一律**保留不删**：删了就没有现场可查了。
 *
 * 会话仓同理：目录建不起来（私有目录被抽了之类的怪事）就 store=null 降级，
 * 「没接库」在界面上有明说，聊天照常、只是不落盘——不许静默丢字。
 */
fun createUiPersistence(context: Context): UiPersistenceBundle {
    val file = File(context.filesDir, SETTINGS_RELATIVE_PATH)
    val sessionStore = try {
        SessionStore(File(context.filesDir, SESSIONS_DIR_NAME))
    } catch (e: Exception) {
        null
    }
    val sessionNote = if (sessionStore == null) {
        "会话库没建成：本次聊天只在屏上、不落盘（重开会丢），其他功能照常"
    } else {
        null
    }
    return try {
        val store = SettingsStore(FileSettingsStorage(file))
        val pending = store.drainIssues().map { it.detail }
        val notice = buildString {
            if (sessionNote != null) append(sessionNote)
            if (pending.isNotEmpty()) {
                if (isNotEmpty()) append("；")
                append("设置里有读不懂的项，已按默认值继续：").append(pending.joinToString("；"))
            }
        }
        UiPersistenceBundle(
            persistence = SettingsUiPersistence(store),
            notice = notice.ifEmpty { null },
            store = sessionStore,
        )
    } catch (e: Exception) {
        UiPersistenceBundle(
            persistence = UiPersistence.None,
            notice = ("设置文件读不懂，本次按空设置启动且不保存改动（原文件保留未动）：" +
                "${e.message}；路径=${file.path}") +
                (sessionNote?.let { "；$it" } ?: ""),
            store = sessionStore,
        )
    }
}
