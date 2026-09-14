package com.hualuo.repotool.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.hualuo.repotool.ui.state.createUiPersistence
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
 * 根界面：v13.1 的骨架——顶栏（☰/页名/ctx 账本）+ 五页内容 + 输入区（仅回合流）+ 底栏五签，
 * 上面盖抽屉、设置层、弹层、toast。所有浮层都是「壳内」的 Box 层：
 * 外壳锁高、滚动只发生在各层内部（原型漂移病的根治，Compose 版同方）。
 *
 * 持久化从这里进：启动时读一份设置（读不懂会带原因退化，不炸界面），
 * 之后界面字段变了就攒着，停 [AUTO_SAVE_DEBOUNCE_MS] 落一次盘，离开时再兜一次。
 * 任何一次「没存上」或「设置里有读不懂的项」都必须走 toast，不许静默。
 *
 * @param versionLabel 版本串由入口从 BuildConfig 注入（单源=version.properties），界面不写死。
 */
@Composable
fun HualuoApp(versionLabel: String) {
    val context = LocalContext.current
    val bundle = remember(context) { createUiPersistence(context) }
    val state = remember(bundle) { AppUiState(bundle.persistence) }
    state.versionLabel = versionLabel

    // 启动通知（设置文件读不懂 / 有读不懂的项）：只弹一次
    LaunchedEffect(bundle.notice) {
        bundle.notice?.let { state.toast(it) }
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

            // 设置层（右滑入，盖满整壳）
            AnimatedVisibility(
                visible = state.settingsOpen,
                enter = slideInHorizontally(tween(220)) { it },
                exit = slideOutHorizontally(tween(220)) { it },
            ) {
                SettingsOverlay(state)
            }

            // 原位弹层（模型/工具/任务详情）+ 确认框 + toast
            SheetsLayer(state)
            ConfirmDialog(state)
            ToastBubble(state)
        }
    }
}

@Composable
private fun TopBar(state: AppUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(CardBg)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { state.drawerOpen = !state.drawerOpen },
            contentAlignment = Alignment.Center,
        ) {
            Text("\u2630", fontSize = 15.sp, color = Ink)
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
            .padding(top = 5.dp, bottom = 11.dp),
    ) {
        NavTab.entries.forEach { tab ->
            val on = state.tab == tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { state.tab = tab },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(tab.icon, fontSize = 18.sp, color = if (on) Accent else SubInk)
                Text(
                    tab.title,
                    fontSize = 10.5.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) Accent else SubInk,
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
