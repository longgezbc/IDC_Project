package com.example.hbsz_app_android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.Grading
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.common.formatDate
import com.example.hbsz_app_android.data.local.UserPreferences
import com.example.hbsz_app_android.data.remote.dto.DashboardSummaryDto
import com.example.hbsz_app_android.data.remote.dto.NotificationDto
import com.example.hbsz_app_android.data.repository.DashboardRepository
import com.example.hbsz_app_android.data.repository.NotificationRepository
import com.example.hbsz_app_android.ui.components.SectionHeader
import com.example.hbsz_app_android.ui.components.StatCard
import com.example.hbsz_app_android.ui.components.blueprintGrid
import com.example.hbsz_app_android.ui.components.notifyTypeToneOf
import com.example.hbsz_app_android.ui.theme.Danger
import com.example.hbsz_app_android.ui.theme.Info
import com.example.hbsz_app_android.ui.theme.Primary
import com.example.hbsz_app_android.ui.theme.PrimaryDark
import com.example.hbsz_app_android.ui.theme.Success
import com.example.hbsz_app_android.ui.theme.Warning
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val summaryLoading: Boolean = true,
    val summary: DashboardSummaryDto? = null,
    val summaryError: String? = null,
    val recentNotifications: List<NotificationDto> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    userPreferences: UserPreferences,
    private val dashboardRepository: DashboardRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    val username: StateFlow<String> = userPreferences.usernameFlow
        .map { it ?: "用户" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "用户")

    /** 是否可审核（PM / admin，对齐小程序端 canReviewProgress 角色判断） */
    val canReview: StateFlow<Boolean> = userPreferences.rolesFlow
        .map { roles -> roles.contains("project_manager") || roles.contains("admin") }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(summaryLoading = it.summary == null) }
        viewModelScope.launch {
            when (val res = dashboardRepository.getSummary()) {
                is Resource.Success -> _state.update {
                    it.copy(
                        summaryLoading = false,
                        summary = res.data,
                        summaryError = null
                    )
                }

                is Resource.Error -> _state.update {
                    it.copy(
                        summaryLoading = false,
                        summaryError = res.message
                    )
                }

                Resource.Loading -> Unit
            }
        }
        viewModelScope.launch {
            when (val res = notificationRepository.getNotifications(pageSize = 5)) {
                is Resource.Success -> _state.update {
                    it.copy(recentNotifications = res.data.records)
                }

                else -> Unit
            }
        }
    }
}

/**
 * 功能宫格入口：标题 + 图标 + 语义色（对齐 Web 端 soft color icon system，
 * 四色系：蓝=常规操作 / 绿=进展与消息 / 橙=提醒类 / 红=审批类）。
 */
private data class Feature(val title: String, val icon: ImageVector, val tint: Color)

/** 通知类型 → 图标（与 notifyTypeTextOf 的类型一一对应，未知类型用通用通知图标） */
private fun notifyTypeIconOf(type: String?): ImageVector = when (type) {
    "progress" -> Icons.Outlined.Timeline
    "task_due" -> Icons.Outlined.WarningAmber
    "doc_update" -> Icons.Outlined.Description
    "archive" -> Icons.Outlined.Archive
    "system" -> Icons.Outlined.Info
    else -> Icons.Outlined.NotificationsActive
}

/**
 * 工作台首页 —— 对齐 Web 端 Dashboard 与小程序工作台的信息架构：
 * 品牌问候区 → 关键指标（统计卡）→ 常用功能宫格 → 最近动态。
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onOpenProject: () -> Unit = {},
    onOpenNotification: () -> Unit = {},
    onOpenReport: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenDocuments: () -> Unit = {},
    onOpenArchive: () -> Unit = {},
    onOpenDailyReport: () -> Unit = {},
    onOpenReview: () -> Unit = {},
    onOpenCustomer: () -> Unit = {},
    onOpenStatistics: () -> Unit = {},
    onOpenWorkloadReport: () -> Unit = {},
    onOpenWorkloadReview: () -> Unit = {},
    onOpenApprovalTodo: () -> Unit = {},
    onOpenTransferLogs: () -> Unit = {}
) {
    val username by viewModel.username.collectAsStateWithLifecycle()
    val canReview by viewModel.canReview.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.summary

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== 品牌问候区（深→浅品牌渐变 + 工程蓝图网格纹理，对齐 Web Banner） =====
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .blueprintGrid()
                    .background(
                        Brush.verticalGradient(
                            listOf(PrimaryDark, Primary)
                        )
                    )
                    .padding(horizontal = 20.dp)
                    .padding(top = 48.dp, bottom = 40.dp)
            ) {
                Text(
                    text = "IDC 项目管家",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "你好，$username",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "今天也要按时完成施工任务哦",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // ===== 关键指标：上叠统计卡（对齐 Web Dashboard 统计卡，点击进入统计分析） =====
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                StatsGrid(
                    stats = stats,
                    onOpenDetail = onOpenStatistics,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .offset(y = (-28).dp),
                    // 统计刷新失败时在卡片区域顶部给出轻提示：有缓存数据也提示，避免静默展示陈旧数据
                    error = state.summaryError
                )
            }
        }

        // ===== 常用功能宫格（对齐小程序工作台快捷入口） =====
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(title = "常用功能")
                Spacer(Modifier.height(4.dp))
                val features = buildList {
                    add(Feature("进度填报", Icons.Outlined.EditNote, Primary) to onOpenReport)
                    add(Feature("任务进度", Icons.Outlined.Timeline, Success) to onOpenTasks)
                    add(Feature("项目资料", Icons.Outlined.FolderShared, Warning) to onOpenDocuments)
                    add(Feature("项目归档", Icons.Outlined.Archive, Info) to onOpenArchive)
                    add(Feature("施工日报", Icons.Outlined.Description, Primary) to onOpenDailyReport)
                    add(Feature("客户管理", Icons.Outlined.BusinessCenter, Success) to onOpenCustomer)
                    add(Feature("审批", Icons.Outlined.PendingActions, Danger) to onOpenReview)
                    add(Feature("消息", Icons.Outlined.NotificationsActive, Warning) to onOpenNotification)
                    // 工作量填报：项目组成员每日填报（全员可见）
                    add(Feature("工作量填报", Icons.Outlined.PostAdd, Primary) to onOpenWorkloadReport)
                    // 工作量审核 / 审批中心 / 转移日志：仅 PM / admin 可见（对齐小程序端 canReview）
                    if (canReview) {
                        add(Feature("工作量审核", Icons.AutoMirrored.Outlined.Grading, Success) to onOpenWorkloadReview)
                        add(Feature("审批中心", Icons.AutoMirrored.Outlined.FactCheck, Danger) to onOpenApprovalTodo)
                        add(Feature("转移日志", Icons.Outlined.SwapHoriz, Info) to onOpenTransferLogs)
                    }
                }
                FeatureGrid(features = features)
            }
        }

        // ===== 最近动态（真实站内通知） =====
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(title = "最近动态")
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        if (state.recentNotifications.isEmpty()) {
                            Text(
                                text = "暂无最新动态",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        } else {
                            state.recentNotifications.forEachIndexed { index, notification ->
                                NotificationBriefRow(notification, onClick = onOpenNotification)
                                if (index < state.recentNotifications.lastIndex) {
                                    Box(
                                        Modifier
                                            .padding(start = 52.dp)
                                            .height(1.dp)
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun StatsGrid(
    stats: DashboardSummaryDto?,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null
) {
    val loading = stats == null
    val pendingText = if (loading) "-" else stats.myPendingTaskCount.toString()
    val overdueText = if (loading) "-" else stats.myOverdueTaskCount.toString()
    val reviewText = if (loading) "-" else stats.pendingReviewCount.toString()
    val unreadText = if (loading) "-" else stats.unreadNotificationCount.toString()

    Surface(
        onClick = onOpenDetail,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            // 加载失败轻提示（不阻断布局，指标回退展示 "-"）
            if (error != null) {
                Text(
                    text = "数据加载失败：$error",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    label = "待办任务", value = pendingText,
                    icon = Icons.Outlined.Timeline,
                    iconBg = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "逾期任务", value = overdueText,
                    icon = Icons.Outlined.WarningAmber,
                    iconBg = Danger,
                    modifier = Modifier.weight(1f),
                    trend = if (loading) null else "需关注", trendUp = false
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    label = "待审核填报", value = reviewText,
                    icon = Icons.Outlined.PendingActions,
                    iconBg = Warning,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "未读消息", value = unreadText,
                    icon = Icons.Outlined.NotificationsActive,
                    iconBg = Success,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 功能宫格：4 列自动分行，入口与对应功能页一一联通。
 * 图标底衬用语义色 12% 透明度（软色处理，对齐 StatCard 与 Web soft color icon system），
 * 图标本体用语义色，四色分布提升功能辨识度。
 */
@Composable
private fun FeatureGrid(features: List<Pair<Feature, () -> Unit>>) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            features.chunked(4).forEach { rowFeatures ->
                Row(Modifier.fillMaxWidth()) {
                    rowFeatures.forEach { (feature, onClick) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onClick)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(feature.tint.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = feature.icon,
                                    contentDescription = feature.title,
                                    tint = feature.tint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = feature.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 最近动态行 —— 头像按通知类型语义化（图标 + 类型色调底衬），
 * 对齐 Web 端通知列表的类型标签配色（notifyTypeToneOf 同源色调）。
 */
@Composable
private fun NotificationBriefRow(notification: NotificationDto, onClick: () -> Unit) {
    val tone = notifyTypeToneOf(notification.notifyType)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tone.container.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = notifyTypeIconOf(notification.notifyType),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = tone.content
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = notification.content ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDate(notification.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
