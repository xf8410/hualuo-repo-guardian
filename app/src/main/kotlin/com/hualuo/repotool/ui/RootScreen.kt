package com.hualuo.repotool.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.HualuoApplication
import com.hualuo.repotool.R
import com.hualuo.repotool.backup.BackupGateway
import com.hualuo.repotool.courier.CourierDelivery
import com.hualuo.repotool.ui.chat.ChatScreen
import com.hualuo.repotool.ui.chat.Composer
import com.hualuo.repotool.ui.chat.SheetsLayer
import com.hualuo.repotool.ui.components.ConfirmDialog
import com.hualuo.repotool.ui.data.DemoCtx
import com.hualuo.repotool.ui.drawer.DrawerOverlay
import com.hualuo.repotool.ui.model.NavTab
import com.hualuo.repotool.ui.observe.ObserveScreen
import com.hualuo.repotool.ui.repo.RepoScreen
import com.hualuo.repotool.ui.settings.SettingsOverlay
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.state.CourierPick
import com.hualuo.repotool.ui.tasks.TasksScreen
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.CardBg
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk
import com.hualuo.repotool.ui.tools.ToolsScreen
import kotlinx.coroutines.delay

/** 自动保存的去抖窗口：改动停在这个时间之后才真落盘（打字时不一个字写一次盘）。 */
private const val AUTO_SAVE_DEBOUNCE_MS = 600L

/**
 * 底栏图标：画出来的矢量图（res/drawable/ic_nav_*.xml），不是表情字符。
 *
 * 这里的对应关系故意不放进 NavTab：模型层只管「有什么」，长什么样是渲染层的事，
 * 换图只动这张表和 drawable，数据表不跟着改。
 */
private val NavTabIcons = mapOf(
    NavTab.Chat to R.drawable.ic_nav_chat,
    NavTab.Tasks to R.drawable.ic_nav_tasks,
    NavTab.ToolsPage to R.drawable.ic_nav_tools,
    NavTab.Repo to R.drawable.ic_nav_repo,
    NavTab.Observe to R.drawable.ic_nav_observe,
)

/** 生成中的细忙条：2dp 高的一条主色，告诉人「还在跑」。 */
private val BusyBarAlpha = 0.55f

/**
 * 根界面：顶栏（菜单钮 / 页名 / ctx 账本）+ 五页内容 + 输入区（仅回合流）+ 底栏五签，
 * 上面盖抽屉、设置层、弹层、toast。所有浮层都是「壳内」的 Box 层：
 * 外壳锁高、滚动只发生在各层内部（原型漂移病的根治，Compose 版同方）。
 *
 * **状态挂进程不挂界面（0.6.0）**：AppUiState 与备份进度行都从 HualuoApplication
 * （进程单例）拿，不再 remember 新建——切出 App（回桌面/转屏）让 Activity 重建时，
 * 拿到的是**同一个**状态实例：生成中的流照跑、半截话和忙灯原样接上；备份跑几分钟
 * 进度行也不丢。「切出去对话就停/失败」的病根。
 * remember(kernel) 只是防重组期反复取值，实例本身不依赖它。
 *
 * 图形一律 vector drawable（用户拍板：UI 不是表情，要画图）。tint 走主题色：
 * 选中主色、未选次要灰，跟文字同一条规则。
 *
 * 持久化从这里进：bundle 由内核持有（全进程一份），启动通知经 consumeStartupNotice
 * 取走即没——Activity 重建不再复读同一条「设置读不懂」。界面字段变了照样攒着，
 * 停 AUTO_SAVE_DEBOUNCE_MS 落一次盘，离开时再兜一次。
 * 看护清单里除了逐个字段，还有 state.settingsRevision——真文本（提供商地址密钥那类）
 * 每改一次推一格修订号，这里只看这一个数：文本键以后增减，看护点都不用跟着改。
 * 任何一次「没存上」或「设置里有读不懂的项」都必须走 toast，不许静默；
 * 会话落盘的岔子（storeIssue）同规矩：写不进就是「重开就丢」，必须出声。
 *
 * 备份（数据控制）与文件投递（0.7.0，长任务页）的桥都架在这里：设置页/长任务页的按钮
 * 只发动作请求（state.pendingDataAction），系统文件选择器（SAF）归这层开——纯状态层
 * 不认识 ActivityResult。选择器结果回来后网关/投递在后台线程流式进出，进度经
 * kernel.backupProgress / kernel.courierProgress（进程级）画在顶栏下面，收尾必须写 null
 * 收行；结果走状态层活通道应用并出声，各管一段（主线程做大会计 IO 是 ANR/闪退病根，
 * 0.5.0 根治；黑盒等待是 0.6.0 根治）。
 *
 * @param versionLabel 版本串由入口从 BuildConfig 注入（单源=version.properties），界面不写死。
 */
@Composable
fun HualuoApp(versionLabel: String) {
    val context = LocalContext.current
    // 进程单例：Activity 怎么死都拿到同一个内核、同一个状态
    val kernel = remember(context) { context.applicationContext as HualuoApplication }
    val state = remember(kernel) { kernel.uiState }
    state.versionLabel = versionLabel

    // 数据控制的三个系统选择器（导出=建文档、导入=选文件、旧包=选文件）；结果一律出声，不静默
    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) {
            state.clearPendingDataAction()
            return@rememberLauncherForActivityResult
        }
        state.toast("正在打包备份……")
        Thread({
            kernel.backupProgress = "正在打包备份……"
            val failure = runCatching {
                BackupGateway.exportTo(context, uri, state.versionLabel) { done, total ->
                    kernel.backupProgress = "正在打包 $done/$total 份会话"
                }
            }.getOrElse { "导出失败：${it.message ?: "写不进去"}" }
            kernel.backupProgress = null
            state.toast(failure ?: "备份已导出（设置 + 全部会话）")
            state.clearPendingDataAction()
        }, "hualuo-backup").start()
    }
    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            state.clearPendingDataAction()
            return@rememberLauncherForActivityResult
        }
        state.toast("正在导入备份……")
        Thread({
            kernel.backupProgress = "正在导入备份……"
            val outcome = runCatching {
                BackupGateway.readImport(context, uri) { done ->
                    kernel.backupProgress = "已读 $done 份会话……"
                }
            }.getOrElse {
                BackupGateway.ImportedBackup(
                    false, null, 0, 0,
                    listOf("读不了这个文件：${it.message ?: "打不开"}"),
                )
            }
            kernel.backupProgress = null
            state.toast(state.applyImportedBackup(outcome))
            state.clearPendingDataAction()
        }, "hualuo-import").start()
    }
    val importAgora = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            state.clearPendingDataAction()
            return@rememberLauncherForActivityResult
        }
        state.toast("正在读旧 Agora 备份……")
        Thread({
            kernel.backupProgress = "正在读旧 Agora 备份……"
            val outcome = runCatching {
                BackupGateway.readAgoraImport(context, uri) { done, total ->
                    kernel.backupProgress = "正在落盘 $done/$total 份会话"
                }
            }.getOrElse {
                BackupGateway.AgoraImportOutcome(
                    false, null, 0, 0,
                    "读不了这个文件：${it.message ?: "打不开"}",
                )
            }
            kernel.backupProgress = null
            state.toast(state.applyAgoraImport(outcome))
            state.clearPendingDataAction()
        }, "hualuo-agora").start()
    }

    // 文件投递的两个系统选择器（0.7.0 B 段）：选一批文件 / 选一棵目录树。
    // 授权尽量拿持久化（runCatching：个别提供方不给就算了——投递在本进程内做完，不赌跨进程重启）；
    // 选完只把「选了什么」记进状态层批账本，真名与大小在投递收集时才 stat。
    val courierPickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) {
            state.clearPendingDataAction()
            return@rememberLauncherForActivityResult
        }
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        state.addCourierPicks(
            uris.map { CourierPick(it.lastPathSegment ?: it.toString(), it.toString(), false) },
        )
        state.toast("已选 ${uris.size} 个文件：点「开始投递」发走")
        state.clearPendingDataAction()
    }
    val courierPickTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) {
            state.clearPendingDataAction()
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        state.addCourierPicks(
            listOf(CourierPick(uri.lastPathSegment ?: "目录", uri.toString(), true)),
        )
        state.toast("已选一棵目录：点「开始投递」发走")
        state.clearPendingDataAction()
    }

    LaunchedEffect(state.pendingDataAction) {
        when (val action = state.pendingDataAction) {
            "export" -> exportBackup.launch("hualuo-backup-${state.versionLabel.replace(" ", "-")}.zip")
            "import" -> importBackup.launch(arrayOf("application/zip", "application/octet-stream"))
            "import_agora" -> importAgora.launch(arrayOf("*/*"))
            AppUiState.ACTION_COURIER_PICK_FILES -> courierPickFiles.launch(arrayOf("*/*"))
            AppUiState.ACTION_COURIER_PICK_TREE -> courierPickTree.launch(null)
            AppUiState.ACTION_COURIER_DELIVER -> Thread({
                CourierDelivery.run(context, kernel, state)
            }, "hualuo-courier").start()
            else -> Unit
        }
    }

    // 启动通知（设置文件读不懂 / 会话库没建成）：取走即没，进程活着只出一次声
    LaunchedEffect(kernel) {
        kernel.consumeStartupNotice()?.let { state.toast(it) }
    }

    // 会话落盘岔子：写不进必须出声（正文还在屏上，但重开就丢——人得知道这事）
    LaunchedEffect(state.chat.storeIssue) {
        state.chat.storeIssue?.let {
            state.toast(it)
            state.chat.clearStoreIssue()
        }
    }

    // 改动落盘：去抖 600ms，失败与坏消息都要出声
    LaunchedEffect(
        state.input,
        state.currentModel,
        state.tab,
        state.thinkOn,
        state.thinkLevel,
        state.webSearchOn,
        state.shellOn,
        state.codeExecOn,
        state.relayOn,
        state.lockToConversation,
        state.settingsRevision,
    ) {
        delay(AUTO_SAVE_DEBOUNCE_MS)
        val failure = state.flushPersistence()
        val messages = state.persistenceMessages()
        if (failure == null && messages.isEmpty()) return@LaunchedEffect
        val notice = buildString {
            if (failure != null) append("设置没存上：$failure")
            if (messages.isNotEmpty()) {
                if (isNotEmpty()) append("；")
                append("设置提示：").append(messages.joinToString("；"))
            }
        }
        state.toast(notice)
    }

    DisposableEffect(Unit) {
        onDispose { state.flushPersistence() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(state)
                // 长活进度行（备份导出/导入/兑换、文件投递）：内核持有，Activity 重建不丢
                ProgressLine(kernel.backupProgress)
                ProgressLine(kernel.courierProgress)
                // 生成中的细忙条：状态挂进程后切出去也照跑，回来这条还在（或已经没了）
                if (state.busy) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Accent.copy(alpha = BusyBarAlpha)),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (state.tab) {
                        NavTab.Chat -> ChatScreen(state)
                        NavTab.Tasks -> TasksScreen(state)
                        NavTab.ToolsPage -> ToolsScreen(state)
                        NavTab.Repo -> RepoScreen(state)
                        NavTab.Observe -> ObserveScreen(state)
                    }
                }
                if (state.tab == NavTab.Chat) {
                    Composer(state)
                }
                BottomNav(state)
            }

            // 抽屉（左侧滑入，自带遮罩）
            AnimatedVisibility(
                visible = state.drawerOpen,
                enter = slideInHorizontally(tween(200)) { -it },
                exit = slideOutHorizontally(tween(200)) { -it },
            ) {
                DrawerOverlay(state)
            }

            // 设置层（右滑入，盖满整壳；滑出方向与滑入对称，都是从右缘走）
            AnimatedVisibility(
                visible = state.settingsOpen,
                enter = slideInHorizontally(tween(220)) { it },
                exit = slideOutHorizontally(tween(220)) { it },
            ) {
                SettingsOverlay(state)
            }

            // 原位弹层（模型/工具/任务详情互斥）+ 确认框 + toast
            SheetsLayer(state)
            ConfirmDialog(state)
            ToastBubble(state)
        }
    }
}

/** 长活进度行（0.6.0 起的形状）：顶栏下面一条 CardBg 窄条，null 不占位。备份与文件投递共用。 */
@Composable
private fun ProgressLine(text: String?) {
    if (text == null) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 11.5.sp, color = SubInk)
    }
}

@Composable
private fun TopBar(state: AppUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg)
            .padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 三横线 + 「菜单」两个字：图形给人扫，文字给人读，缺一边都会看不懂
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable { state.drawerOpen = !state.drawerOpen }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_menu),
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("菜单", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(10.dp))
        Text(state.tab.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .background(CardBg, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(
                DemoCtx,
                fontSize = 11.sp,
                color = SubInk,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun BottomNav(state: AppUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg)
            .padding(top = 7.dp, bottom = 11.dp),
    ) {
        NavTab.entries.forEach { tab ->
            val on = state.tab == tab
            val color = if (on) Accent else SubInk
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { state.tab = tab }
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 图上只给一个 tint：选中就实心（视觉更重），未选描线本来就淡
                NavTabIcons[tab]?.let { res ->
                    Icon(
                        painter = painterResource(res),
                        contentDescription = tab.title,
                        tint = color,
                        modifier = Modifier.size(if (on) 22.dp else 21.dp),
                    )
                }
                Spacer(Modifier.size(2.dp))
                Text(
                    tab.title,
                    fontSize = 11.5.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun ToastBubble(state: AppUiState) {
    // 原型行为：toast 1.8 秒自动收（clearTimeout + setTimeout 的 Compose 等价）
    LaunchedEffect(state.toastToken) {
        if (state.toastToken > 0) {
            delay(1800)
            state.toastVisible = false
        }
    }
    AnimatedVisibility(
        visible = state.toastVisible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(250)),
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 128.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                text = state.toastText,
                color = Color.White,
                fontSize = 12.5.sp,
                modifier = Modifier
                    .background(Color(0xDD22262B), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
