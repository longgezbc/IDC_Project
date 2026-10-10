package com.example.hbsz_app_android.ui.notification

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.common.formatDateTime
import com.example.hbsz_app_android.data.remote.dto.NotificationDto
import com.example.hbsz_app_android.data.repository.NotificationCenter
import com.example.hbsz_app_android.data.repository.NotificationRepository
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.notifyTypeTextOf
import com.example.hbsz_app_android.ui.components.notifyTypeToneOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 通知类型筛选（对齐后端 notifyType 枚举） */
private val TYPE_FILTERS = listOf(
    "全部" to null,
    "进度" to "progress",
    "到期" to "task_due",
    "文档" to "doc_update",
    "归档" to "archive",
    "系统" to "system"
)

/** 分页大小（沿用现有默认值） */
private const val PAGE_SIZE = 50

data class NotificationUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val notifications: List<NotificationDto> = emptyList(),
    val currentType: String = "全部",
    val pageNum: Int = 1,            // 已加载的最后一页页码
    val hasMore: Boolean = true,     // 是否还有下一页
    val loadingMore: Boolean = false, // 是否正在加载更多
    val message: String? = null      // 操作失败等轻提示，展示后清除
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val notificationCenter: NotificationCenter
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationUiState())
    val state: StateFlow<NotificationUiState> = _state

    init {
        load("全部")
        // 通知触达专项：WebSocket 实时新通知到达时插入列表头部（免轮询刷新）
        viewModelScope.launch {
            notificationCenter.incomingNotifications.collect { dto ->
                onIncomingNotification(dto)
            }
        }
    }

    /**
     * 实时通知插入：
     * - 当前筛选为"全部"或类型匹配时插到列表头部（id 去重防竞态重复）；
     * - 加载中/出错状态不插入（随后的 load 会拉到该消息）。
     */
    private fun onIncomingNotification(dto: NotificationDto) {
        val cur = _state.value
        if (cur.loading || cur.error != null) return
        if (cur.notifications.any { it.id == dto.id }) return
        val activeType = TYPE_FILTERS.firstOrNull { it.first == cur.currentType }?.second
        if (activeType != null && activeType != dto.notifyType) return // 当前筛选不匹配
        _state.value = cur.copy(notifications = listOf(dto) + cur.notifications)
    }

    fun setTypeFilter(label: String) {
        _state.value = _state.value.copy(currentType = label)
        load(label)
    }

    fun refresh() = load(_state.value.currentType)

    /**
     * 分页代次：筛选/刷新时自增。
     * 进行中的 loadMore 若持旧代次返回，响应作废，防止旧筛选的数据 append 到新列表（竞态）。
     */
    private var loadEpoch = 0L

    /** 重置到第一页加载（筛选 / 刷新时调用） */
    private fun load(label: String) {
        loadEpoch++
        val type = TYPE_FILTERS.firstOrNull { it.first == label }?.second
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, loadingMore = false)
            when (val res = notificationRepository.getNotifications(pageNum = 1, pageSize = PAGE_SIZE, notifyType = type)) {
                is Resource.Success -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        notifications = res.data.records,
                        pageNum = 1,
                        hasMore = PAGE_SIZE < res.data.total.toInt(),
                        loadingMore = false
                    )
                    // 以服务端为准校准全局未读角标（含 WebSocket 断线期间漏掉的消息）
                    notificationCenter.refreshUnreadCount()
                }
                is Resource.Error -> _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    error = res.message
                )
                Resource.Loading -> Unit
            }
        }
    }

    /** 滚动加载更多：请求下一页并追加到列表尾部 */
    fun loadMore() {
        val cur = _state.value
        if (cur.loadingMore || !cur.hasMore || cur.loading) return
        val epoch = loadEpoch
        val type = TYPE_FILTERS.firstOrNull { it.first == cur.currentType }?.second
        viewModelScope.launch {
            if (epoch != loadEpoch) return@launch // 筛选已切换，放弃本次追加
            _state.value = _state.value.copy(loadingMore = true)
            when (val res = notificationRepository.getNotifications(pageNum = cur.pageNum + 1, pageSize = PAGE_SIZE, notifyType = type)) {
                is Resource.Success -> {
                    if (epoch != loadEpoch) { // 响应返回时筛选已变，丢弃过期数据
                        _state.value = _state.value.copy(loadingMore = false)
                        return@launch
                    }
                    val nextPage = cur.pageNum + 1
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        notifications = _state.value.notifications + res.data.records,
                        pageNum = nextPage,
                        hasMore = nextPage * PAGE_SIZE < res.data.total.toInt()
                    )
                }
                is Resource.Error -> {
                    if (epoch != loadEpoch) {
                        _state.value = _state.value.copy(loadingMore = false)
                        return@launch
                    }
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        error = res.message
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** PUT /notifications/{id}/read 标记单条已读（乐观更新，失败回滚并轻提示） */
    fun markAsRead(id: Long) {
        val oldIsRead = _state.value.notifications.firstOrNull { it.id == id }?.isRead ?: return
        // 乐观更新：先本地置为已读
        _state.value = _state.value.copy(
            notifications = _state.value.notifications.map {
                if (it.id == id) it.copy(isRead = 1) else it
            }
        )
        viewModelScope.launch {
            when (notificationRepository.markAsRead(id)) {
                is Resource.Success -> notificationCenter.decrementUnread() // 角标即时 -1
                is Resource.Error -> _state.value = _state.value.copy(
                    // 失败回滚本地已读状态，避免与下次刷新结果不一致
                    notifications = _state.value.notifications.map {
                        if (it.id == id) it.copy(isRead = oldIsRead) else it
                    },
                    message = "标记已读失败，请稍后重试"
                )
                Resource.Loading -> Unit
            }
        }
    }

    /** 清除轻提示（UI 展示后调用） */
    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** PUT /notifications/read-all 全部已读（乐观更新，失败回滚并轻提示） */
    fun markAllAsRead() {
        val old = _state.value.notifications
        _state.value = _state.value.copy(
            notifications = old.map { it.copy(isRead = 1) }
        )
        viewModelScope.launch {
            when (val res = notificationRepository.markAllAsRead()) {
                is Resource.Success -> notificationCenter.refreshUnreadCount() // 全部已读 → 角标归零校准
                is Resource.Error -> _state.value = _state.value.copy(
                    notifications = old, // 失败回滚
                    message = "全部已读失败：${res.message}"
                )
                Resource.Loading -> Unit
            }
        }
    }
}

/**
 * 消息中心 —— 对齐 Web 端 NotificationDrawer：
 * 通知类型标签（进度/到期/文档/归档/系统）+ 未读圆点 + 一键已读。
 * 数据源：GET /notifications（登录即可访问）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    viewModel: NotificationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 操作失败等轻提示：Toast 展示一次后清除
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        val msg = state.message
        if (!msg.isNullOrEmpty()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }
    val unreadCount = state.notifications.count { it.isRead == 0 }
    val listState = rememberLazyListState()
    // 滚动接近底部（剩余 3 项内）时触发加载更多。
    // key 用 lastVisibleIndex + totalItemsCount：短列表追加后若仍全部可见可继续触发（布尔 key 不变化会漏拉）
    val lastVisibleIndex by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }
    LaunchedEffect(lastVisibleIndex, listState.layoutInfo.totalItemsCount) {
        if (lastVisibleIndex >= listState.layoutInfo.totalItemsCount - 3) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "消息中心" + if (unreadCount > 0) " ($unreadCount)" else "",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    if (unreadCount > 0) {
                        IconButton(onClick = { viewModel.markAllAsRead() }) {
                            Icon(
                                Icons.Outlined.DoneAll,
                                contentDescription = "全部已读",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // 类型筛选
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TYPE_FILTERS, key = { it.first }) { (label, _) ->
                    FilterChip(
                        selected = state.currentType == label,
                        onClick = { viewModel.setTypeFilter(label) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            when {
                state.loading -> LoadingState(Modifier.fillMaxSize())
                state.error != null -> ErrorState(
                    message = state.error ?: "加载失败",
                    modifier = Modifier.fillMaxSize(),
                    onRetry = { viewModel.refresh() }
                )
                state.notifications.isEmpty() -> EmptyState(text = "暂无消息")
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.notifications, key = { it.id }) { notification ->
                        NotificationItem(
                            notification = notification,
                            onMarkRead = { viewModel.markAsRead(notification.id) }
                        )
                    }
                    // 列表底部：加载中 / 没有更多
                    item(key = "load_more_footer") {
                        LoadMoreFooter(
                            loadingMore = state.loadingMore,
                            hasMore = state.hasMore,
                            isEmpty = state.notifications.isEmpty()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(notification: NotificationDto, onMarkRead: () -> Unit) {
    val unread = notification.isRead == 0
    Surface(
        onClick = onMarkRead,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 类型图标（未读高亮品牌色）
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (unread) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = null,
                    tint = if (unread) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (unread) {
                        Spacer(Modifier.width(6.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = notification.content ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusTag(
                        text = notifyTypeTextOf(notification.notifyType),
                        tone = notifyTypeToneOf(notification.notifyType)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatDateTime(notification.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 列表底部加载状态：加载更多中显示小圈 + "加载中…"，全部加载完显示 "— 没有更多了 —"。
 */
@Composable
private fun LoadMoreFooter(loadingMore: Boolean, hasMore: Boolean, isEmpty: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            loadingMore -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "加载中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            !hasMore && !isEmpty -> Text(
                text = "— 没有更多了 —",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
