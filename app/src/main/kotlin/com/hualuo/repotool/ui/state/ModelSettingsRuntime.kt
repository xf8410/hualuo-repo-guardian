package com.hualuo.repotool.ui.state

/** 进程级模型设置句柄。Application 创建一次，设置页与聊天发送共用。 */
object ModelSettingsRuntime {
    @Volatile private var installed: ModelSettingsState? = null

    fun install(state: ModelSettingsState) {
        installed = state
    }

    fun current(): ModelSettingsState? = installed
}
