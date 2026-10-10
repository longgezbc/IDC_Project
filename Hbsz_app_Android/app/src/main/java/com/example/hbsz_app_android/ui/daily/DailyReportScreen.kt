package com.example.hbsz_app_android.ui.daily

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.hbsz_app_android.common.DownloadSaver
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.data.remote.dto.DailyReportPreviewDto
import com.example.hbsz_app_android.data.remote.dto.ExportRecordDto
import com.example.hbsz_app_android.data.repository.StatisticsRepository
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.SectionHeader
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.TagTone
import com.example.hbsz_app_android.ui.components.statusToneOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import javax.inject.Inject

// ==================== ViewModel ====================

data class DailyReportUiState(
    val loading: Boolean = true,
    val exporting: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val rangeLabel: String = "最近 7 天",
    val startDate: String = "",
    val endDate: String = "",
    val preview: DailyReportPreviewDto? = null,
    /** 导出成功但缺少存储权限时暂存的待下载记录，由 UI 申请权限后重试 */
    val pendingDownload: ExportRecordDto? = null
)

@HiltViewModel
class DailyReportViewModel @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DailyReportUiState())
    val state: StateFlow<DailyReportUiState> = _state

    init { load("最近 7 天") }

    fun refresh() = load(_state.value.rangeLabel)

    /** 按预置区间加载：最近 7 天 / 最近 30 天 / 本月 */
    fun load(rangeLabel: String) {
        val today = LocalDate.now()
        val (start, end) = when (rangeLabel) {
            "最近 30 天" -> today.minusDays(29) to today
            "本月" -> today.withDayOfMonth(1) to today
            else -> today.minusDays(6) to today
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true, error = null, message = null,
                rangeLabel = rangeLabel, startDate = start.toString(), endDate = end.toString()
            )
            when (val res = statisticsRepository.getDailyReportPreview(start.toString(), end.toString())) {
                is Resource.Success -> _state.value = _state.value.copy(
                    loading = false,
                    preview = res.data
                )
                is Resource.Error -> _state.value = _state.value.copy(
                    loading = false,
                    error = res.message
                )
                Resource.Loading -> Unit
            }
        }
    }

    /** 导出 Excel（POST /daily-report/export）：有存储权限则接着下载保存，否则暂存记录交由 UI 申请权限 */
    fun exportAndSave(context: Context) {
        val s = _state.value
        if (s.startDate.isBlank() || s.endDate.isBlank()) return
        if (s.exporting) return // 防重入：上一笔导出未完成时不再发起新导出
        viewModelScope.launch {
            _state.value = s.copy(exporting = true, message = null, pendingDownload = null)
            when (val exportRes = statisticsRepository.exportDailyReport(s.startDate, s.endDate)) {
                is Resource.Success -> {
                    val record = exportRes.data
                    val id = record.id
                    if (id == null) {
                        _state.value = _state.value.copy(exporting = false, message = "导出成功（未返回记录ID，请稍后在历史中下载）")
                        return@launch
                    }
                    if (DownloadSaver.hasWritePermission(context)) {
                        downloadAndSave(context, record)
                    } else {
                        // 缺少写存储权限：暂存待下载记录，由 UI 申请权限后自动重试
                        _state.value = _state.value.copy(exporting = false, pendingDownload = record)
                    }
                }
                is Resource.Error -> _state.value = _state.value.copy(
                    exporting = false,
                    message = "导出失败：${exportRes.message}"
                )
                Resource.Loading -> Unit
            }
        }
    }

    /** 权限授予后自动重试：下载暂存的导出记录并保存到系统「下载」目录 */
    fun downloadPending(context: Context) {
        val record = _state.value.pendingDownload ?: return
        _state.value = _state.value.copy(pendingDownload = null)
        downloadAndSave(context, record)
    }

    /** 放弃待下载记录（权限被拒时调用） */
    fun clearPendingDownload() {
        if (_state.value.pendingDownload != null) {
            _state.value = _state.value.copy(pendingDownload = null)
        }
    }

    /** GET /daily-report/download/{id} → 通过 DownloadSaver 保存到系统公共「下载」目录 */
    private fun downloadAndSave(context: Context, record: ExportRecordDto) {
        val id = record.id ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(exporting = true, message = null)
            when (val dlRes = statisticsRepository.downloadExport(id)) {
                is Resource.Success -> {
                    val fileName = record.fileName?.takeIf { it.isNotBlank() }
                        ?: "施工日报_${_state.value.startDate}_${_state.value.endDate}.xlsx"
                    val result = runCatching {
                        dlRes.data.use { body ->
                            DownloadSaver.saveToDownloads(
                                context,
                                fileName,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                body.byteStream()
                            )
                        }
                    }.fold(
                        onSuccess = { "已保存到 $it" },
                        onFailure = { "保存文件失败：${it.message}" }
                    )
                    _state.value = _state.value.copy(exporting = false, message = result)
                }
                is Resource.Error -> _state.value = _state.value.copy(
                    exporting = false,
                    message = "导出成功，下载失败：${dlRes.message}"
                )
                Resource.Loading -> Unit
            }
        }
    }
}

// ==================== JsonElement 取值辅助 ====================

private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNullSafe()

private fun JsonPrimitive.contentOrNullSafe(): String? = try { content } catch (_: Exception) { null }

private fun JsonElement?.int(): Int? = str()?.toIntOrNull()

private fun Map<String, JsonElement>.str(key: String): String? = this[key].str()?.takeIf { it != "null" }

private fun Map<String, JsonElement>.int(key: String): Int? = this[key].int()

// ==================== UI ====================

private val RANGE_OPTIONS = listOf("最近 7 天", "最近 30 天", "本月")

/**
 * 施工日报页 —— 对接后端 GET /daily-report/preview + POST /daily-report/export + GET /daily-report/download/{id}：
 * 全局概览指标 → 各项目甘特图进度汇总 → 任务级明细（可折叠），右上角导出 Excel（.xlsx）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportScreen(
    onBack: () -> Unit = {},
    viewModel: DailyReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 待下载导出记录 id 暂存：权限授予后据此自动重试下载
    var pendingDownloadId by remember { mutableStateOf<Long?>(null) }

    // 写存储权限申请（API 26~28 写公共下载目录需要；拒绝则无法保存文件）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val hasPending = pendingDownloadId != null
        pendingDownloadId = null
        if (granted && hasPending) {
            viewModel.downloadPending(context) // 授权后自动重试下载
        } else {
            viewModel.clearPendingDownload()
            Toast.makeText(context, "需要存储权限才能保存文件", Toast.LENGTH_SHORT).show()
        }
    }

    // 导出成功但缺少存储权限：暂存记录 id 并发起权限申请
    val pendingRecord = state.pendingDownload
    LaunchedEffect(pendingRecord) {
        if (pendingRecord != null) {
            pendingDownloadId = pendingRecord.id
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("施工日报", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.exportAndSave(context) },
                        enabled = !state.exporting && !state.loading
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = "导出 Excel",
                            tint = if (state.exporting) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val preview = state.preview
        // 任务明细按项目分组：remember 缓存分组结果，避免 LazyColumn 内容每次重组重复分组
        val tasksByProject = remember(preview?.taskProgress) {
            preview?.taskProgress.orEmpty().groupBy { it.str("projectId") ?: "" }
        }
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(
                Modifier.padding(padding),
                message = state.error ?: "加载失败",
                onRetry = viewModel::refresh
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ===== 时间范围选择器 + 导出状态 =====
                item(key = "range") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RangeSelector(
                            current = state.rangeLabel,
                            onSelect = viewModel::load
                        )
                        Spacer(Modifier.weight(1f))
                        if (state.exporting) {
                            Text(
                                "正在导出…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    state.message?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (preview == null || (preview.projectSummary.isEmpty() && preview.taskProgress.isEmpty())) {
                    item(key = "empty") { EmptyState(text = "暂无日报数据") }
                } else {
                    // ===== 全局概览（summary） =====
                    val summary = preview.summary
                    item(key = "summary_header") { SectionHeader(title = "进度概览") }
                    item(key = "summary") {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                SummaryRow(
                                    "项目 ${summary.int("totalProjects") ?: 0}",
                                    "任务 ${summary.int("totalTasks") ?: 0}",
                                    "已完成 ${summary.int("completedTasks") ?: 0}"
                                )
                                Spacer(Modifier.height(6.dp))
                                SummaryRow(
                                    "进行中 ${summary.int("inProgressTasks") ?: 0}",
                                    "未开始 ${summary.int("pendingTasks") ?: 0}",
                                    "延期 ${summary.int("delayedTasks") ?: 0}"
                                )
                            }
                        }
                    }

                    // ===== 各项目进度汇总 + 任务明细（分组结果已在上方 remember 缓存） =====
                    preview.projectSummary.forEachIndexed { idx, project ->
                        val pid = project.str("projectId") ?: ""
                        item(key = "project_$idx") {
                            ProjectSummaryCard(project)
                        }
                        val tasks = tasksByProject[pid].orEmpty()
                        if (tasks.isNotEmpty()) {
                            // key 回退用 index：taskId 缺失时 Map.hashCode 可能重复，会造成 LazyColumn duplicate key 崩溃
                            itemsIndexed(tasks, key = { i, t -> "task_${t.str("taskId") ?: "idx_$i"}_$pid" }) { _, task ->
                                TaskProgressCard(task)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeSelector(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(vertical = 4.dp)
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                current,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "选择时间范围",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RANGE_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(vararg texts: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        texts.forEach { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** 项目级甘特图进度汇总卡（projectSummary Map） */
@Composable
private fun ProjectSummaryCard(project: Map<String, JsonElement>) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = project.str("projectName") ?: "-",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusTag(
                    text = project.str("status") ?: "-",
                    tone = statusToneOf(project.str("status"))
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    project.str("projectCode")?.takeIf { it.isNotBlank() },
                    project.str("managerName")?.takeIf { it.isNotBlank() }?.let { "经理 $it" },
                    project.str("endDate")?.takeIf { it.isNotBlank() }?.let { "计划完工 $it" }
                ).joinToString(" · ").ifBlank { "-" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "整体进度",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                LinearProgressIndicator(
                    progress = { (project.int("progress") ?: 0) / 100f },
                    modifier = Modifier.weight(1f).height(6.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${project.int("progress") ?: 0}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            val total = project.int("taskTotal") ?: 0
            val done = project.int("taskCompleted") ?: 0
            val inProg = project.int("taskInProgress") ?: 0
            val pending = project.int("taskPending") ?: 0
            val delayed = project.int("taskDelayed") ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "任务 $total（完成 $done · 进行 $inProg · 未开始 $pending）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (delayed > 0) {
                    StatusTag(text = "延期 $delayed", tone = TagTone.DANGER)
                }
            }
        }
    }
}

/** 任务级甘特图进度明细卡（taskProgress Map） */
@Composable
private fun TaskProgressCard(task: Map<String, JsonElement>) {
    val isDelayed = (task.int("isDelayed") ?: 0) == 1
    val isMilestone = (task.int("isMilestone") ?: 0) == 1
    val isKeyTask = (task.int("isKeyTask") ?: 0) == 1
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isDelayed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMilestone || isKeyTask) {
                    Icon(
                        Icons.Outlined.Flag,
                        contentDescription = if (isMilestone) "里程碑" else "关键任务",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = task.str("taskName") ?: "-",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${task.int("progress") ?: 0}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = listOfNotNull(
                        task.str("startDate")?.takeIf { it.isNotBlank() },
                        task.str("endDate")?.takeIf { it.isNotBlank() }?.let { "至 $it" }
                    ).joinToString(" ").ifBlank { "计划日期未设置" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusTag(
                    text = task.str("status") ?: "-",
                    tone = statusToneOf(task.str("status"))
                )
            }
            val assignee = task.str("assigneeName")
            val delayDays = task.int("delayDays") ?: 0
            if (!assignee.isNullOrBlank() || isDelayed) {
                Spacer(Modifier.height(4.dp))
                Row {
                    if (!assignee.isNullOrBlank()) {
                        Text(
                            text = "负责人 $assignee",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                    }
                    if (isDelayed) {
                        StatusTag(text = "已延期 $delayDays 天", tone = TagTone.DANGER)
                    }
                }
            }
        }
    }
}
