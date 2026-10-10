package com.example.hbsz_app_android.ui.project

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
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
import com.example.hbsz_app_android.data.local.UserPreferences
import com.example.hbsz_app_android.data.remote.dto.ActiveUserDto
import com.example.hbsz_app_android.data.remote.dto.ProjectAssignmentDto
import com.example.hbsz_app_android.data.remote.dto.ProjectDto
import com.example.hbsz_app_android.data.remote.dto.ProjectProgressDto
import com.example.hbsz_app_android.data.remote.dto.TaskDto
import com.example.hbsz_app_android.data.repository.ProjectAuthRepository
import com.example.hbsz_app_android.data.repository.ProjectRepository
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.FeedbackSnackbarHost
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.SectionHeader
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.TagTone
import com.example.hbsz_app_android.ui.components.statusToneOf
import com.example.hbsz_app_android.ui.components.statusTextOf
import com.example.hbsz_app_android.ui.theme.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val project: ProjectDto? = null,
    val progress: ProjectProgressDto? = null,
    // ===== 授权工程师与转移（2026-08 同步 Web 端项目转移/授权能力） =====
    /** 项目下有效授权工程师（GET /projects/{id}/authorization） */
    val engineers: List<ProjectAssignmentDto> = emptyList(),
    /** 转移弹窗下拉数据：活跃工程师 / 活跃项目经理 */
    val activeEngineers: List<ActiveUserDto> = emptyList(),
    val activeManagers: List<ActiveUserDto> = emptyList(),
    /** 弹窗下拉数据加载中 */
    val optionsLoading: Boolean = false,
    /** 转移/授权/撤销提交中（弹窗与按钮防重复） */
    val submitting: Boolean = false,
    /** 操作结果提示（Snackbar 展示，自动消失） */
    val message: String? = null,
    /** 提示类型：true = 失败（红色浮层），false = 成功（深色浮层） */
    val messageIsError: Boolean = false
)

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val projectAuthRepository: ProjectAuthRepository,
    userPreferences: UserPreferences
) : ViewModel() {
    private val _state = MutableStateFlow(ProjectDetailUiState())
    val state: StateFlow<ProjectDetailUiState> = _state

    /** 是否可管理授权/转移（admin/PM，对齐后端 project:transfer 权限语义） */
    val canManage: StateFlow<Boolean> = userPreferences.rolesFlow
        .map { roles -> roles.contains("project_manager") || roles.contains("admin") }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun load(projectId: Long) {
        if (projectId <= 0) {
            _state.value = ProjectDetailUiState(loading = false, error = "无效的项目ID")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            // 详情、进度、授权工程师并行请求，互不阻塞
            coroutineScope {
                val detailDeferred = async { projectRepository.getProjectDetail(projectId) }
                val progressDeferred = async { projectRepository.getProjectProgress(projectId) }
                val engineersDeferred = async { projectAuthRepository.listProjectEngineers(projectId) }
                val detailRes = detailDeferred.await()
                val progressRes = progressDeferred.await()
                val engineersRes = engineersDeferred.await()
                when (detailRes) {
                    is Resource.Success -> _state.value = ProjectDetailUiState(
                        loading = false,
                        project = detailRes.data,
                        progress = (progressRes as? Resource.Success)?.data,
                        // 授权列表加载失败不阻断详情（工程师区显示空态）
                        engineers = (engineersRes as? Resource.Success)?.data ?: emptyList()
                    )
                    is Resource.Error -> _state.value = _state.value.copy(
                        loading = false,
                        error = detailRes.message
                    )
                    Resource.Loading -> Unit
                }
            }
        }
    }

    /**
     * 转移弹窗下拉数据预加载（打开弹窗时调用，已加载则跳过）。
     * 拉取失败不阻断弹窗——空列表时下拉无可选项，用户重进详情页可重试。
     */
    fun loadTransferOptions() {
        if (_state.value.optionsLoading ||
            (_state.value.activeEngineers.isNotEmpty() && _state.value.activeManagers.isNotEmpty())
        ) return
        _state.value = _state.value.copy(optionsLoading = true)
        viewModelScope.launch {
            coroutineScope {
                val engineersDeferred = async { projectAuthRepository.listActiveEngineers() }
                val managersDeferred = async { projectAuthRepository.listActiveManagers() }
                val engineersRes = engineersDeferred.await()
                val managersRes = managersDeferred.await()
                _state.value = _state.value.copy(
                    optionsLoading = false,
                    activeEngineers = (engineersRes as? Resource.Success)?.data ?: emptyList(),
                    activeManagers = (managersRes as? Resource.Success)?.data ?: emptyList()
                )
            }
        }
    }

    /**
     * 执行转移/授权（按弹窗模式分发）。
     * 成功后整页刷新：负责人移交会变更 manager（概览区即时反映），工程师替换会变更授权列表。
     */
    fun transfer(
        projectId: Long,
        mode: TransferMode,
        target: ActiveUserDto,
        remark: String?
    ) {
        if (_state.value.submitting) return
        _state.value = _state.value.copy(submitting = true, message = null)
        viewModelScope.launch {
            val res = when (mode) {
                TransferMode.MANAGER -> projectAuthRepository.transferProject(
                    projectId, toManagerId = target.id, remark = remark
                )
                TransferMode.REPLACE -> projectAuthRepository.transferProject(
                    projectId, toEngineerId = target.id, remark = remark
                )
                TransferMode.ASSIGN -> projectAuthRepository.assignEngineer(
                    projectId, target.id, remark
                )
            }
            when (res) {
                is Resource.Success -> {
                    _state.value = _state.value.copy(
                        submitting = false,
                        message = when (mode) {
                            TransferMode.MANAGER -> "已移交负责人：${target.name ?: target.username ?: "-"}"
                            TransferMode.REPLACE -> "已替换工程师：${target.name ?: target.username ?: "-"}"
                            TransferMode.ASSIGN -> "已授权工程师：${target.name ?: target.username ?: "-"}"
                        },
                        messageIsError = false
                    )
                    load(projectId)
                }
                is Resource.Error -> _state.value = _state.value.copy(
                    submitting = false,
                    message = res.message,
                    messageIsError = true
                )
                Resource.Loading -> Unit
            }
        }
    }

    /** 撤销指定工程师授权（后端 CAS 软删单点） */
    fun revokeEngineer(projectId: Long, engineerId: Long, engineerName: String?) {
        if (_state.value.submitting) return
        _state.value = _state.value.copy(submitting = true, message = null)
        viewModelScope.launch {
            when (val res = projectAuthRepository.revokeEngineer(projectId, engineerId)) {
                is Resource.Success -> {
                    _state.value = _state.value.copy(
                        submitting = false,
                        message = "已撤销 ${engineerName ?: "该工程师"} 的授权",
                        messageIsError = false
                    )
                    loadEngineersOnly(projectId)
                }
                is Resource.Error -> _state.value = _state.value.copy(
                    submitting = false,
                    message = res.message,
                    messageIsError = true
                )
                Resource.Loading -> Unit
            }
        }
    }

    /** 仅刷新授权工程师列表（撤销后无需重拉详情与进度） */
    private fun loadEngineersOnly(projectId: Long) {
        viewModelScope.launch {
            when (val res = projectAuthRepository.listProjectEngineers(projectId)) {
                is Resource.Success -> _state.value = _state.value.copy(engineers = res.data)
                is Resource.Error -> _state.value = _state.value.copy(
                    message = res.message,
                    messageIsError = true
                )
                Resource.Loading -> Unit
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}

/**
 * 项目详情页 —— 对齐 Web 端项目详情信息架构：
 * 项目概览（状态/基础信息）→ 进度总览 → 任务进度列表 → 参与成员（从任务负责人聚合）
 * → 授权工程师（2026-08 新增：授权列表 + PM 转移/授权/撤销入口）。
 * 数据源：GET /projects/{id} + GET /projects/{id}/progress + GET /projects/{id}/authorization。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: Long,
    onBack: () -> Unit = {},
    viewModel: ProjectDetailViewModel = hiltViewModel()
) {
    androidx.compose.runtime.LaunchedEffect(projectId) { viewModel.load(projectId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canManage by viewModel.canManage.collectAsStateWithLifecycle()
    val project = state.project
    val progressInfo = state.progress

    // 转移/授权弹窗与撤销确认弹窗的本地开合状态
    var showTransferDialog by remember { mutableStateOf(false) }
    var revokeTarget by remember { mutableStateOf<ProjectAssignmentDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = project?.name ?: "项目详情",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // PM/admin 顶栏快捷入口：项目转移/授权（对齐 Web 项目卡片"转移"操作）
                    if (canManage && project != null) {
                        IconButton(onClick = {
                            viewModel.loadTransferOptions()
                            showTransferDialog = true
                        }) {
                            Icon(
                                Icons.Outlined.SwapHoriz,
                                contentDescription = "项目转移/授权"
                            )
                        }
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
                onRetry = { viewModel.load(projectId) }
            )
            project == null -> EmptyState(text = "项目不存在或已被删除")
            else -> ProjectDetailContent(
                project = project,
                tasks = progressInfo?.tasks ?: emptyList(),
                overallProgress = progressInfo?.overallProgress ?: project.progress,
                engineers = state.engineers,
                canManage = canManage,
                submitting = state.submitting,
                onOpenTransfer = {
                    viewModel.loadTransferOptions()
                    showTransferDialog = true
                },
                onRevoke = { revokeTarget = it },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        }
    }

    // ===== 转移/授权弹窗（三模式：移交负责人 / 替换工程师 / 新增授权） =====
    if (showTransferDialog && project != null) {
        ProjectTransferDialog(
            projectName = project.name,
            currentManagerName = project.manager,
            engineers = state.activeEngineers,
            managers = state.activeManagers,
            optionsLoading = state.optionsLoading,
            submitting = state.submitting,
            onSubmit = { mode, target, remark ->
                viewModel.transfer(projectId, mode, target, remark)
                showTransferDialog = false
            },
            onDismiss = { showTransferDialog = false }
        )
    }

    // ===== 撤销授权确认弹窗 =====
    revokeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("撤销授权", fontWeight = FontWeight.SemiBold) },
            text = {
                Text("确认撤销 ${target.engineerName ?: "该工程师"} 对「${project?.name ?: "该项目"}」的访问授权？撤销后其将无法查看该项目。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        target.engineerId?.let { engineerId ->
                            viewModel.revokeEngineer(
                                projectId,
                                engineerId,
                                target.engineerName
                            )
                        }
                        revokeTarget = null
                    },
                    enabled = !state.submitting
                ) { Text("确认撤销") }
            },
            dismissButton = {
                OutlinedButton(onClick = { revokeTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ProjectDetailContent(
    project: ProjectDto,
    tasks: List<TaskDto>,
    overallProgress: Int,
    engineers: List<ProjectAssignmentDto>,
    canManage: Boolean,
    submitting: Boolean,
    onOpenTransfer: () -> Unit,
    onRevoke: (ProjectAssignmentDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val taskDone = tasks.count { it.status == "已完成" || it.status == "completed" }
    val taskTotal = tasks.size

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== 项目概览 =====
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusTag(
                            text = statusTextOf(project.status),
                            tone = statusToneOf(project.status)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "优先级：${project.priority ?: "-"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    DetailMetaRow(Icons.Outlined.Person, "项目经理", project.manager ?: "未指派")
                    DetailMetaRow(
                        Icons.Outlined.Schedule, "工期",
                        "${project.startDate?.take(10) ?: "-"} ~ ${project.endDate?.take(10) ?: "-"}"
                    )
                    DetailMetaRow(Icons.Outlined.LocationOn, "地址", project.address ?: "-")
                    project.customerName?.takeIf { it.isNotBlank() }?.let {
                        DetailMetaRow(Icons.Outlined.Person, "客户", it)
                    }
                }
            }
        }

        // ===== 进度总览 =====
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 环形进度（总进度）
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { overallProgress.coerceIn(0, 100) / 100f },
                                modifier = Modifier.size(72.dp),
                                strokeWidth = 6.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "$overallProgress%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "总进度",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (taskTotal == 0) overallProgress.coerceIn(0, 100) / 100f
                                    else taskDone.toFloat() / taskTotal
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = Success,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "任务完成 $taskDone/$taskTotal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ===== 任务进度 =====
        item { SectionHeader(title = "任务进度") }
        if (tasks.isEmpty()) {
            item { EmptyState(text = "暂无任务") }
        } else {
            items(tasks, key = { it.id }) { task ->
                TaskCard(task)
            }
        }

        // ===== 参与成员（从任务负责人聚合） =====
        item { SectionHeader(title = "参与成员") }
        item {
            // remember 缓存成员聚合结果，避免每次重组重复计算
            val members = remember(tasks) {
                tasks.mapNotNull { it.assignee?.takeIf { name -> name.isNotBlank() } }.distinct()
            }
            if (members.isEmpty()) {
                EmptyState(text = "暂无成员信息")
            } else {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        members.forEach { name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name.take(1),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "任务负责人",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ===== 授权工程师（2026-08 同步 Web 端授权能力：列表 + PM 管理操作） =====
        item {
            SectionHeader(
                title = "授权工程师",
                trailing = if (canManage) {
                    {
                        OutlinedButton(
                            onClick = onOpenTransfer,
                            enabled = !submitting,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("转移 / 授权", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else null
            )
        }
        if (engineers.isEmpty()) {
            item { EmptyState(text = "暂无授权工程师") }
        } else {
            items(engineers, key = { it.engineerId ?: it.id ?: it.hashCode().toLong() }) { engineer ->
                EngineerCard(
                    engineer = engineer,
                    canManage = canManage,
                    submitting = submitting,
                    onRevoke = { onRevoke(engineer) }
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

/**
 * 授权工程师卡片 —— 信息对齐 Web 端授权列表：
 * 姓名/账号 + 授权范围 + 授权时间 + 过期时间（PM 可撤销）。
 */
@Composable
private fun EngineerCard(
    engineer: ProjectAssignmentDto,
    canManage: Boolean,
    submitting: Boolean,
    onRevoke: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (engineer.engineerName ?: "-").take(1),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = engineer.engineerName ?: "未知工程师",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    engineer.engineerUsername?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 授权范围标签（read_only/read_write/review → 中文）
                StatusTag(
                    text = when (engineer.permissionScope) {
                        "read_only" -> "只读"
                        "read_write" -> "读写"
                        "review" -> "审核"
                        else -> "读写"
                    },
                    tone = when (engineer.permissionScope) {
                        "read_only" -> TagTone.INFO
                        "review" -> TagTone.WARNING
                        else -> TagTone.SUCCESS
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Text(
                    text = "授权时间：${formatDate(engineer.assignedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    // expireAt 为 null 表示永久授权
                    text = engineer.expireAt?.let { "有效期至 ${formatDate(it)}" } ?: "永久有效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // PM 撤销操作（对齐 Web 授权列表"撤销"按钮）
            if (canManage) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onRevoke,
                        enabled = !submitting,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("撤销授权", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                StatusTag(text = statusTextOf(task.status), tone = statusToneOf(task.status))
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { task.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = if (task.status == "已延期" || task.status == "delayed")
                    MaterialTheme.colorScheme.error else Success,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    text = "负责人：${task.assignee ?: "未指派"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "截止 ${task.endDate?.take(10) ?: "-"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailMetaRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
