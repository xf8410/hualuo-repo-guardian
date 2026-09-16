package com.hualuo.repotool

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
