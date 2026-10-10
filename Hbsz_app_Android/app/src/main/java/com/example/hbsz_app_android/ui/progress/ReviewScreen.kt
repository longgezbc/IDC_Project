package com.example.hbsz_app_android.ui.progress

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
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import coil.compose.AsyncImage
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.common.formatDateTime
import com.example.hbsz_app_android.data.remote.dto.PageResult
import com.example.hbsz_app_android.data.remote.dto.ProgressLogDto
import com.example.hbsz_app_android.data.repository.TaskRepository
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.reviewStatusToneOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val pending: List<ProgressLogDto> = emptyList(),
    /** 符合条件的待审核总数（标题栏展示） */
    val total: Long = 0,
    /** 已加载的最后一页页码 */
    val pageNum: Int = 1,
    /** 是否还有下一页 */
    val hasMore: Boolean = true,
    /** 是否正在加载下一页 */
    val loadingMore: Boolean = false,
    /** 正在提交审核的日志ID */
    val submittingId: String? = null,
    /** 审核操作结果提示 */
    val actionMessage: String? = null
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state

    companion object {
        private const val PAGE_SIZE = 20
    }

    init {
        refresh()
    }

    /**
     * 是否还有下一页：
     * - pages > 0：以服务端总页数为准（旧版数组响应被包装为 pages=1，自然不再翻页）
     * - pages = 0：按 total 估算兜底
     */
    private fun computeHasMore(page: PageResult<ProgressLogDto>, loadedPage: Int): Boolean =
        if (page.pages > 0) loadedPage < page.pages
        else loadedPage * PAGE_SIZE < page.total

    /** 重置到第一页加载（进入页面 / 重试） */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val res = taskRepository.getPendingProgress(pageNum = 1, pageSize = PAGE_SIZE)) {
                is Resource.Success -> {
                    val page = res.data
                    _state.value = _state.value.copy(
                        loading = false,
                        pending = page.records,
                        total = page.total,
                        pageNum = 1,
                        hasMore = computeHasMore(page, 1)
                    )
                }
                is Resource.Error -> _state.value = _state.value.copy(
                    loading = false,
                    error = res.message
                )
                Resource.Loading -> Unit
            }
        }
    }

    /** 滚动到底部加载下一页 */
    fun loadMore() {
        val cur = _state.value
        if (cur.loadingMore || !cur.hasMore || cur.loading) return
        viewModelScope.launch {
            _state.value = cur.copy(loadingMore = true)
            when (val res = taskRepository.getPendingProgress(pageNum = cur.pageNum + 1, pageSize = PAGE_SIZE)) {
                is Resource.Success -> {
                    val page = res.data
                    val next = cur.pageNum + 1
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        // distinctBy 兜底：后端忽略分页参数返回全量时不产生重复项
                        pending = (_state.value.pending + page.records).distinctBy { it.id },
                        total = page.total,
                        pageNum = next,
                        hasMore = computeHasMore(page, next)
                    )
                }
                is Resource.Error -> _state.value = _state.value.copy(
                    loadingMore = false,
                    actionMessage = res.message
                )
                Resource.Loading -> Unit
            }
        }
    }

    /** PUT /tasks/progress/{logId}/review（审核通过则回写任务进度，驳回需意见） */
    fun review(logId: String, approve: Boolean, remark: String?) {
        val id = logId.toLongOrNull() ?: return
        // 仅拦截同一条日志的重复提交，不同日志可并发审核
        if (_state.value.submittingId == logId) return
        viewModelScope.launch {
            _state.value = _state.value.copy(submittingId = logId, actionMessage = null)
            when (val res = taskRepository.reviewProgress(id, approve, remark)) {
                is Resource.Success -> {
                    _state.value = _state.value.copy(
                        submittingId = null,
                        pending = _state.value.pending.filterNot { it.id == logId },
                        // 本地同步扣减总数，标题栏计数保持一致
                        total = (_state.value.total - 1).coerceAtLeast(0),
                        actionMessage = if (approve) "已通过该填报" else "已驳回该填报"
                    )
                }
                is Resource.Error -> _state.value = _state.value.copy(
                    submittingId = null,
                    actionMessage = res.message
                )
                Resource.Loading -> Unit
            }
        }
    }

    fun clearActionMessage() {
        _state.value = _state.value.copy(actionMessage = null)
    }
}

/**
 * 待审核页 —— 对齐 Web 端 review/PendingReview.vue + ProgressReviewDialog.vue：
 * 项目经理审核施工人员的进度填报，通过则更新任务进度，驳回需填写意见。
 * 数据源：GET /tasks/progress/pending（分页，task:progress:review 权限）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onBack: () -> Unit = {},
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 驳回弹窗状态
    var rejectTarget by remember { mutableStateOf<ProgressLogDto?>(null) }
    var rejectComment by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 分页后展示服务端总数，而非已加载条数
                    Text("待审核 (${state.total})", fontWeight = FontWeight.SemiBold)
                },
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
        // 操作结果提示条
        state.actionMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        when {
            state.loading -> LoadingState(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
            state.error != null -> ErrorState(
                message = state.error ?: "加载失败",
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                onRetry = { viewModel.refresh() }
            )
            // 当前页审核完但服务端还有更多：重拉下一批（已审项服务端已移除），而非误显"全部完毕"
            state.pending.isEmpty() && state.hasMore && !state.loadingMore -> {
                LaunchedEffect(Unit) { viewModel.refresh() }
                LoadingState(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                )
            }
            state.pending.isEmpty() -> Column(Modifier.padding(padding)) {
                EmptyState(text = "太棒了，填报已全部审核完毕")
            }
            else -> {
                val listState = rememberLazyListState()
                // 滚动至列表末尾（剩余 3 项内）时自动加载下一页。
                // key 用 lastVisibleIndex + totalItemsCount：短列表追加后若仍全部可见可继续触发（布尔 key 不变化会漏拉）
                val lastVisibleIndex by remember {
                    derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                }
                LaunchedEffect(lastVisibleIndex, listState.layoutInfo.totalItemsCount) {
                    if (lastVisibleIndex >= listState.layoutInfo.totalItemsCount - 3) viewModel.loadMore()
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.pending, key = { it.id }) { submission ->
                        ReviewCard(
                            submission = submission,
                            submitting = state.submittingId == submission.id,
                            onApprove = { viewModel.review(submission.id, approve = true, remark = null) },
                            onReject = {
                                rejectTarget = submission
                                rejectComment = ""
                            }
                        )
                    }
                    // 分页 footer
                    if (state.loadingMore) {
                        item(key = "loading_more") { LoadMoreFooter(loading = true) }
                    } else if (!state.hasMore && state.pending.isNotEmpty()) {
                        item(key = "no_more") { LoadMoreFooter(loading = false) }
                    }
                }
            }
        }
    }

    // ===== 驳回意见弹窗（对齐 Web 端 ProgressReviewDialog 驳回分支，意见必填） =====
    rejectTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("驳回填报", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("「${target.taskName ?: "-"}」· 上报进度 ${target.progress}%")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rejectComment,
                        onValueChange = { rejectComment = it },
                        placeholder = { Text("请填写驳回意见（必填）") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.review(target.id, approve = false, remark = rejectComment.trim())
                        rejectTarget = null
                    },
                    enabled = rejectComment.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("确认驳回") }
            },
            dismissButton = {
                OutlinedButton(onClick = { rejectTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ReviewCard(
    submission: ProgressLogDto,
    submitting: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
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
                        text = submission.taskName ?: "未知任务",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            submission.projectName,
                            submission.reporterName,
                            submission.reportTime?.let { formatDateTime(it) }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusTag(
                    text = "待审核",
                    tone = reviewStatusToneOf(submission.reviewStatus)
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "上报进度",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                LinearProgressIndicator(
                    progress = { submission.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp),
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

            submission.remark?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 施工照片（相对路径 → /files/preview/ 完整URL）
            if (submission.images.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${submission.images.size} 张施工照片",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    submission.images.take(3).forEach { path ->
                        AsyncImage(
                            model = TaskRepositoryPreviewUrl(path),
                            contentDescription = "施工照片",
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = !submitting,
                    modifier = Modifier.weight(1f)
                ) { Text(if (submitting) "处理中…" else "驳回") }
                Button(
                    onClick = onApprove,
                    enabled = !submitting,
                    modifier = Modifier.weight(1f)
                ) { Text("通过") }
            }
        }
    }
}

/** 预览URL工具（Compose 内无仓库注入场景下的顶层函数委托） */
@Composable
private fun TaskRepositoryPreviewUrl(relativePath: String): String =
    rememberPreviewUrl(relativePath)

@Composable
private fun rememberPreviewUrl(relativePath: String): String =
    androidx.compose.runtime.remember(relativePath) {
        if (relativePath.startsWith("http", ignoreCase = true)) relativePath
        else com.example.hbsz_app_android.BuildConfig.API_BASE_URL.trimEnd('/') +
            "/files/preview/" + relativePath
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
