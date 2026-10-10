package com.example.hbsz_app_android.ui.document

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.common.DownloadSaver
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.data.remote.dto.DocumentDto
import com.example.hbsz_app_android.data.repository.DocumentRepository
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.TagTone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val documents: List<DocumentDto> = emptyList(),
    val filter: String = "全部",
    /** 分页状态：当前页码 / 是否还有更多 / 是否正在加载下一页 */
    val pageNum: Int = 1,
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false,
    /** 正在下载的文档ID */
    val downloadingId: String? = null,
    /** 下载结果提示（成功含保存路径 / 失败原因） */
    val message: String? = null
)

@HiltViewModel
class DocumentViewModel @Inject constructor(
    private val documentRepository: DocumentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(DocumentUiState())
    val state: StateFlow<DocumentUiState> = _state

    companion object {
        private const val PAGE_SIZE = 20
    }

    init {
        load()
    }

    fun setFilter(filter: String) {
        _state.value = _state.value.copy(filter = filter)
        load()
    }

    fun refresh() = load()

    /**
     * 分页代次：筛选/刷新时自增。
     * 进行中的 loadMore 若持旧代次返回，响应作废，防止旧筛选的数据 append 到新列表（竞态）。
     */
    private var loadEpoch = 0L

    /** 重置到第一页并整体替换列表（筛选切换 / 刷新共用） */
    private fun load() {
        loadEpoch++
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, loadingMore = false)
            val status = _state.value.filter.takeIf { it != "全部" }
            when (val res = documentRepository.getDocuments(pageNum = 1, pageSize = PAGE_SIZE, status = status)) {
                is Resource.Success -> _state.value = _state.value.copy(
                    loading = false,
                    documents = res.data.records,
                    pageNum = 1,
                    hasMore = PAGE_SIZE < res.data.total
                )
                is Resource.Error -> _state.value = _state.value.copy(
                    loading = false,
                    error = res.message
                )
                Resource.Loading -> Unit
            }
        }
    }

    /** 加载下一页，成功后追加到列表尾部 */
    fun loadMore() {
        val cur = _state.value
        if (cur.loadingMore || !cur.hasMore || cur.loading) return
        val epoch = loadEpoch
        viewModelScope.launch {
            if (epoch != loadEpoch) return@launch // 筛选已切换，放弃本次追加
            _state.value = _state.value.copy(loadingMore = true)
            val nextPage = _state.value.pageNum + 1
            val status = _state.value.filter.takeIf { it != "全部" }
            when (val res = documentRepository.getDocuments(pageNum = nextPage, pageSize = PAGE_SIZE, status = status)) {
                is Resource.Success -> {
                    if (epoch != loadEpoch) { // 响应返回时筛选已变，丢弃过期数据
                        _state.value = _state.value.copy(loadingMore = false)
                        return@launch
                    }
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        documents = _state.value.documents + res.data.records,
                        pageNum = nextPage,
                        hasMore = nextPage * PAGE_SIZE < res.data.total
                    )
                }
                is Resource.Error -> {
                    if (epoch != loadEpoch) {
                        _state.value = _state.value.copy(loadingMore = false)
                        return@launch
                    }
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        message = "加载更多失败：${res.message}"
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** GET /documents/{id}/download → 保存到系统公共「下载」目录（调用方需先确保存储权限） */
    fun download(context: Context, doc: DocumentDto) {
        val id = doc.id.toLongOrNull() ?: return
        if (_state.value.downloadingId != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(downloadingId = doc.id, message = null)
            val fileName = doc.name.ifBlank { "document_${doc.id}" }
            val mime = guessMimeType(fileName)
            val result: String? = when (val res = documentRepository.download(id)) {
                is Resource.Success -> res.data.use { body ->
                    // body.use 保证 ResponseBody 在复制完成后关闭
                    runCatching {
                        DownloadSaver.saveToDownloads(context, fileName, mime, body.byteStream())
                    }.map { "已保存到 $it" }
                        .getOrElse { "保存文件失败：${it.message}" }
                }
                is Resource.Error -> "下载失败：${res.message}"
                Resource.Loading -> null // 加载中不设置提示，避免空白 Snackbar
            }
            _state.value = _state.value.copy(downloadingId = null, message = result)
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}

/**
 * 项目资料页 —— 对齐 Web 端 documents/DocumentManagement.vue：
 * 文档列表（文件类型图标/所属项目/大小/上传人/审核状态），按审核状态筛选。
 * 数据源：GET /documents（document:query 权限）；下载走流式接口保存到本地。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    onBack: () -> Unit = {},
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 下载结果用 Snackbar 展示
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // API 26~28 写公共下载目录需要存储权限：申请授权后自动继续下载
    var pendingDownloadDoc by remember { mutableStateOf<DocumentDto?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val doc = pendingDownloadDoc
        pendingDownloadDoc = null
        if (granted) {
            doc?.let { viewModel.download(context, it) }
        } else {
            Toast.makeText(context, "需要存储权限才能保存文件", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestDownload(doc: DocumentDto) {
        if (DownloadSaver.hasWritePermission(context)) {
            viewModel.download(context, doc)
        } else {
            // 暂存待下载文档，授权成功后继续下载
            pendingDownloadDoc = doc
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("项目资料", fontWeight = FontWeight.SemiBold) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf("全部", "待审核", "已审核", "已驳回")) { option ->
                    FilterChip(
                        selected = state.filter == option,
                        onClick = { viewModel.setFilter(option) },
                        label = { Text(option) }
                    )
                }
            }

            when {
                state.loading -> LoadingState(Modifier.fillMaxSize())
                state.error != null -> ErrorState(
                    message = state.error ?: "加载失败",
                    modifier = Modifier.fillMaxSize(),
                    onRetry = { viewModel.refresh() }
                )
                state.documents.isEmpty() -> EmptyState(text = "暂无${state.filter}文档")
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
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.documents, key = { it.id }) { doc ->
                            DocumentCard(
                                doc = doc,
                                downloading = state.downloadingId == doc.id,
                                onDownload = { requestDownload(doc) }
                            )
                        }
                        // 末尾 footer：加载中 / 没有更多
                        if (state.loadingMore || (!state.hasMore && state.documents.isNotEmpty())) {
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
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

/** 根据文件扩展名推断 MIME 类型，兜底 application/octet-stream */
private fun guessMimeType(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> "application/pdf"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "xls" -> "application/vnd.ms-excel"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "zip" -> "application/zip"
        "rar" -> "application/vnd.rar"
        else -> "application/octet-stream"
    }
}

/** 文件类型 → 图标（对齐 Web 端文档类型图标映射） */
private fun fileTypeIcon(fileType: String?): ImageVector = when (fileType?.lowercase()) {
    "pdf" -> Icons.Outlined.PictureAsPdf
    "xlsx", "xls", "csv" -> Icons.Outlined.Description
    "png", "jpg", "jpeg" -> Icons.Outlined.Image
    "zip", "rar" -> Icons.Outlined.FolderZip
    else -> Icons.Outlined.Description
}

@Composable
private fun DocumentCard(
    doc: DocumentDto,
    downloading: Boolean,
    onDownload: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fileTypeIcon(doc.fileType),
                    contentDescription = doc.fileType,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = doc.projectName ?: "未关联项目",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = listOfNotNull(
                            doc.size,
                            doc.uploader,
                            doc.uploadDate?.take(10)
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                StatusTag(
                    text = doc.status,
                    tone = when (doc.status) {
                        "待审核" -> TagTone.WARNING
                        "已驳回" -> TagTone.DANGER
                        else -> TagTone.SUCCESS
                    }
                )
                Spacer(Modifier.height(8.dp))
                if (downloading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = "下载",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
