package com.example.hbsz_app_android.ui.approval

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.common.formatDateTime
import com.example.hbsz_app_android.data.remote.dto.PageResult
import com.example.hbsz_app_android.data.remote.dto.TodoCountDto
import com.example.hbsz_app_android.data.remote.dto.TodoItemDto
import com.example.hbsz_app_android.data.repository.ApprovalTodoRepository
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.FeedbackSnackbarHost
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.PullRefreshBox
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.todoSourceTextOf
import com.example.hbsz_app_android.ui.components.todoSourceToneOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApprovalTodoUiState(
    val counts: TodoCountDto? = null,
    /** 来源筛选：null = 全部（progress/workload/document/auth） */
    val source: String? = null,
    val todos: List<TodoItemDto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val pageNum: Int = 1,
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false,
    /** 正在提交审核的待办标识（source:bizId） */
    val submittingKey: String? = null,
    /** 操作结果提示（Snackbar 展示，自动消失） */
    val message: String? = null,
    /** 提示类型：true = 失败（红色浮层），false = 成功（深色浮层） */
    val messageIsError: Boolean = false
)

@HiltViewModel
class ApprovalTodoViewModel @Inject constructor(
    private val approvalTodoRepository: ApprovalTodoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ApprovalTodoUiState())
    val state: StateFlow<ApprovalTodoUiState> = _state

    companion object {
        private const val PAGE_SIZE = 20
    }

    init {
        refresh()
    }

    /** 重置到第一页 + 刷新计数（进入页面 / 切换来源 / 重试） */
    fun refresh() {
        viewModelScope.launch {
            when (val res = approvalTodoRepository.getCounts()) {
                is Resource.Success -> _state.update { it.copy(counts = res.data) }
                is Resource.Error -> Unit // 计数失败不阻断列表，卡片展示 "-"
                Resource.Loading -> Unit
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (
                val res = approvalTodoRepository.listTodos(
                    pageNum = 1,
                    pageSize = PAGE_SIZE,
                    source = _state.value.source
                )
            ) {
                is Resource.Success -> {
                    val page = res.data
                    _state.update {
                        it.copy(
                            loading = false,
                            todos = page.records,
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

    /** 切换来源筛选（点击计数卡或筛选 Chip） */
    fun setSource(source: String?) {
        if (source == _state.value.source) return
        _state.update { it.copy(source = source) }
        refresh()
    }

    /** 是否还有下一页（pages=0 时按 total 估算兜底） */
    private fun computeHasMore(page: PageResult<TodoItemDto>, loadedPage: Int): Boolean =
        if (page.pages > 0) loadedPage < page.pages
        else loadedPage * PAGE_SIZE < page.total

    /** 滚动到底部加载下一页 */
    fun loadMore() {
        val cur = _state.value
        if (cur.loadingMore || !cur.hasMore || cur.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            when (
                val res = approvalTodoRepository.listTodos(
                    pageNum = cur.pageNum + 1,
                    pageSize = PAGE_SIZE,
                    source = cur.source
                )
            ) {
                is Resource.Success -> {
                    val page = res.data
                    val next = cur.pageNum + 1
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            // distinctBy 兜底：后端忽略分页参数返回全量时不产生重复项
                            todos = (it.todos + page.records).distinctBy { t -> t.source to t.bizId },
                            pageNum = next,
                            hasMore = computeHasMore(page, next)
                        )
                    }
                }

                is Resource.Error -> _state.update {
                    it.copy(loadingMore = false, message = res.message, messageIsError = true)
                }

                Resource.Loading -> Unit
            }
        }
    }

    /**
     * 快捷审核：按来源分发到对应业务模块审核接口（后端 CAS/归属校验零重复）。
     * 成功后本地移除待办并同步扣减分类计数。
     */
    fun review(todo: TodoItemDto, approve: Boolean, remark: String?) {
        val key = "${todo.source}:${todo.bizId}"
        if (_state.value.submittingKey == key) return
        _state.update { it.copy(submittingKey = key, message = null) }
        viewModelScope.launch {
            when (
                val res = approvalTodoRepository.reviewTodo(
                    source = todo.source,
                    bizId = todo.bizId,
                    approve = approve,
                    remark = remark?.takeIf { it.isNotBlank() }
                )
            ) {
                is Resource.Success -> {
                    val action = if (approve) "通过" else "驳回"
                    _state.update { st ->
                        st.copy(
                            submittingKey = null,
                            todos = st.todos.filterNot { t -> t.source == todo.source && t.bizId == todo.bizId },
                            counts = st.counts?.decrement(todo.source),
                            message = "已${action}该${todoSourceTextOf(todo.source)}待办",
                            messageIsError = false
                        )
                    }
                }

                is Resource.Error -> _state.update {
                    it.copy(submittingKey = null, message = res.message, messageIsError = true)
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}

/** 分类计数本地扣减（审核成功后保持卡片与列表一致，避免整页重刷） */
private fun TodoCountDto.decrement(source: String): TodoCountDto = when (source) {
    "progress" -> copy(progress = (progress - 1).coerceAtLeast(0), total = (total - 1).coerceAtLeast(0))
    "workload" -> copy(workload = (workload - 1).coerceAtLeast(0), total = (total - 1).coerceAtLeast(0))
    "document" -> copy(document = (document - 1).coerceAtLeast(0), total = (total - 1).coerceAtLeast(0))
    "auth" -> copy(auth = (auth - 1).coerceAtLeast(0), total = (total - 1).coerceAtLeast(0))
    else -> this
}

/**
 * 审批待办中心 —— 对齐 uni-app 端 pages/approval/todo.vue：
 * 聚合进度/工作量/文档/授权四类审核待办的统一入口；
 * 快捷审核按来源分发到各业务模块接口（后端 CAS/权限/通知逻辑零重复）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalTodoScreen(
    onBack: () -> Unit = {},
    viewModel: ApprovalTodoViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var rejectTarget by remember { mutableStateOf<TodoItemDto?>(null) }
    var rejectRemark by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val total = state.counts?.total
                    Text(
                        if (total != null) "审批中心 ($total)" else "审批中心",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            FeedbackSnackbarHost(
                message = state.message,
                isError = state.messageIsError,
                onDismiss = { viewModel.clearMessage() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ===== 分类计数卡（首格"全部"= 总数；点击切换来源筛选，替代独立 Chip 行） =====
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val counts = state.counts
                Row(Modifier.padding(vertical = 12.dp)) {
                    CountCell(
                        label = "全部",
                        value = counts?.total,
                        selected = state.source == null,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setSource(null) }
                    )
                    CountCell(
                        label = "进度",
                        value = counts?.progress,
                        selected = state.source == "progress",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setSource("progress") }
                    )
                    CountCell(
                        label = "工作量",
                        value = counts?.workload,
                        selected = state.source == "workload",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setSource("workload") }
                    )
                    CountCell(
                        label = "文档",
                        value = counts?.document,
                        selected = state.source == "document",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setSource("document") }
                    )
                    CountCell(
                        label = "授权",
                        value = counts?.auth,
                        selected = state.source == "auth",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setSource("auth") }
                    )
                }
            }

            // ===== 待办列表（下拉刷新计数 + 列表） =====
            PullRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    state.loading && state.todos.isEmpty() -> LoadingState(Modifier.fillMaxSize())
                    state.error != null -> ErrorState(
                        message = state.error ?: "加载失败",
                        modifier = Modifier.fillMaxSize(),
                        onRetry = { viewModel.refresh() }
                    )

                    state.todos.isEmpty() -> EmptyState(
                        text = if (state.source == null) "暂无待办，太棒了" else "暂无${todoSourceTextOf(state.source)}待办"
                    )

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
                                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                state.todos,
                                key = { it.source to it.bizId }
                            ) { todo ->
                                TodoCard(
                                    todo = todo,
                                    submitting = state.submittingKey == "${todo.source}:${todo.bizId}",
                                    onApprove = { viewModel.review(todo, approve = true, remark = null) },
                                    onReject = {
                                        rejectTarget = todo
                                        rejectRemark = ""
                                    }
                                )
                            }
                            if (state.loadingMore) {
                                item(key = "loading_more") { LoadMoreFooter(loading = true) }
                            } else if (!state.hasMore && state.todos.isNotEmpty()) {
                                item(key = "no_more") { LoadMoreFooter(loading = false) }
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== 驳回弹窗（意见选填：四类审核后端均不强制） =====
    rejectTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("驳回待办", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("「${target.title ?: "-"}」· ${target.submitter ?: "-"}")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rejectRemark,
                        onValueChange = { rejectRemark = it },
                        placeholder = { Text("请输入驳回意见（选填）") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.review(target, approve = false, remark = rejectRemark.trim())
                        rejectTarget = null
                    },
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

/** 分类计数单元格（数值 + 标签，点击切换来源筛选） */
@Composable
private fun CountCell(
    label: String,
    value: Long?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value?.toString() ?: "-",
            style = MaterialTheme.typography.titleLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 待办卡片：来源标签 + 标题 + 项目/提交人/时间 + 通过/驳回 */
@Composable
private fun TodoCard(
    todo: TodoItemDto,
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
                StatusTag(
                    text = todoSourceTextOf(todo.source),
                    tone = todoSourceToneOf(todo.source)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = todo.submitTime?.let { formatDateTime(it) } ?: "-",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = todo.title ?: "待办事项",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    todo.projectName?.takeIf { it.isNotBlank() },
                    todo.submitter?.takeIf { it.isNotBlank() }?.let { "提交人：$it" }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            todo.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                modifier = Modifier.height(16.dp).width(16.dp),
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
