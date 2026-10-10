package com.example.hbsz_app_android.ui.progress

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
import androidx.compose.material.icons.outlined.PhotoCamera
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.common.formatDateTime
import com.example.hbsz_app_android.data.remote.dto.ProgressLogDto
import com.example.hbsz_app_android.data.repository.DashboardRepository
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.reviewStatusTextOf
import com.example.hbsz_app_android.ui.components.reviewStatusToneOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 分页大小（沿用现有默认值） */
private const val PAGE_SIZE = 20

data class MySubmissionsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val submissions: List<ProgressLogDto> = emptyList(),
    val filter: String = "全部",
    val pageNum: Int = 1,            // 已加载的最后一页页码
    val hasMore: Boolean = true,     // 是否还有下一页
    val loadingMore: Boolean = false // 是否正在加载更多
)

@HiltViewModel
class MySubmissionsViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MySubmissionsUiState())
    val state: StateFlow<MySubmissionsUiState> = _state

    init {
        load("全部")
    }

    fun setFilter(filter: String) {
        _state.value = _state.value.copy(filter = filter)
        load(filter)
    }

    fun refresh() = load(_state.value.filter)

    /** 筛选条件 → reviewStatus 查询参数（"全部"不传） */
    private fun reviewStatusOf(filter: String): String? = when (filter) {
        "待审核" -> "pending"
        "已通过" -> "approved"
        "已驳回" -> "rejected"
        else -> null
    }

    /**
     * 分页代次：筛选/刷新时自增。
     * 进行中的 loadMore 若持旧代次返回，响应作废，防止旧筛选的数据 append 到新列表（竞态）。
     */
    private var loadEpoch = 0L

    /** 重置到第一页加载（筛选 / 刷新时调用） */
    private fun load(filter: String) {
        loadEpoch++
        _state.value = _state.value.copy(loading = _state.value.submissions.isEmpty(), error = null, loadingMore = false)
        viewModelScope.launch {
            when (val res = dashboardRepository.getMySubmissions(pageNum = 1, pageSize = PAGE_SIZE, reviewStatus = reviewStatusOf(filter))) {
                is Resource.Success -> _state.value = _state.value.copy(
                    loading = false,
                    submissions = res.data.records,
                    pageNum = 1,
                    hasMore = PAGE_SIZE < res.data.total.toInt(),
                    loadingMore = false
                )

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
        viewModelScope.launch {
            if (epoch != loadEpoch) return@launch // 筛选已切换，放弃本次追加
            _state.value = _state.value.copy(loadingMore = true)
            when (val res = dashboardRepository.getMySubmissions(pageNum = cur.pageNum + 1, pageSize = PAGE_SIZE, reviewStatus = reviewStatusOf(cur.filter))) {
                is Resource.Success -> {
                    if (epoch != loadEpoch) { // 响应返回时筛选已变，丢弃过期数据
                        _state.value = _state.value.copy(loadingMore = false)
                        return@launch
                    }
                    val nextPage = cur.pageNum + 1
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        submissions = _state.value.submissions + res.data.records,
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
 * 填报记录页 —— 数据来自 GET /dashboard/my-submissions（真实后端接口）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySubmissionsScreen(
    onBack: () -> Unit = {},
    viewModel: MySubmissionsViewModel = hiltViewModel()
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
                title = { Text("填报记录", fontWeight = FontWeight.SemiBold) },
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
                items(listOf("全部", "待审核", "已通过", "已驳回")) { option ->
                    FilterChip(
                        selected = state.filter == option,
                        onClick = { viewModel.setFilter(option) },
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

                state.submissions.isEmpty() -> EmptyState(text = "暂无${state.filter}的填报")

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.submissions, key = { it.id }) { SubmissionCard(it) }
                    // 列表底部：加载中 / 没有更多
                    item(key = "load_more_footer") {
                        LoadMoreFooter(
                            loadingMore = state.loadingMore,
                            hasMore = state.hasMore,
                            isEmpty = state.submissions.isEmpty()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmissionCard(submission: ProgressLogDto) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = submission.taskName ?: "任务",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = submission.projectName ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusTag(
                    text = reviewStatusTextOf(submission.reviewStatus),
                    tone = reviewStatusToneOf(submission.reviewStatus)
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "上报进度",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                LinearProgressIndicator(
                    progress = { submission.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.weight(1f).height(6.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${submission.progress}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!submission.remark.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = submission.remark,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 驳回/审核意见
            submission.reviewRemark?.takeIf { it.isNotBlank() }?.let { comment ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "审核意见：$comment",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (submission.reviewStatus == "rejected") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${submission.images.size} 张施工照片",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatDateTime(submission.reportTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
