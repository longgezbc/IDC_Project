package com.example.hbsz_app_android.ui.project

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.common.formatDateTime
import com.example.hbsz_app_android.data.remote.dto.PageResult
import com.example.hbsz_app_android.data.remote.dto.TransferLogDto
import com.example.hbsz_app_android.data.repository.ProjectAuthRepository
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.PullRefreshBox
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.actionTextOf
import com.example.hbsz_app_android.ui.components.actionToneOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 操作类型筛选（对齐 Web TransferLogs 筛选项；raw → 后端 actionType 参数） */
private val ACTION_FILTERS = listOf(
    null to "全部",
    "transfer" to "转移",
    "transfer_leader" to "负责人移交",
    "assign" to "直接授权",
    "revoke" to "撤销",
    "auth_request" to "授权申请",
    "approve" to "审批通过",
    "reject" to "审批驳回"
)

/** 分页大小 */
private const val PAGE_SIZE = 20

data class TransferLogsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val logs: List<TransferLogDto> = emptyList(),
    /** 当前操作类型筛选（null = 全部） */
    val actionType: String? = null,
    val pageNum: Int = 1,
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false
)

@HiltViewModel
class TransferLogsViewModel @Inject constructor(
    private val projectAuthRepository: ProjectAuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(TransferLogsUiState())
    val state: StateFlow<TransferLogsUiState> = _state

    init {
        refresh()
    }

    /** 重置到第一页（进入页面 / 切换筛选 / 下拉刷新） */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (
                val res = projectAuthRepository.getTransferLogs(
                    pageNum = 1,
                    pageSize = PAGE_SIZE,
                    actionType = _state.value.actionType
                )
            ) {
                is Resource.Success -> {
                    val page = res.data
                    _state.update {
                        it.copy(
                            loading = false,
                            logs = page.records,
                            pageNum = 1,
                            hasMore = computeHasMore(page, 1)
                        )
                    }
                }

                is Resource.Error -> _state.update {
                    it.copy(loading = false, error = res.message)
                }

                Resource.Loading -> Unit
            }
        }
    }

    /** 切换操作类型筛选 */
    fun setActionType(actionType: String?) {
        if (actionType == _state.value.actionType) return
        _state.update { it.copy(actionType = actionType) }
        refresh()
    }

    /** 是否还有下一页（pages=0 时按 total 估算兜底） */
    private fun computeHasMore(page: PageResult<TransferLogDto>, loadedPage: Int): Boolean =
        if (page.pages > 0) loadedPage < page.pages
        else loadedPage * PAGE_SIZE < page.total

    /** 滚动到底部加载下一页 */
    fun loadMore() {
        val cur = _state.value
        if (cur.loadingMore || !cur.hasMore || cur.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            when (
                val res = projectAuthRepository.getTransferLogs(
                    pageNum = cur.pageNum + 1,
                    pageSize = PAGE_SIZE,
                    actionType = cur.actionType
                )
            ) {
                is Resource.Success -> {
                    val page = res.data
                    val next = cur.pageNum + 1
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            // distinctById 兜底防重复项
                            logs = (it.logs + page.records).distinctBy { log -> log.id },
                            pageNum = next,
                            hasMore = computeHasMore(page, next)
                        )
                    }
                }

                is Resource.Error -> _state.update {
                    it.copy(loadingMore = false, error = res.message)
                }

                Resource.Loading -> Unit
            }
        }
    }
}

/**
 * 转移/授权审计日志页 —— 对齐 Web 端 TransferLogs.vue 的移动端形态：
 * 操作类型筛选 Chips + 日志卡片（操作类型标签、原→目标工程师、操作人、时间、备注）。
 * 数据源：GET /projects/authorization/logs（project:query 权限，
 * 非 admin/PM 用户由后端限定可见范围，无 projectId 时返回空列表）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferLogsScreen(
    onBack: () -> Unit = {},
    viewModel: TransferLogsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("转移日志", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ===== 操作类型筛选 Chips =====
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ACTION_FILTERS, key = { it.second }) { (raw, label) ->
                    FilterChip(
                        selected = state.actionType == raw,
                        onClick = { viewModel.setActionType(raw) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ===== 日志列表（下拉刷新 + 触底加载更多） =====
            PullRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    state.loading && state.logs.isEmpty() -> LoadingState(Modifier.fillMaxSize())
                    state.error != null -> ErrorState(
                        message = state.error ?: "加载失败",
                        modifier = Modifier.fillMaxSize(),
                        onRetry = { viewModel.refresh() }
                    )

                    state.logs.isEmpty() -> EmptyState(text = "暂无转移日志")

                    else -> {
                        val listState = rememberLazyListState()
                        // 滚动至列表末尾（剩余 3 项内）时自动加载下一页
                        val lastVisibleIndex by remember {
                            derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                        }
                        LaunchedEffect(lastVisibleIndex, listState.layoutInfo.totalItemsCount) {
                            if (lastVisibleIndex >= listState.layoutInfo.totalItemsCount - 3) viewModel.loadMore()
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.logs, key = { it.id ?: it.hashCode().toLong() }) { log ->
                                TransferLogCard(log)
                            }
                            if (state.loadingMore) {
                                item(key = "loading_more") { LoadMoreFooter(loading = true) }
                            } else if (!state.hasMore && state.logs.isNotEmpty()) {
                                item(key = "no_more") { LoadMoreFooter(loading = false) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 日志卡片 —— 信息对齐 Web TransferLogs 表格列：
 * 操作类型标签 + 项目名 + 原→目标工程师 + 操作人 + 时间 + 备注。
 * operatedBy 为 null 时（系统自动操作，如授权过期定时回收）operatorName 为"系统定时任务"。
 */
@Composable
private fun TransferLogCard(log: TransferLogDto) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusTag(
                    text = actionTextOf(log.actionType),
                    tone = actionToneOf(log.actionType)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = log.projectName ?: "未知项目",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = log.operatedAt?.let { formatDateTime(it) } ?: "-",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 原 → 目标工程师（任一存在时展示）
            val from = log.fromEngineerName
            val to = log.toEngineerName
            if (from != null || to != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append(from ?: "无")
                        append(" → ")
                        append(to ?: "无")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "操作人：${log.operatorName ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            log.remark?.takeIf { it.isNotBlank() }?.let { remark ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = remark,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 列表底部分页 footer：加载中 / 没有更多 */
@Composable
private fun LoadMoreFooter(loading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "加载中…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "— 没有更多了 —",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
