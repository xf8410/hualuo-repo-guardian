package com.hualuo.repotool

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hualuo.repotool.notify.CiNotifyWorker
import com.hualuo.repotool.ui.HualuoApp
import com.hualuo.repotool.ui.theme.HualuoTheme
import java.util.concurrent.TimeUnit

/**
 * 安卓壳入口：v13.1 定稿 UI（hualuo-ui-proto ui/v13.html）的 Compose 实现从这里进。
 * 任何会出错的实在活（搬文件、算校验、解压、跟 GitHub 说话、会话库）都在 :engine / 后续模块里，
 * 界面只拿状态画东西——和原壳的分工不变。
 * 版本串走 BuildConfig（构建时读 version.properties，CI 红线四盯单源），界面里一个数字都不写。
 *
 * CI 提醒（2026-09-16 接回旧 Agora 魔改版就有的功能）：
 *  - 通知权限 Android 13+ 首开问一次，拒了不烦第二遍（去系统设置开，设置页有指引）；
 *  - 后台轮询排班 KEEP：已排过就不重排，干活的是 CiNotifyWorker，不开前台服务。
 *
 * 系统栏让位（2026-09-22 修，用户手机实报「菜单点不到、底签被裁」）：
 * 与旧 Agora 同款做法——透明系统栏 + 内容自己让位（三处 padding 在 RootScreen：顶栏、底栏、键盘）。
 * targetSdk 35 的 Android 15+ 被系统强制 edge-to-edge，旧版本那种「系统自动替 App 让位」没有了；
 * 这里显式调用之后，所有机型的让位行为一致。light 档 = 深色系统栏图标（本 App 只有亮色主题，
 * 不跟随系统暗色——否则暗色手机上图标会变成浅色、压在浅底上看不见）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 透明系统栏 + 内容自己让位（对齐旧 Agora；细节见类头注释）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 导航栏不做系统强制对比遮罩，让 App 自己的底色透出去（旧 Agora 同款）
            window.isNavigationBarContrastEnforced = false
        }
        ensureCiNotifyReady()
        val versionLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        setContent {
            HualuoTheme {
                HualuoApp(versionLabel = versionLabel)
            }
        }
    }

    private fun ensureCiNotifyReady() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFY)
        }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CiNotifyWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CiNotifyWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    private companion object {
        const val REQUEST_NOTIFY = 1001
    }
}
