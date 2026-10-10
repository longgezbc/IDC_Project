package com.example.hbsz_app_android.ui.workload

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.example.hbsz_app_android.common.formatDate
import com.example.hbsz_app_android.common.formatDateTime
import com.example.hbsz_app_android.common.formatQuantity
import com.example.hbsz_app_android.data.remote.dto.ProjectDto
import com.example.hbsz_app_android.data.remote.dto.WorkloadRecordDto
import com.example.hbsz_app_android.data.repository.ProjectRepository
import com.example.hbsz_app_android.data.repository.WorkloadRepository
import com.example.hbsz_app_android.ui.components.DropdownField
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.FeedbackSnackbarHost
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.PullRefreshBox
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.reviewStatusTextOf
import com.example.hbsz_app_android.ui.components.reviewStatusToneOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkloadReviewUiState(
    val projects: List<ProjectDto> = emptyList(),
    val selectedProject: ProjectDto? = null,
    /** 筛选状态：pending / approved / rejected（默认待审核） */
    val reviewStatus: String = "pending",
    val records: List<WorkloadRecordDto> = emptyList(),
    val recordsLoading: Boolean = false,
    val recordsError: String? = null,
    /** 正在提交审核的记录ID集合 */
    val submittingIds: Set<Long> = emptySet(),
    /** 操作结果提示（Snackbar 展示，自动消失） */
    val message: String? = null,
    /** 提示类型：true = 失败（红色浮层），false = 成功（深色浮层） */
    val messageIsError: Boolean = false
)

@HiltViewModel
class WorkloadReviewViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val workloadRepository: WorkloadRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WorkloadReviewUiState())
    val state: StateFlow<WorkloadReviewUiState> = _state

    init {
        loadProjects()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            when (val res = projectRepository.getProjects(pageNum = 1, pageSize = 100)) {
                is Resource.Success -> _state.update { it.copy(projects = res.data.records) }
                is Resource.Error -> _state.update {
                    it.copy(message = "项目列表加载失败：${res.message}", messageIsError = true)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun selectProject(project: ProjectDto) {
        val projectId = project.id.toLongOrNull() ?: return
        _state.update { it.copy(selectedProject = project) }
        loadRecords(projectId)
    }

    fun setStatus(status: String) {
        if (status == _state.value.reviewStatus) return
        _state.update { it.copy(reviewStatus = status) }
        _state.value.selectedProject?.id?.toLongOrNull()?.let { loadRecords(it) }
    }

    /** 审核列表（不传日期查全部日期；单次全量返回，与 uni-app 端一致） */
    private fun loadRecords(projectId: Long) {
        _state.update { it.copy(recordsLoading = true, recordsError = null) }
        viewModelScope.launch {
            when (
                val res = workloadRepository.listReviewRecords(
                    projectId = projectId,
                    reviewStatus = _state.value.reviewStatus
                )
            ) {
                is Resource.Success -> _state.update {
                    it.copy(recordsLoading = false, records = res.data)
                }

                is Resource.Error -> _state.update {
                    it.copy(recordsLoading = false, recordsError = res.message, records = emptyList())
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun retry() {
        _state.value.selectedProject?.id?.toLongOrNull()?.let { loadRecords(it) }
    }

    /**
     * 审核（单条复用批量接口 ids=[id]，CAS 语义）：
     * 通过后计入清单完成量与人员绩效；驳回后成员可修改重新提交。
     */
    fun review(record: WorkloadRecordDto, approve: Boolean, remark: String?) {
        if (record.id in _state.value.submittingIds) return
        _state.update {
            it.copy(submittingIds = it.submittingIds + record.id, message = null)
        }
        viewModelScope.launch {
            when (
                val res = workloadRepository.reviewRecords(
                    ids = listOf(record.id),
                    approve = approve,
                    remark = remark?.takeIf { it.isNotBlank() }
                )
            ) {
                is Resource.Success -> {
                    val action = if (approve) "通过" else "驳回"
                    val st = _state.value
                    // 待审核筛选：本地移除已审项保持流畅；已通过/已驳回筛选：重载刷新状态
                    if (st.reviewStatus == "pending") {
                        _state.update {
                            it.copy(
                                submittingIds = it.submittingIds - record.id,
                                records = it.records.filterNot { r -> r.id == record.id },
                                message = "已${action} ${res.data} 条填报",
                                messageIsError = false
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                submittingIds = it.submittingIds - record.id,
                                message = "已${action} ${res.data} 条填报",
                                messageIsError = false
                            )
                        }
                        st.selectedProject?.id?.toLongOrNull()?.let { loadRecords(it) }
                    }
                }

                is Resource.Error -> _state.update {
                    it.copy(
                        submittingIds = it.submittingIds - record.id,
                        message = res.message,
                        messageIsError = true
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}

/** 审核状态筛选选项（value 与后端 reviewStatus 枚举一致） */
private val STATUS_FILTERS = listOf(
    "pending" to "待审核",
    "approved" to "已通过",
    "rejected" to "已驳回"
)

/**
 * 工作量审核页（PM 视角）—— 对齐 uni-app 端 pages/workload/review.vue：
 * 选择项目后集中审核成员提交的每日工作量填报，可「通过」或「驳回」（驳回可填原因）。
 * 审核通过后数据计入清单完成量与人员绩效；CAS 语义保证同一条记录仅被审核一次。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkloadReviewScreen(
    onBack: () -> Unit = {},
    viewModel: WorkloadReviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 驳回弹窗状态
    var rejectTarget by remember { mutableStateOf<WorkloadRecordDto?>(null) }
    var rejectRemark by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工作量审核", fontWeight = FontWeight.SemiBold) },
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
            // ===== 筛选卡：项目 + 状态 =====
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropdownField(
                        label = "项目",
                        options = state.projects,
                        selected = state.selectedProject,
                        textOf = { it.name },
                        onSelect = { viewModel.selectProject(it) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        STATUS_FILTERS.forEach { (value, label) ->
                            FilterChip(
                                selected = state.reviewStatus == value,
                                onClick = { viewModel.setStatus(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // ===== 记录列表（下拉刷新当前项目记录） =====
            PullRefreshBox(
                isRefreshing = state.recordsLoading,
                onRefresh = { viewModel.retry() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    state.recordsLoading && state.records.isEmpty() ->
                        LoadingState(Modifier.fillMaxSize())
                    state.recordsError != null -> ErrorState(
                        message = state.recordsError ?: "加载失败",
                        modifier = Modifier.fillMaxSize(),
                        onRetry = { viewModel.retry() }
                    )

                    state.records.isEmpty() -> EmptyState(
                        text = if (state.selectedProject != null) "暂无相关填报记录" else "请先选择项目"
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.records, key = { it.id }) { record ->
                            WorkloadRecordCard(
                                record = record,
                                submitting = record.id in state.submittingIds,
                                onApprove = { viewModel.review(record, approve = true, remark = null) },
                                onReject = {
                                    rejectTarget = record
                                    rejectRemark = ""
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ===== 驳回原因弹窗（选填，对齐 uni-app 端驳回交互） =====
    rejectTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("驳回原因", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("「${target.workScope ?: "-"}」· ${target.workerName ?: "-"} · ${formatQuantity(target.quantity)}${target.unit ?: ""}")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rejectRemark,
                        onValueChange = { rejectRemark = it },
                        placeholder = { Text("请输入驳回原因（选填）") },
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

/** 填报明细卡片：作业上下文 + 人员数量 + 审核信息 + 通过/驳回操作 */
@Composable
private fun WorkloadRecordCard(
    record: WorkloadRecordDto,
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
                        text = record.workScope ?: "未知作业",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = record.category ?: "未分类",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusTag(
                    text = reviewStatusTextOf(record.reviewStatus),
                    tone = reviewStatusToneOf(record.reviewStatus)
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoColumn(label = "日期", value = formatDate(record.recordDate))
                InfoColumn(label = "人员", value = record.workerName ?: "-")
                InfoColumn(
                    label = "完成数量",
                    value = "${formatQuantity(record.quantity)}${record.unit ?: ""}"
                )
            }

            // 审核信息（已审记录展示审核人与意见）
            if (record.reviewStatus != "pending") {
                record.reviewerName?.let { reviewer ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "审核人：$reviewer" + (record.reviewTime?.let { " · ${formatDateTime(it)}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                record.reviewRemark?.takeIf { it.isNotBlank() }?.let { remark ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "审核意见：$remark",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 操作按钮：仅待审核记录可操作
            if (record.reviewStatus == "pending") {
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
}

@Composable
private fun InfoColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
