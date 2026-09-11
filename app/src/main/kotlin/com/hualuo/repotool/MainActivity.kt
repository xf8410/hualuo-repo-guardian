package com.hualuo.repotool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hualuo.engine.io.DEFAULT_BUFFER_BYTES
import com.hualuo.engine.version.AppVersion

/**
 * 安卓这边只做"壳"：拿到权限、把界面画出来。
 * 任何会出错的实在活（搬文件、算校验、解压、跟 GitHub 说话）都在 :engine 里，那边能单独跑测试。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 版本号由构建时从 version.properties 注入，界面不写死任何数字
        val version = AppVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        setContent {
            MaterialTheme {
                HomeScreen(version)
            }
        }
    }
}

@Composable
private fun HomeScreen(version: AppVersion) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "${stringResource(R.string.home_version_prefix)} ${version.display()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = "${stringResource(R.string.home_engine_ready)}（缓冲区 ${DEFAULT_BUFFER_BYTES / 1024} KiB）",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
