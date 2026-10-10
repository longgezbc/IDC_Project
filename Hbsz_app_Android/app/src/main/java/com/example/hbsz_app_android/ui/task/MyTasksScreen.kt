package com.example.hbsz_app_android.ui.task

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.hbsz_app_android.common.formatDate
import com.example.hbsz_app_android.data.remote.dto.MyTaskItemDto
import com.example.hbsz_app_android.data.repository.DashboardRepository
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.TagTone
import com.example.hbsz_app_android.ui.components.statusToneOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 分页大小（沿用现有默认值） */
private const val PAGE_SIZE = 20

/** 任务列表页状态 */
data class MyTasksUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val tasks: List<MyTaskItemDto> = emptyList(),
    val statusOptions: List<String> = listOf("全部"),
    val statusFilter: String = "全部",
    val pageNum: Int = 1,            // 已加载的最后一页页码
    val hasMore: Boolean = true,     // 是否还有下一页
    val loadingMore: Boolean = false // 是否正在加载更多
)

@HiltViewModel
class MyTasksViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MyTasksUiState())
    val state: StateFlow<MyTasksUiState> = _state

    init {
        refresh()
    }

    fun setStatusFilter(filter: String) {
        _state.value = _state.value.copy(statusFilter = filter)
        load(filter)
    }

    fun refresh() = load(_state.value.statusFilter)

    /**
     * 分页代次：筛选/刷新时自增。
     * 进行中的 loadMore 若持旧代次返回，响应作废，防止旧筛选的数据 append 到新列表（竞态）。
     */
    private var loadEpoch = 0L

    /** 重置到第一页加载（筛选 / 刷新时调用） */
    private fun load(statusFilter: String) {
        loadEpoch++
        val status = statusFilter.takeIf { it != "全部" }
        _state.value = _state.value.copy(loading = _state.value.tasks.isEmpty(), error = null, loadingMore = false)
        viewModelScope.launch {
            when (val res = dashboardRepository.getMyTasks(pageNum = 1, pageSize = PAGE_SIZE, status = status)) {
                is Resource.Success -> {
                    val records = res.data.records
                    // 筛选选项与已有选项取并集：切到"已完成"后"进行中"等选项不会从筛选栏消失
                    val options = (listOf("全部") + _state.value.statusOptions + records.map { it.status }).distinct()
                    _state.value = _state.value.copy(
                        loading = false,
                        error = null,
                        tasks = records,
                        statusOptions = options,
                        pageNum = 1,
                        hasMore = PAGE_SIZE < res.data.total.toInt(),
                        loadingMore = false
                    )
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
        val status = cur.statusFilter.takeIf { it != "全部" }
        viewModelScope.launch {
            if (epoch != loadEpoch) return@launch // 筛选已切换，放弃本次追加
            _state.value = _state.value.copy(loadingMore = true)
            when (val res = dashboardRepository.getMyTasks(pageNum = cur.pageNum + 1, pageSize = PAGE_SIZE, status = status)) {
                is Resource.Success -> {
                    if (epoch != loadEpoch) { // 响应返回时筛选已变，丢弃过期数据
                        _state.value = _state.value.copy(loadingMore = false)
                        return@launch
                    }
                    val nextPage = cur.pageNum + 1
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        tasks = _state.value.tasks + res.data.records,
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
}

/**
 * 我的任务页 —— 数据来自 GET /dashboard/my-tasks（真实后端接口）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    onBack: () -> Unit = {},
    onReport: (Long) -> Unit = {},
    viewModel: MyTasksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                title = { Text("我的任务", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.statusOptions) { option ->
                    FilterChip(
                        selected = state.statusFilter == option,
                        onClick = { viewModel.setStatusFilter(option) },
                        label = { Text(option) }
                    )
                }
            }

            when {
                state.loading -> LoadingState()

                state.error != null -> ErrorState(
                    message = state.error ?: "加载失败",
                    onRetry = { viewModel.refresh() }
                )

                state.tasks.isEmpty() -> EmptyState(
                    text = "暂无${if (state.statusFilter == "全部") "" else state.statusFilter}任务"
                )

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskCard(task = task, onClick = { onReport(task.id) })
                    }
                    // 列表底部：加载中 / 没有更多
                    item(key = "load_more_footer") {
                        LoadMoreFooter(
                            loadingMore = state.loadingMore,
                            hasMore = state.hasMore,
                            isEmpty = state.tasks.isEmpty()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: MyTaskItemDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusTag(text = task.status, tone = statusToneOf(task.status))
            }
            if (!task.projectName.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = task.projectName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { task.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.isMilestone == 1) {
                    StatusTag(text = "里程碑", tone = TagTone.INFO)
                    Spacer(Modifier.width(6.dp))
                }
                if (task.isKeyTask == 1) {
                    StatusTag(text = "关键任务", tone = TagTone.DANGER)
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "截止 ${formatDate(task.endDate)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (task.status != "已完成") {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "去填报",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(18.dp)
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
