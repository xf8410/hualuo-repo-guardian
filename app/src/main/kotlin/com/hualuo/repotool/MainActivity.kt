package com.hualuo.repotool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hualuo.repotool.ui.HualuoApp
import com.hualuo.repotool.ui.theme.HualuoTheme

/**
 * 安卓壳入口：v13.1 定稿 UI（hualuo-ui-proto ui/v13.html）的 Compose 实现从这里进。
 * 任何会出错的实在活（搬文件、算校验、解压、跟 GitHub 说话、会话库）都在 :engine / 后续模块里，
 * 界面只拿状态画东西——和原壳的分工不变。
 * 版本串走 BuildConfig（构建时读 version.properties，CI 红线四盯单源），界面里一个数字都不写。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val versionLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        setContent {
            HualuoTheme {
                HualuoApp(versionLabel = versionLabel)
            }
        }
    }
}
