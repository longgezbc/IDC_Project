package com.example.hbsz_app_android.ui.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.data.remote.dto.ProjectDto
import com.example.hbsz_app_android.data.repository.ProjectRepository
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.SkeletonList
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.statusToneOf
import com.example.hbsz_app_android.ui.components.statusTextOf
import com.example.hbsz_app_android.ui.theme.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 状态筛选选项（对齐 Web ProjectList 状态筛选 + statusMap） */
private val STATUS_FILTERS = listOf("全部", "进行中", "未开始", "已完成", "已延期", "已暂停")

/** 筛选条件 → 后端 status 查询参数（后端存中文状态值，"全部"不传） */
private fun statusParamOf(filter: String): String? =
    filter.takeIf { it != "全部" }

/** 分页大小（沿用现有默认值） */
private const val PAGE_SIZE = 20

data class ProjectListUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val projects: List<ProjectDto> = emptyList(),
    val pageNum: Int = 1,            // 已加载的最后一页页码
    val hasMore: Boolean = true,     // 是否还有下一页
    val loadingMore: Boolean = false // 是否正在加载更多
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ProjectListUiState())
    val state: StateFlow<ProjectListUiState> = _state

    private var keyword: String = ""
    private var currentFilter: String = "全部"

    /** 关键字搜索（带 300ms 防抖） */
    private val keywordFlow = MutableStateFlow("")

    init {
        load("全部")
        viewModelScope.launch {
            keywordFlow.debounce(300).collect { kw ->
                if (kw.trim() != keyword) {
                    keyword = kw.trim()
                    load(currentFilter)
                }
            }
        }
    }

    fun setKeyword(kw: String) {
        keywordFlow.value = kw
    }

    fun setStatusFilter(filter: String) {
        currentFilter = filter
        load(filter)
    }

    fun refresh() = load(currentFilter)

    /**
     * 分页代次：筛选/搜索/刷新时自增。
     * 进行中的 loadMore 若持旧代次返回，响应作废，防止旧筛选的数据 append 到新列表（竞态）。
     */
    private var loadEpoch = 0L

    /** 重置到第一页加载（搜索 / 筛选 / 刷新时调用） */
    private fun load(filter: String) {
        loadEpoch++
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, loadingMore = false)
            val kw = keyword.trim().takeIf { it.isNotEmpty() }
            when (val res = projectRepository.getProjects(pageNum = 1, pageSize = PAGE_SIZE, keyword = kw, status = statusParamOf(filter))) {
                is Resource.Success -> _state.value = _state.value.copy(
                    loading = false,
                    projects = res.data.records,
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
            if (epoch != loadEpoch) return@launch // 筛选/搜索已切换，放弃本次追加
            _state.value = _state.value.copy(loadingMore = true)
            val kw = keyword.trim().takeIf { it.isNotEmpty() }
            when (val res = projectRepository.getProjects(pageNum = cur.pageNum + 1, pageSize = PAGE_SIZE, keyword = kw, status = statusParamOf(currentFilter))) {
                is Resource.Success -> {
                    if (epoch != loadEpoch) { // 响应返回时筛选已变，丢弃过期数据
                        _state.value = _state.value.copy(loadingMore = false)
                        return@launch
                    }
                    val nextPage = cur.pageNum + 1
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        projects = _state.value.projects + res.data.records,
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
 * 项目列表页 —— 对齐 Web 端 ProjectList.vue 的移动端形态：
 * 搜索框 + 状态筛选 Chips + 项目卡片（状态标签、进度条、经理、地址、工期）。
 * 数据源：GET /projects（project:query 权限）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    onOpenDetail: (Long) -> Unit = {},
    viewModel: ProjectListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var keyword by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("全部") }
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
                title = { Text("项目管理", fontWeight = FontWeight.SemiBold) },
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
            // ===== 搜索栏 =====
            OutlinedTextField(
                value = keyword,
                onValueChange = {
                    keyword = it
                    viewModel.setKeyword(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索项目名称 / 编号 / 客户") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            // ===== 状态筛选 Chips =====
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(STATUS_FILTERS) { status ->
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = {
                            statusFilter = status
                            viewModel.setStatusFilter(status)
                        },
                        label = { Text(status) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ===== 项目卡片列表 =====
            when {
                // 首屏加载用骨架屏（预占卡片位，避免内容跳变）
                state.loading -> SkeletonList(count = 4)
                state.error != null -> ErrorState(
                    message = state.error ?: "加载失败",
                    modifier = Modifier.fillMaxSize(),
                    onRetry = { viewModel.refresh() }
                )
                state.projects.isEmpty() -> EmptyState(text = "暂无项目数据")
                else -> LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onOpenDetail(project.id.toLongOrNull() ?: -1L) }
                        )
                    }
                    // 列表底部：加载中 / 没有更多
                    item(key = "load_more_footer") {
                        LoadMoreFooter(
                            loadingMore = state.loadingMore,
                            hasMore = state.hasMore,
                            isEmpty = state.projects.isEmpty()
                        )
                    }
                }
            }
        }
    }
}

/**
 * 项目卡片 —— 信息层级对齐 Web 端卡片视图：
 * 标题行（状态标签）→ 编号/客户 → 进度条 → 经理/地址/工期。
 * shape 统一 shapes.medium（与其他列表卡片一致）。
 */
@Composable
private fun ProjectCard(project: ProjectDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // 标题 + 标签
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                StatusTag(text = statusTextOf(project.status), tone = statusToneOf(project.status))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = listOfNotNull(
                    project.code?.takeIf { it.isNotBlank() },
                    project.customerName?.takeIf { it.isNotBlank() }
                ).joinToString(" · ").ifEmpty { "-" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // 进度条
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { project.progress / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp),
                    color = if (project.status == "已延期" || project.status == "delayed")
                        MaterialTheme.colorScheme.error else Success,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${project.progress}%",
                    // tnum 等宽数字：百分比数值宽度稳定，进度条行不跳动
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))

            // 元信息：经理 / 地址 / 工期
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaItem(Icons.Outlined.Person, project.manager ?: "未指派")
                Spacer(Modifier.width(14.dp))
                MetaItem(Icons.Outlined.LocationOn, project.address ?: "-", maxChars = 8)
                Spacer(Modifier.weight(1f))
                MetaItem(
                    Icons.Outlined.Schedule,
                    "${project.startDate?.take(10) ?: "-"} ~ ${project.endDate?.take(10) ?: "-"}"
                )
            }
        }
    }
}

@Composable
private fun MetaItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, maxChars: Int = Int.MAX_VALUE) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = if (text.length > maxChars) text.take(maxChars) + "…" else text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
