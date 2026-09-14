package com.hualuo.repotool.ui.state

import android.content.Context
import com.hualuo.engine.settings.FileSettingsStorage
import com.hualuo.engine.settings.SettingsStore
import java.io.File

/** 设置文件在应用私有目录里的相对路径（备份、迁移、出问题去找它，都按这个路径）。 */
const val SETTINGS_RELATIVE_PATH = "settings/ui.properties"

/**
 * 启动时建好的持久化出口：[persistence] 给 AppUiState 用，[notice] 是给界面出声用的一句话
 * （正常启动为 null）。
 */
class UiPersistenceBundle(
    val persistence: UiPersistence,
    val notice: String?,
)

/**
 * 建真机用的持久化出口。文件放在应用私有目录，读写不需要任何运行时权限。
 *
 * **读不懂不许炸界面**：[SettingsStore] 只在两种情况下抛异常——整份文件超过 1 MiB、
 * 或者那个路径被目录占了——两种都是盘上有东西写坏了。这时候退化成
 * [UiPersistence.None]（按空设置启动，且本次改动**不落盘**，免得把坏文件覆盖掉），
 * 把原因和路径写进 [UiPersistenceBundle.notice] 让界面 toast 出来。
 * 原文件一律**保留不删**：删了就没有现场可查了。
 */
fun createUiPersistence(context: Context): UiPersistenceBundle {
    val file = File(context.filesDir, SETTINGS_RELATIVE_PATH)
    return try {
        val store = SettingsStore(FileSettingsStorage(file))
        val pending = store.drainIssues().map { it.detail }
        UiPersistenceBundle(
            persistence = SettingsUiPersistence(store),
            notice = if (pending.isEmpty()) {
                null
            } else {
                "设置里有读不懂的项，已按默认值继续：" + pending.joinToString("；")
            },
        )
    } catch (e: Exception) {
        UiPersistenceBundle(
            persistence = UiPersistence.None,
            notice = "设置文件读不懂，本次按空设置启动且不保存改动（原文件保留未动）：" +
                "${e.message}；路径=${file.path}",
        )
    }
}
