package com.example.hbsz_app_android.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.hbsz_app_android.ui.navigation.AppNavHost
import com.example.hbsz_app_android.ui.navigation.Screen
import com.example.hbsz_app_android.ui.navigation.navigateToTab

/**
 * 主页面：自适应导航套件（手机底部导航栏 / 平板侧边栏）+ 导航图。
 * 四个一级 Tab：首页 / 项目 / 消息 / 我的。
 *
 * 通知触达专项："消息"Tab 展示全局未读角标（NotificationCenter 实时驱动：
 * WebSocket 新通知到达 +1，消息页加载/已读后以服务端计数校准）。
 */
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val navItems = listOf(Screen.Home, Screen.Project, Screen.Notification, Screen.Mine)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            navItems.forEach { screen ->
                item(
                    icon = {
                        if (screen == Screen.Notification && unreadCount > 0) {
                            // 未读角标：99+ 封顶显示（Badge 组件自带语义色）
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                                    }
                                }
                            ) {
                                Icon(screen.icon, contentDescription = screen.label)
                            }
                        } else {
                            Icon(screen.icon, contentDescription = screen.label)
                        }
                    },
                    label = { Text(screen.label) },
                    selected = currentRoute == screen.route,
                    onClick = { navController.navigateToTab(screen.route) }
                )
            }
        }
    ) {
        AppNavHost(navController = navController, modifier = Modifier.fillMaxSize())
    }
}
