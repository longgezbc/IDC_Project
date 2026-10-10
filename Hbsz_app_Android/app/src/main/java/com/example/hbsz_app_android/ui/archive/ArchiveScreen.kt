package com.example.hbsz_app_android.ui.archive

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.common.formatAmount
import com.example.hbsz_app_android.data.remote.dto.ArchiveStatisticsDto
import com.example.hbsz_app_android.data.remote.dto.ProjectDto
import com.example.hbsz_app_android.data.repository.ProjectRepository
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.StatCard
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.TagTone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ==================== ViewModel ====================

data class ArchiveUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val statistics: ArchiveStatisticsDto? = null,
    val projects: List<ProjectDto> = emptyList(),
    /** 分页状态：当前页码 / 是否还有更多 / 是否正在加载下一页 */
    val pageNum: Int = 1,
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false
)

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ArchiveUiState())
    val state: StateFlow<ArchiveUiState> = _state

    companion object {
        private const val PAGE_SIZE = 20
    }

    init { load() }

    fun refresh() = load()

    /** 重置到第一页并整体替换列表（统计信息随首页一起加载） */
    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val listRes = projectRepository.getArchivedProjects(pageNum = 1, pageSize = PAGE_SIZE)
            val statsRes = projectRepository.getArchiveStatistics()
            when (listRes) {
                is Resource.Success -> _state.value = _state.value.copy(
                    loading = false,
                    projects = listRes.data.records,
                    statistics = (statsRes as? Resource.Success)?.data,
                    pageNum = 1,
                    hasMore = PAGE_SIZE < listRes.data.total
                )
                is Resource.Error -> _state.value = _state.value.copy(
                    loading = false,
                    error = listRes.message
                )
                Resource.Loading -> Unit
            }
        }
    }

    /** 加载下一页，成功后追加到列表尾部 */
    fun loadMore() {
        val cur = _state.value
        if (cur.loadingMore || !cur.hasMore || cur.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true)
            val nextPage = _state.value.pageNum + 1
            when (val res = projectRepository.getArchivedProjects(pageNum = nextPage, pageSize = PAGE_SIZE)) {
                is Resource.Success -> _state.value = _state.value.copy(
                    loadingMore = false,
                    projects = _state.value.projects + res.data.records,
                    pageNum = nextPage,
                    hasMore = nextPage * PAGE_SIZE < res.data.total
                )
                is Resource.Error -> _state.value = _state.value.copy(loadingMore = false)
                Resource.Loading -> Unit
            }
        }
    }
}

// ==================== UI ====================

/**
 * 归档库页 —— 对接后端 GET /projects/archived + GET /projects/archived/statistics：
 * 顶部归档统计卡（总数/合同总额/本年/本月），下方已归档项目列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    onBack: () -> Unit = {},
    viewModel: ArchiveViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("归档库 (${state.statistics?.totalCount ?: state.projects.size})", fontWeight = FontWeight.SemiBold) },
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
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(
                Modifier.padding(padding),
                message = state.error ?: "加载失败",
                onRetry = viewModel::refresh
            )
            else -> {
                val listState = rememberLazyListState()
                // 滚动接近底部（最后可见项 >= 总项数-3）时自动加载下一页。
                // key 用 lastVisibleIndex + totalItemsCount：短列表追加后若仍全部可见可继续触发（布尔 key 不变化会漏拉）
                val lastVisibleIndex by remember {
                    derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                }
                LaunchedEffect(lastVisibleIndex, listState.layoutInfo.totalItemsCount) {
                    if (lastVisibleIndex >= listState.layoutInfo.totalItemsCount - 3) viewModel.loadMore()
                }
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ===== 归档统计 header（GET /projects/archived/statistics） =====
                    item(key = "statistics") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val stats = state.statistics
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatCard(
                                    label = "归档总数",
                                    value = "${stats?.totalCount ?: 0}",
                                    icon = Icons.Outlined.Archive,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    label = "本年归档",
                                    value = "${stats?.currentYearCount ?: 0}",
                                    icon = Icons.Outlined.TrendingUp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            stats?.totalContractAmount?.let { amount ->
                                StatCard(
                                    label = "合同总金额",
                                    value = formatAmount(amount),
                                    icon = Icons.Outlined.Payments
                                )
                            }
                            stats?.currentMonthCount?.let {
                                Text(
                                    text = "本月新增归档 $it 个",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    // ===== 归档项目列表 =====
                    if (state.projects.isEmpty()) {
                        item(key = "empty") { EmptyState(text = "暂无归档项目") }
                    } else {
                        items(state.projects, key = { it.id }) { ArchiveCard(it) }
                    }

                    // 末尾 footer：加载中 / 没有更多
                    if (state.loadingMore || (!state.hasMore && state.projects.isNotEmpty())) {
                        item(key = "footer") {
                            Text(
                                text = if (state.loadingMore) "加载中…" else "— 没有更多了 —",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveCard(project: ProjectDto) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Archive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusTag(text = "已归档", tone = TagTone.SUCCESS)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    project.code?.takeIf { it.isNotBlank() },
                    project.customerName?.takeIf { it.isNotBlank() }
                ).joinToString(" · ").ifBlank { "-" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "完工进度",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                LinearProgressIndicator(
                    progress = { project.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.weight(1f).height(6.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${project.progress}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = project.endDate?.takeIf { it.isNotBlank() }?.let { "$it 验收" } ?: "验收日期未记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = project.manager?.takeIf { it.isNotBlank() }?.let { "项目经理 $it" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
