package com.hualuo.repotool.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hualuo.engine.github.GitHubCiClient
import com.hualuo.engine.github.GitHubRun
import com.hualuo.engine.settings.FileSettingsStorage
import com.hualuo.engine.settings.SettingsStore
import com.hualuo.repotool.MainActivity
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.state.SETTINGS_RELATIVE_PATH
import com.hualuo.repotool.ui.state.SettingsUiPersistence
import com.hualuo.repotool.ui.state.UiKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * CI 提醒的判定器（**纯 JVM 可测**，不碰任何安卓 API）。
 *
 * 规矩就三条：
 *  - 只有「跑完的 run」才有资格出声（红或绿）；在跑的不记也不响——
 *    要是把 in_progress 的 id 记下来，下一轮它跑完了反而永远安静；
 *  - 记的是「见过的最新完成 run 的 id」：id 没变就闭嘴，变了才响；
 *  - 首次（没记过 id）也响——用户装上就该知道当前是红是绿，不装神秘。
 */
class CiNotifyDecider(lastSeenIdText: String?) {
    private val lastSeenId = lastSeenIdText?.trim()?.toLongOrNull()

    fun decide(runs: List<GitHubRun>, repoLabel: String): CiNotifyDecision {
        val newest = runs.firstOrNull() ?: return CiNotifyDecision(null, null, null)
        val conclusion = newest.conclusion ?: return CiNotifyDecision(null, null, null)
        if (lastSeenId == newest.id) return CiNotifyDecision(null, null, null)
        val verdict = if (conclusion == "success") "CI 绿了" else "CI 红了"
        return CiNotifyDecision(
            title = verdict,
            detail = "$repoLabel · ${newest.headSha.take(7)} · $conclusion · run ${newest.id}",
            newLastId = newest.id,
        )
    }
}

/** 判定结果：title 为 null = 这轮闭嘴。 */
data class CiNotifyDecision(
    val title: String?,
    val detail: String?,
    val newLastId: Long?,
)

/**
 * CI 红绿提醒的后台轮询（接回旧 Agora 魔改版就有的功能，2026-09-16 用户点名要回）。
 *
 * 工作方式：WorkManager 每 15 分钟拍一次（MainActivity 首开时排班，KEEP 不重排），
 * 读设置里的仓库/令牌，拉最近一条 run，没见过且已跑完就发一条系统通知。
 *
 * 安静的三种情况（都不算静默丢功能，是设计如此）：
 *  - 开关关着（设置「GitHub 工作台」里那枚）；
 *  - 通知权限没给（Android 13+ 拒过就去系统设置开，设置页有指引）；
 *  - 网络拉不动（等下一轮，通知不该对断网刷屏）。
 *
 * 纪律：**不开前台服务**——manifest 里永远没有 <service>（旧 Agora 崩 140 次的病）。
 */
class CiNotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val appContext = applicationContext
        val persist = SettingsUiPersistence(
            SettingsStore(FileSettingsStorage(File(appContext.filesDir, SETTINGS_RELATIVE_PATH))),
        )
        // 开关默认开；读不懂按开走（这个键只有 true/false 两种写法，异常值不值得堵一次响铃）
        if (persist.load(UiKeys.CI_NOTIFY)?.trim()?.equals("false", ignoreCase = true) == true) {
            return@withContext Result.success()
        }
        val notifier = NotificationManagerCompat.from(appContext)
        if (!notifier.areNotificationsEnabled()) return@withContext Result.success()
        val repo = persist.load(UiKeys.GITHUB_REPO)?.trim()?.takeIf { it.isNotEmpty() }
            ?: AppUiState.DEFAULT_GITHUB_REPO
        val token = persist.load(UiKeys.GITHUB_TOKEN)?.trim()?.takeIf { it.isNotEmpty() }
        val snapshot = runCatching { GitHubCiClient().latestRuns(repo, token, limit = 1) }
            .getOrElse { return@withContext Result.success() }
        if (snapshot.error != null) return@withContext Result.success()

        val decision = CiNotifyDecider(persist.load(UiKeys.CI_LAST_RUN_ID)).decide(snapshot.runs, repo)
        decision.newLastId?.let { id ->
            persist.save(UiKeys.CI_LAST_RUN_ID, id.toString())
            // 记账尽力而为：存失败的最坏结果是下一轮重复响一次，不哑
            runCatching { persist.flush() }
        }
        val title = decision.title
        val detail = decision.detail
        if (title != null && detail != null) {
            raise(appContext, notifier, title, detail)
        }
        Result.success()
    }

    private fun raise(context: Context, notifier: NotificationManagerCompat, title: String, detail: String) {
        notifier.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "CI 提醒", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        // 33+ 没权限时 notify 会抛 SecurityException：上面已查过权限，这里再兜一层防竞态
        runCatching { notifier.notify(NOTIFICATION_ID_BASE + (detail.hashCode() % 1000), notification) }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "ci-notify"
        private const val CHANNEL_ID = "ci"
        private const val NOTIFICATION_ID_BASE = 20_260_916
    }
}
