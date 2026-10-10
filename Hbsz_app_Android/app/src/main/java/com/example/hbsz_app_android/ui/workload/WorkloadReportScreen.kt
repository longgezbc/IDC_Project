package com.example.hbsz_app_android.ui.workload

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.data.remote.dto.FrequentItemDto
import com.example.hbsz_app_android.data.remote.dto.MyProjectBrief
import com.example.hbsz_app_android.data.remote.dto.WorkloadRecordEntry
import com.example.hbsz_app_android.data.repository.WorkloadRepository
import com.example.hbsz_app_android.ui.components.DateField
import com.example.hbsz_app_android.ui.components.DropdownField
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.FeedbackSnackbarHost
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.PullRefreshBox
import com.example.hbsz_app_android.ui.components.SectionHeader
import com.example.hbsz_app_android.ui.theme.Success
import com.example.hbsz_app_android.ui.theme.Warning
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 快速填报卡片（UI 模型）：常用项 + 会话内编辑状态。
 * locked = approved（已审核锁定只读）；rejected 附驳回原因可改后重提。
 */
data class QuickCard(
    val itemId: Long,
    val category: String?,
    val workScope: String?,
    val unit: String?,
    val ratedWorkload: Double?,
    val fillCount: Int,
    val lastQuantity: Double?,
    val lastDate: String?,
    val yesterdayQty: Double?,
    val reviewStatus: String?,   // null=今日未填 / pending / approved / rejected
    val reviewRemark: String?,
    val locked: Boolean,
    var value: Double            // 当前编辑值（>0 才提交）
)

data class WorkloadReportUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val recordDate: String = LocalDate.now().toString(),
    /** 我参与的项目组（未归档），单项目自动选中 */
    val projects: List<MyProjectBrief> = emptyList(),
    val selectedProjectId: Long? = null,
    val selectedProjectName: String? = null,
    val myUserId: Long = 0,
    /** 卡片：常用项 ∪ 今日已填 ∪ 复制昨天补入 */
    val cards: List<QuickCard> = emptyList(),
    val totalItemCount: Int = 0,
    val submitting: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false
)

@HiltViewModel
class WorkloadReportViewModel @Inject constructor(
    private val workloadRepository: WorkloadRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WorkloadReportUiState())
    val state: StateFlow<WorkloadReportUiState> = _state

    init {
        loadContext()
    }

    /**
     * 加载快速填报上下文：常用项（近 14 天频次序）+ 今日回显 + 昨日数量。
     * 首次不带 projectId 拿项目列表，单项目自动选中后带 id 二次请求。
     */
    fun loadContext(projectId: Long? = _state.value.selectedProjectId) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = workloadRepository.getMyReportContext(projectId, _state.value.recordDate)) {
                is Resource.Success -> {
                    val data = res.data
                    // 未指定项目且单项目：自动选中后二次请求拿完整上下文
                    if (projectId == null && data.projects.size == 1) {
                        loadContext(data.projects[0].projectId)
                        return@launch
                    }
                    val cards = buildCards(data.frequentItems, data.todayRecords, data.yesterdayRecords)
                    _state.update {
                        it.copy(
                            loading = false,
                            myUserId = data.myUserId,
                            projects = data.projects,
                            selectedProjectId = projectId ?: data.projectId,
                            selectedProjectName = data.projectName,
                            cards = cards,
                            totalItemCount = data.totalItemCount
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

    /** 卡片 = 常用项 ∪ 今日已填项（后者含已驳回提醒），值回显今日数量 */
    private fun buildCards(
        frequent: List<FrequentItemDto>,
        today: Map<String, com.example.hbsz_app_android.data.remote.dto.WorkloadCellDto>,
        yesterday: Map<String, Double>
    ): List<QuickCard> {
        val result = mutableListOf<QuickCard>()
        val seen = mutableSetOf<Long>()
        for (fi in frequent) {
            seen.add(fi.itemId)
            result.add(fi.toCard(today, yesterday))
        }
        // 今日已填但不在常用列表的项（含驳回重填提醒）
        for ((key, cell) in today) {
            val id = key.toLongOrNull() ?: continue
            if (id in seen) continue
            result.add(
                QuickCard(
                    itemId = id,
                    category = "今日填报",
                    workScope = "（清单项 #$id）",
                    unit = null,
                    ratedWorkload = null,
                    fillCount = 0,
                    lastQuantity = null,
                    lastDate = null,
                    yesterdayQty = yesterday[key],
                    reviewStatus = cell.reviewStatus,
                    reviewRemark = cell.reviewRemark,
                    locked = cell.reviewStatus == "approved",
                    value = cell.quantity
                )
            )
        }
        return result
    }

    private fun FrequentItemDto.toCard(
        today: Map<String, com.example.hbsz_app_android.data.remote.dto.WorkloadCellDto>,
        yesterday: Map<String, Double>
    ): QuickCard {
        val cell = today[itemId.toString()]
        return QuickCard(
            itemId = itemId,
            category = category,
            workScope = workScope,
            unit = unit,
            ratedWorkload = ratedWorkload,
            fillCount = fillCount,
            lastQuantity = lastQuantity,
            lastDate = lastDate,
            yesterdayQty = yesterday[itemId.toString()],
            reviewStatus = cell?.reviewStatus,
            reviewRemark = cell?.reviewRemark,
            locked = cell?.reviewStatus == "approved",
            value = cell?.quantity ?: 0.0
        )
    }

    /** 切换日期：重载上下文（今日回显/昨日量随日期变化） */
    fun changeDate(date: String) {
        if (date == _state.value.recordDate) return
        _state.update { it.copy(recordDate = date) }
        loadContext()
    }

    fun selectProject(projectId: Long) {
        loadContext(projectId)
    }

    /** 更新卡片值（输入框） */
    fun updateValue(itemId: Long, value: Double) {
        _state.update { st ->
            st.copy(cards = st.cards.map { if (it.itemId == itemId) it.copy(value = value) else it })
        }
    }

    /** 步进 ±（步长：昨日量 → 常填量 → 1，不小于 0） */
    fun step(itemId: Long, direction: Int) {
        val card = _state.value.cards.firstOrNull { it.itemId == itemId } ?: return
        if (card.locked) return
        val unit = card.yesterdayQty ?: card.lastQuantity ?: 1.0
        val next = ((card.value + direction * unit * 100).toInt() / 100.0).coerceAtLeast(0.0)
        updateValue(itemId, next)
    }

    /** 复制昨天：空值非锁定卡片带入昨日量（已有值不覆盖，可先清零再复制） */
    fun copyYesterday() {
        val st = _state.value
        var copied = 0
        val newCards = st.cards.map { card ->
            if (!card.locked && card.value == 0.0 && card.yesterdayQty != null) {
                copied++
                card.copy(value = card.yesterdayQty!!)
            } else card
        }
        if (copied > 0) {
            _state.update { it.copy(cards = newCards, message = "已复制 $copied 项昨日数量，可微调后提交", messageIsError = false) }
        } else {
            _state.update { it.copy(message = "没有可复制的昨日数量（卡片已有值或已锁定）", messageIsError = true) }
        }
    }

    fun retry() = loadContext()

    /** 提交：仅非锁定且 >0 的卡片，保存后进入待审核 */
    fun submit() {
        val st = _state.value
        if (st.submitting) return
        val projectId = st.selectedProjectId ?: run {
            _state.update { it.copy(message = "请先选择项目", messageIsError = true) }
            return
        }
        if (st.myUserId <= 0) {
            _state.update { it.copy(message = "登录信息异常，请重新进入页面", messageIsError = true) }
            return
        }
        val entries = st.cards.filter { !it.locked && it.value > 0 }
            .map { WorkloadRecordEntry(itemId = it.itemId, workerUserId = st.myUserId, quantity = it.value) }
        if (entries.isEmpty()) {
            _state.update { it.copy(message = "请至少填写一项数量", messageIsError = true) }
            return
        }
        _state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val res = workloadRepository.saveRecords(projectId, st.recordDate, entries)) {
                is Resource.Success -> {
                    _state.update {
                        it.copy(submitting = false, message = "已提交 ${entries.size} 项，待项目经理审核", messageIsError = false)
                    }
                    loadContext(projectId) // 重载刷新审核状态
                }

                is Resource.Error -> _state.update {
                    it.copy(submitting = false, message = res.message, messageIsError = true)
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}

/**
 * 工作量快速填报页（工程师视角，2026-08 重构）—— 对齐 Web 端"快速填报"卡片流：
 * 常用项卡片（近 14 天填报频次排序）+ 一键复制昨天 + 步进微调 → 30 秒完成当日填报。
 * 审核语义与 Web/uni-app 端一致：approved 锁定、pending 可改、rejected 附原因可重提；
 * 保存复用 POST /workload/records（后端审核链路/成员校验单点不变）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkloadReportScreen(
    onBack: () -> Unit = {},
    viewModel: WorkloadReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工作量填报", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.retry() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
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
        bottomBar = {
            if (state.cards.isNotEmpty()) {
                val editedCount = state.cards.count { !it.locked && it.value > 0 }
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Button(
                        onClick = { viewModel.submit() },
                        enabled = !state.submitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            when {
                                state.submitting -> "提交中…"
                                editedCount > 0 -> "提交填报（$editedCount 项）"
                                else -> "提交填报"
                            }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        PullRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { viewModel.retry() },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ===== 筛选卡：项目（多项目时）+ 日期 + 复制昨天 =====
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(
                                title = "快速填报",
                                modifier = Modifier.weight(1f)
                            )
                            // 复制昨天（核心快捷路径）
                            OutlinedButton(
                                onClick = { viewModel.copyYesterday() },
                                enabled = state.cards.any { !it.locked && it.yesterdayQty != null }
                            ) {
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("复制昨天")
                            }
                        }
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (state.projects.size > 1) {
                                    val selected = state.projects.firstOrNull { it.projectId == state.selectedProjectId }
                                    DropdownField(
                                        label = "项目",
                                        options = state.projects,
                                        selected = selected,
                                        textOf = { it.projectName },
                                        onSelect = { viewModel.selectProject(it.projectId) }
                                    )
                                } else if (state.projects.size == 1) {
                                    Text(
                                        text = state.projects[0].projectName +
                                            (if (state.projects[0].roleInTeam == "leader") "（我是组长）" else ""),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DateField(
                                    label = "填报日期",
                                    date = state.recordDate,
                                    onDateChange = { viewModel.changeDate(it) }
                                )
                            }
                        }
                    }
                }

                // ===== 卡片流 =====
                when {
                    state.loading && state.cards.isEmpty() -> item { LoadingState(Modifier.fillMaxWidth()) }
                    state.error != null -> item {
                        ErrorState(
                            message = state.error ?: "加载失败",
                            modifier = Modifier.fillMaxWidth(),
                            onRetry = { viewModel.retry() }
                        )
                    }

                    state.projects.isEmpty() -> item {
                        EmptyState(
                            text = "您还未加入任何项目组",
                            hint = "请联系项目经理将您添加为项目组成员",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    state.cards.isEmpty() -> item {
                        EmptyState(
                            text = "暂无常用清单项",
                            hint = "首次使用请等待项目经理导入清单；填过一次后将自动成为常用项",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    else -> {
                        items(state.cards, key = { it.itemId }) { card ->
                            QuickReportCard(
                                card = card,
                                enabled = !state.submitting,
                                onValueChange = { viewModel.updateValue(card.itemId, it) },
                                onStep = { viewModel.step(card.itemId, it) }
                            )
                        }
                        // 底部提示：还有更多清单项
                        if (state.totalItemCount > state.cards.size) {
                            item {
                                Text(
                                    text = "已展示常用 ${state.cards.size} 项，项目清单共 ${state.totalItemCount} 项（Web 端可搜索添加更多）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 快速填报卡片：类型标签 + 状态 + 范围 + 元信息（昨日/常填/额定）+ 步进器 */
@Composable
private fun QuickReportCard(
    card: QuickCard,
    enabled: Boolean,
    onValueChange: (Double) -> Unit,
    onStep: (Int) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (card.reviewStatus == "approved") {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
            } else if (card.reviewStatus == "rejected") {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            // 标题行：类型 + 审核状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = card.category ?: "未分类",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = when (card.reviewStatus) {
                        "approved" -> "已通过"
                        "pending" -> "待审核"
                        "rejected" -> "已驳回"
                        else -> "今日未填"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (card.reviewStatus) {
                        "approved" -> Success
                        "pending" -> Warning
                        "rejected" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = card.workScope ?: "-",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // 元信息：常填 / 昨日 / 额定
            val metaText = buildList {
                if (card.fillCount > 0) add("常填 ${formatQty(card.lastQuantity)}${card.unit ?: ""}（近${card.fillCount}次）")
                card.yesterdayQty?.let { add("昨日 ${formatQty(it)}${card.unit ?: ""}") }
                card.ratedWorkload?.let { add("额定 ${formatQty(it)}${card.unit ?: ""}") }
            }.joinToString(" · ")
            if (metaText.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 驳回原因
            if (card.reviewStatus == "rejected" && !card.reviewRemark.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "驳回原因：${card.reviewRemark}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(10.dp))
            // 步进器：− [大数字输入] 单位 +
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onStep(-1) },
                    enabled = enabled && !card.locked && card.value > 0,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 6.dp
                    )
                ) { Text("−", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = if (card.value == 0.0) "" else formatQty(card.value),
                    onValueChange = { text -> onValueChange(text.toDoubleOrNull() ?: 0.0) },
                    enabled = enabled && !card.locked,
                    singleLine = true,
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (card.locked) Success else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = card.unit ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { onStep(1) },
                    enabled = enabled && !card.locked,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 6.dp
                    )
                ) { Text("+", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** 数量展示：整数去尾零（12.0 → "12"，12.5 → "12.5"） */
private fun formatQty(v: Double?): String {
    if (v == null) return "-"
    return if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
