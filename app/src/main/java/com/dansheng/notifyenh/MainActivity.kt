package com.dansheng.notifyenh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.dansheng.notifyenh.data.prefs.AppPreferences
import com.dansheng.notifyenh.data.prefs.ThemeMode
import com.dansheng.notifyenh.service.NotifyEnhService
import com.dansheng.notifyenh.ui.components.ChangelogDialog
import com.dansheng.notifyenh.ui.screens.NotificationListScreen
import com.dansheng.notifyenh.ui.screens.SettingsScreen
import com.dansheng.notifyenh.ui.screens.TaskerScreen
import com.dansheng.notifyenh.ui.theme.NotifyEnhTheme
import com.dansheng.notifyenh.util.PermissionUtils.isNotificationServiceEnabled
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appPreferences = AppPreferences(this)

        // 当应用启动时，如果权限已授予但服务未连接，检查是否为手动关闭，否则尝试强制重连
        lifecycleScope.launch {
            if (isNotificationServiceEnabled(this@MainActivity) &&
                !NotifyEnhService.isServiceRunning.value &&
                !appPreferences.isManuallyStoppedFlow.first()
            ) {
                NotifyEnhService.tryReconnectService(this@MainActivity)
            }
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by appPreferences.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            NotifyEnhTheme(darkTheme = darkTheme) {
                NotifyEnhApp(appPreferences)
            }
        }
    }

}

@Composable
fun NotifyEnhApp(appPreferences: AppPreferences) {
    val context = LocalContext.current
    val destinations = AppDestinations.entries
    val pagerState = rememberPagerState(pageCount = { destinations.size })
    val scope = rememberCoroutineScope() // 用于在点击事件中启动协程

    val lastSeenVersion by appPreferences.lastSeenVersionFlow.collectAsState(initial = -1)
    val currentVersion = remember(context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.longVersionCode.toInt()
        } catch (_: Exception) {
            0
        }
    }

    if (lastSeenVersion != -1 && currentVersion > lastSeenVersion) {
        ChangelogDialog(
            onDismiss = {
                scope.launch {
                    appPreferences.setLastSeenVersion(currentVersion)
                }
            }
        )
    }

    // 拦截返回键：如果不在首页则跳转回首页，否则直接退出应用
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    // 直接通过当前 Pager 的页码计算出当前选中的导航目标
    val currentDestination = destinations[pagerState.currentPage]

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            destinations.forEach { destination ->
                val targetPage = destinations.indexOf(destination)
                item(
                    icon = {
                        Icon(
                            painterResource(destination.icon),
                            contentDescription = stringResource(destination.label)
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                    selected = destination == currentDestination, // 依然能正确高亮
                    onClick = {
                        // 3. 点击时直接异步触发滚动，不再污染中间状态
                        scope.launch {
                            // 如果你想带流畅动画，用 animateScrollToPage
                            pagerState.animateScrollToPage(targetPage)

                            // 提示：如果你发现跨多页动画依然不理想，可以直接用 scrollToPage（无动画秒切）
                            // pagerState.scrollToPage(targetPage)
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                beyondViewportPageCount = 1
            ) { page ->
                when (destinations[page]) {
                    AppDestinations.HOME -> NotificationListScreen()
                    AppDestinations.Tasker -> TaskerScreen()
                    AppDestinations.PROFILE -> SettingsScreen()
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: Int,
    val icon: Int,
) {
    HOME(R.string.nav_home, R.drawable.ic_home),
    Tasker(R.string.nav_tasks, R.drawable.ic_tasks),
    PROFILE(R.string.nav_settings, R.drawable.ic_settings),
}
