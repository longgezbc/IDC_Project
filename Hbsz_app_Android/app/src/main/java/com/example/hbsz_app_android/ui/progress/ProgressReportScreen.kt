package com.example.hbsz_app_android.ui.progress

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import coil.compose.AsyncImage
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.data.remote.dto.MyTaskItemDto
import com.example.hbsz_app_android.data.remote.dto.ProgressReportRequest
import com.example.hbsz_app_android.data.repository.DashboardRepository
import com.example.hbsz_app_android.data.repository.TaskRepository
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.SectionHeader
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.statusToneOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** 已上传的施工照片（本地 Uri 预览 + 服务端相对路径） */
data class UploadedPhoto(val localUri: Uri, val remotePath: String)

data class ProgressReportUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val tasks: List<MyTaskItemDto> = emptyList(),
    val selectedTaskId: Long = -1L,
    val uploading: Boolean = false,
    val submitting: Boolean = false,
    val photos: List<UploadedPhoto> = emptyList(),
    val submitError: String? = null,
    val submitted: Boolean = false
)

@HiltViewModel
class ProgressReportViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressReportUiState())
    val state: StateFlow<ProgressReportUiState> = _state

    init {
        loadTasks()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            when (val res = dashboardRepository.getMyTasks(pageSize = 50)) {
                is Resource.Success -> {
                    val reportable = res.data.records.filter { it.status != "已完成" }
                    _state.value = _state.value.copy(
                        loading = false,
                        error = null,
                        tasks = reportable,
                        selectedTaskId = reportable.firstOrNull()?.id ?: -1L
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

    fun selectTask(id: Long) {
        _state.value = _state.value.copy(selectedTaskId = id)
    }

    fun removePhoto(photo: UploadedPhoto) {
        _state.value = _state.value.copy(photos = _state.value.photos - photo)
    }

    /** 上传选中的图片（Uri → 缓存文件 → POST /files/upload） */
    fun uploadPhotos(context: android.content.Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.value = _state.value.copy(uploading = true, submitError = null)
        viewModelScope.launch {
            val uploaded = mutableListOf<UploadedPhoto>()
            for (uri in uris) {
                val file = withContext(Dispatchers.IO) { uri.toCacheFile(context) }
                if (file == null) {
                    _state.value = _state.value.copy(
                        uploading = false,
                        submitError = "读取图片失败"
                    )
                    return@launch
                }
                // 优先取系统记录的真实 MIME（png/heic 等），取不到再按扩展名推断，兜底 image/jpeg
                val mime = withContext(Dispatchers.IO) {
                    context.contentResolver.getType(uri) ?: guessImageMime(uri.lastPathSegment ?: "")
                }
                try {
                    when (val res = taskRepository.uploadImage(file, mime)) {
                        is Resource.Success -> uploaded.add(UploadedPhoto(uri, res.data))
                        is Resource.Error -> {
                            _state.value = _state.value.copy(
                                uploading = false,
                                submitError = "图片上传失败：${res.message}"
                            )
                            return@launch
                        }

                        Resource.Loading -> Unit
                    }
                } finally {
                    file.delete()
                }
            }
            _state.value = _state.value.copy(
                uploading = false,
                photos = _state.value.photos + uploaded
            )
        }
    }

    /** 校验失败时更新错误提示（供 UI 扩展函数调用） */
    fun setSubmitError(msg: String?) {
        _state.value = _state.value.copy(submitError = msg)
    }

    /** PUT /tasks/{id}/progress 提交填报（taskId 由 UI 传入当前展示的任务，防止提交到默认选中项） */
    fun submit(
        taskId: Long,
        progress: Int,
        remark: String,
        actualStartDate: String,
        actualEndDate: String,
        onDone: () -> Unit = {}
    ) {
        val st = _state.value
        if (st.submitting) return
        if (taskId <= 0L) { // 防御：无有效任务直接拦截
            _state.value = st.copy(submitError = "请先选择任务")
            return
        }
        _state.value = st.copy(submitting = true, submitError = null)
        viewModelScope.launch {
            val body = ProgressReportRequest(
                progress = progress,
                remark = remark.takeIf { it.isNotBlank() },
                images = st.photos.map { it.remotePath },
                actualStartDate = actualStartDate,
                actualEndDate = actualEndDate
            )
            when (val res = taskRepository.reportProgress(taskId, body)) {
                is Resource.Success -> {
                    _state.value = _state.value.copy(submitting = false, submitted = true)
                    onDone()
                }

                is Resource.Error -> _state.value = _state.value.copy(
                    submitting = false,
                    submitError = res.message
                )

                Resource.Loading -> Unit
            }
        }
    }

    private fun Uri.toCacheFile(context: android.content.Context): File? = runCatching {
        val input = context.contentResolver.openInputStream(this) ?: return null
        val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        input.use { src -> file.outputStream().use { src.copyTo(it) } }
        file
    }.getOrNull()
}

/**
 * 进度填报页 —— 真实接口：
 * 任务列表 GET /dashboard/my-tasks；图片上传 POST /files/upload；提交 PUT /tasks/{id}/progress。
 * 提交后进入待审核流（项目经理审核通过才更新任务进度）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressReportScreen(
    taskId: Long? = null,
    onBack: () -> Unit = {},
    viewModel: ProgressReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val today = rememberSaveable { LocalDate.now().toString() }

    var progress by rememberSaveable { mutableIntStateOf(60) }
    var description by rememberSaveable { mutableStateOf("") }
    var actualStartDate by rememberSaveable { mutableStateOf(today) }
    var actualEndDate by rememberSaveable { mutableStateOf(today) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6)
    ) { uris -> viewModel.uploadPhotos(context, uris) }

    val task = state.tasks.firstOrNull { it.id == (taskId ?: state.selectedTaskId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("进度填报", fontWeight = FontWeight.SemiBold) },
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
                modifier = Modifier.padding(padding),
                message = state.error ?: "加载失败"
            )

            state.tasks.isEmpty() -> EmptyState(
                modifier = Modifier.padding(padding),
                text = "暂无可填报的任务"
            )

            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ===== 选择任务 =====
                SectionHeader(title = "选择任务")
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        task?.let {
                            Text(
                                text = it.projectName ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        // 全量任务下拉选择器（内部可滚动），确保所有任务都能被选中填报
                        var taskMenuExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = taskMenuExpanded,
                            onExpandedChange = { taskMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = task?.name ?: "请选择任务",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("任务（共 ${state.tasks.size} 个）") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = taskMenuExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = taskMenuExpanded,
                                onDismissRequest = { taskMenuExpanded = false }
                            ) {
                                // 注意：菜单内容不能用 LazyColumn——ExposedDropdownMenu 内部按
                                // IntrinsicSize.Min 测宽度，Lazy 布局不支持 intrinsic 测量会直接崩溃。
                                // 任务量为几十级，普通 Column + verticalScroll 足够。
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = 320.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    state.tasks.forEach { t ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = t.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = "进度 ${t.progress}%",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.selectTask(t.id)
                                                taskMenuExpanded = false
                                            },
                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                        )
                                    }
                                }
                            }
                        }
                        task?.let {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "当前进度 ${it.progress}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                StatusTag(text = it.status, tone = statusToneOf(it.status))
                            }
                        }
                    }
                }

                // ===== 上报进度 =====
                SectionHeader(title = "本期上报进度")
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$progress%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (task != null && progress < task.progress) "低于当前进度，请确认"
                                else "预计审核通过后更新任务进度",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = progress.toFloat(),
                            onValueChange = { progress = it.toInt() },
                            valueRange = 0f..100f
                        )
                    }
                }

                // ===== 实际起止时间（后端必填） =====
                SectionHeader(title = "实际施工起止日期")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = actualStartDate,
                        onValueChange = { actualStartDate = it },
                        label = { Text("实际开工(YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = actualEndDate,
                        onValueChange = { actualEndDate = it },
                        label = { Text("实际结束(YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ===== 施工说明 =====
                SectionHeader(title = "施工说明")
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("简述本期完成的工作内容（如：完成 B 区桥架安装 120 米）") },
                    minLines = 3
                )

                // ===== 施工照片（必传，真实上传） =====
                SectionHeader(title = "施工照片（必传）")
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (state.photos.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "点击添加现场施工照片",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.photos.take(3).forEach { photo ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(84.dp)
                                    ) {
                                        AsyncImage(
                                            model = photo.localUri,
                                            contentDescription = "施工照片",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                    RoundedCornerShape(8.dp)
                                                )
                                        )
                                        IconButton(
                                            onClick = { viewModel.removePhoto(photo) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(28.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surface,
                                                    RoundedCornerShape(50)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = "移除照片",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            if (state.photos.size > 3) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "已选 ${state.photos.size} 张（继续点击下方按钮可追加）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !state.uploading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.uploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("上传中…")
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.photos.isEmpty()) "添加照片" else "追加照片")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "至少上传 1 张现场施工照片，审核通过后进度才会更新",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 错误提示
                state.submitError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // ===== 提交 =====
                Button(
                    onClick = {
                        // 区分「格式不合法」与「结束早于开始」两种错误，分别提示
                        val startDate = runCatching { LocalDate.parse(actualStartDate) }.getOrNull()
                        val endDate = runCatching { LocalDate.parse(actualEndDate) }.getOrNull()
                        when {
                            state.photos.isEmpty() -> viewModel.submitError("请至少添加 1 张施工照片")
                            startDate == null || endDate == null ->
                                viewModel.submitError("日期格式应为 YYYY-MM-DD")
                            endDate < startDate -> viewModel.submitError("结束日期不能早于开工日期")
                            // task 为 null 含两种情况：未选择任务；导航传入的 taskId 不在可填报列表（如已完成）
                            task == null -> viewModel.submitError(
                                if (taskId != null) "该任务当前不可填报（可能已完成），请从任务列表重新进入"
                                else "请先选择任务"
                            )

                            else -> viewModel.submit(task.id, progress, description, actualStartDate, actualEndDate)
                        }
                    },
                    enabled = !state.submitted && !state.submitting,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        when {
                            state.submitted -> "已提交，等待项目经理审核"
                            state.submitting -> "提交中…"
                            else -> "提交填报"
                        }
                    )
                }

                if (state.submitted) {
                    Text(
                        text = "填报已进入审核流：项目经理审核通过后将更新任务与项目整体进度，可在「填报记录」中跟踪状态。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 设置提交错误（供校验失败提示），写入状态供 UI 展示 */
private fun ProgressReportViewModel.submitError(msg: String) {
    setSubmitError(msg)
}

/** 按扩展名推断图片 MIME 类型（ContentResolver 查不到真实类型时的兜底），默认 image/jpeg */
private fun guessImageMime(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }
}
