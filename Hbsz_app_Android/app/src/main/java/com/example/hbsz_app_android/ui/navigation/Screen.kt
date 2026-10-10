package com.example.hbsz_app_android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航目的地定义。
 * 二级页（详情/表单/设置等）见 [Dest]，不进入底部导航。
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "首页", Icons.Outlined.Home)
    data object Project : Screen("project", "项目", Icons.Outlined.BusinessCenter)
    data object Notification : Screen("notification", "消息", Icons.Outlined.Notifications)
    data object Mine : Screen("mine", "我的", Icons.Outlined.Person)
}

/** 二级页路由：业务功能页 + 个人区页面 */
object Dest {
    const val PROJECT_DETAIL = "project_detail/{projectId}"
    const val MY_TASKS = "my_tasks"
    const val PROGRESS_REPORT = "progress_report?taskId={taskId}"
    const val MY_SUBMISSIONS = "my_submissions"
    const val REVIEW = "review"
    const val WORKLOAD_REPORT = "workload_report"
    const val WORKLOAD_REVIEW = "workload_review"
    const val APPROVAL_TODO = "approval_todo"
    const val TRANSFER_LOGS = "transfer_logs"
    const val DAILY_REPORT = "daily_report"
    const val DOCUMENTS = "documents"
    const val ARCHIVE = "archive"
    const val CUSTOMER = "customer"
    const val STATISTICS = "statistics"
    const val PROFILE = "profile"
    const val ACCOUNT_SECURITY = "account_security"
    const val NOTIFY_SETTINGS = "notify_settings"
    const val ABOUT = "about"

    const val ARG_PROJECT_ID = "projectId"
    const val ARG_TASK_ID = "taskId"

    fun projectDetail(projectId: Long) = "project_detail/$projectId"
    fun progressReport(taskId: Long? = null) =
        if (taskId == null) "progress_report" else "progress_report?taskId=$taskId"
}
