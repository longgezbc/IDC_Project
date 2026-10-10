package com.example.hbsz_app_android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.hbsz_app_android.ui.archive.ArchiveScreen
import com.example.hbsz_app_android.ui.approval.ApprovalTodoScreen
import com.example.hbsz_app_android.ui.customer.CustomerScreen
import com.example.hbsz_app_android.ui.daily.DailyReportScreen
import com.example.hbsz_app_android.ui.document.DocumentScreen
import com.example.hbsz_app_android.ui.home.HomeScreen
import com.example.hbsz_app_android.ui.mine.MineScreen
import com.example.hbsz_app_android.ui.notification.NotificationScreen
import com.example.hbsz_app_android.ui.profile.AboutScreen
import com.example.hbsz_app_android.ui.profile.AccountSecurityScreen
import com.example.hbsz_app_android.ui.profile.NotificationSettingsScreen
import com.example.hbsz_app_android.ui.profile.ProfileScreen
import com.example.hbsz_app_android.ui.progress.MySubmissionsScreen
import com.example.hbsz_app_android.ui.progress.ProgressReportScreen
import com.example.hbsz_app_android.ui.progress.ReviewScreen
import com.example.hbsz_app_android.ui.project.ProjectDetailScreen
import com.example.hbsz_app_android.ui.project.ProjectScreen
import com.example.hbsz_app_android.ui.project.TransferLogsScreen
import com.example.hbsz_app_android.ui.statistics.StatisticsScreen
import com.example.hbsz_app_android.ui.task.MyTasksScreen
import com.example.hbsz_app_android.ui.workload.WorkloadReportScreen
import com.example.hbsz_app_android.ui.workload.WorkloadReviewScreen

/**
 * 主页面导航图：4 个底部 Tab + 14 个二级页。
 * 新增页面步骤：1) Screen.kt/Dest 添加路由；2) 此处注册 composable；3) 入口回调接线。
 */
@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        // ===== 底部 Tab =====
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenProject = { navController.navigateToTab(Screen.Project.route) },
                onOpenNotification = { navController.navigateToTab(Screen.Notification.route) },
                onOpenReport = { navController.navigate(Dest.progressReport()) },
                onOpenTasks = { navController.navigate(Dest.MY_TASKS) },
                onOpenDocuments = { navController.navigate(Dest.DOCUMENTS) },
                onOpenArchive = { navController.navigate(Dest.ARCHIVE) },
                onOpenDailyReport = { navController.navigate(Dest.DAILY_REPORT) },
                onOpenReview = { navController.navigate(Dest.REVIEW) },
                onOpenCustomer = { navController.navigate(Dest.CUSTOMER) },
                onOpenStatistics = { navController.navigate(Dest.STATISTICS) },
                onOpenWorkloadReport = { navController.navigate(Dest.WORKLOAD_REPORT) },
                onOpenWorkloadReview = { navController.navigate(Dest.WORKLOAD_REVIEW) },
                onOpenApprovalTodo = { navController.navigate(Dest.APPROVAL_TODO) },
                onOpenTransferLogs = { navController.navigate(Dest.TRANSFER_LOGS) }
            )
        }
        composable(Screen.Project.route) {
            ProjectScreen(
                onOpenDetail = { projectId ->
                    navController.navigate(Dest.projectDetail(projectId))
                }
            )
        }
        composable(Screen.Notification.route) { NotificationScreen() }
        composable(Screen.Mine.route) {
            MineScreen(
                onOpenTasks = { navController.navigate(Dest.MY_TASKS) },
                onOpenSubmissions = { navController.navigate(Dest.MY_SUBMISSIONS) },
                onOpenArchive = { navController.navigate(Dest.ARCHIVE) },
                onOpenSecurity = { navController.navigate(Dest.ACCOUNT_SECURITY) },
                onOpenProfile = { navController.navigate(Dest.PROFILE) },
                onOpenNotifySettings = { navController.navigate(Dest.NOTIFY_SETTINGS) },
                onOpenAbout = { navController.navigate(Dest.ABOUT) }
            )
        }

        // ===== 项目详情 =====
        composable(
            route = Dest.PROJECT_DETAIL,
            arguments = listOf(
                navArgument(Dest.ARG_PROJECT_ID) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong(Dest.ARG_PROJECT_ID)
                ?: return@composable
            ProjectDetailScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() }
            )
        }

        // ===== 任务与进度填报流 =====
        composable(Dest.MY_TASKS) {
            MyTasksScreen(
                onBack = { navController.popBackStack() },
                onReport = { taskId -> navController.navigate(Dest.progressReport(taskId)) }
            )
        }
        composable(
            route = Dest.PROGRESS_REPORT,
            arguments = listOf(
                navArgument(Dest.ARG_TASK_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getLong(Dest.ARG_TASK_ID) ?: -1L
            ProgressReportScreen(
                taskId = taskId.takeIf { it > 0 },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Dest.MY_SUBMISSIONS) {
            MySubmissionsScreen(onBack = { navController.popBackStack() })
        }
        composable(Dest.REVIEW) {
            ReviewScreen(onBack = { navController.popBackStack() })
        }
        composable(Dest.WORKLOAD_REPORT) {
            WorkloadReportScreen(onBack = { navController.popBackStack() })
        }
        composable(Dest.WORKLOAD_REVIEW) {
            WorkloadReviewScreen(onBack = { navController.popBackStack() })
        }
        composable(Dest.APPROVAL_TODO) {
            ApprovalTodoScreen(onBack = { navController.popBackStack() })
        }
        composable(Dest.TRANSFER_LOGS) {
            TransferLogsScreen(onBack = { navController.popBackStack() })
        }

        // ===== 资料与记录 =====
        composable(Dest.DAILY_REPORT) {
            DailyReportScreen(onBack = { navController.popBackStack() })
        }
        composable(Dest.DOCUMENTS) {
            DocumentScreen(onBack = { navController.popBackStack() })
        }
        composable(Dest.ARCHIVE) {
            ArchiveScreen(onBack = { navController.popBackStack() })
        }
        composable(Dest.CUSTOMER) {
            CustomerScreen(onBack = { navController.popBackStack() })
        }
        composable(Dest.STATISTICS) {
            StatisticsScreen(onBack = { navController.popBackStack() })
        }

        // ===== 个人区 =====
        composable(Dest.PROFILE) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
        composable(Dest.ACCOUNT_SECURITY) {
            AccountSecurityScreen(onBack = { navController.popBackStack() })
        }
        composable(Dest.NOTIFY_SETTINGS) {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Dest.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Tab 间切换：保留各 Tab 状态，避免重复入栈 */
internal fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
