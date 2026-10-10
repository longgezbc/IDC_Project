package com.example.hbsz_app_android.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.common.formatAmount
import com.example.hbsz_app_android.data.remote.dto.StatisticsOverviewDto
import com.example.hbsz_app_android.data.repository.StatisticsRepository
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.SectionHeader
import com.example.hbsz_app_android.ui.components.StatCard
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.statusTextOf
import com.example.hbsz_app_android.ui.components.statusToneOf
import com.example.hbsz_app_android.ui.theme.ChartPrimary
import com.example.hbsz_app_android.ui.theme.ChartStatusActive
import com.example.hbsz_app_android.ui.theme.ChartStatusDanger
import com.example.hbsz_app_android.ui.theme.ChartStatusDone
import com.example.hbsz_app_android.ui.theme.ChartStatusOther
import com.example.hbsz_app_android.ui.theme.ChartStatusWarning
import com.example.hbsz_app_android.ui.theme.Info
import com.example.hbsz_app_android.ui.theme.Primary
import com.example.hbsz_app_android.ui.theme.Success
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.entryOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ==================== ViewModel ====================

data class StatisticsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val overview: StatisticsOverviewDto? = null
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatisticsUiState())
    val state: StateFlow<StatisticsUiState> = _state

    init { load() }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val res = statisticsRepository.getOverview()) {
                is Resource.Success -> _state.value = StatisticsUiState(
                    loading = false,
                    overview = res.data
                )
                is Resource.Error -> _state.value = _state.value.copy(
                    loading = false,
                    error = res.message
                )
                Resource.Loading -> Unit
            }
        }
    }
}

// ==================== UI ====================

/** 状态名 → 图表语义色（对齐 Web 端 chartTheme.js 状态映射，低饱和色盲友好） */
private val toneColors: List<Pair<String, Color>> = listOf(
    "进行中" to ChartStatusActive,
    "已完成" to ChartStatusDone,
    "未开始" to ChartStatusOther,
    "已延期" to ChartStatusDanger,
    "已暂停" to ChartStatusWarning
)

private fun statusColor(name: String?): Color {
    val text = statusTextOf(name)
    // 未知状态回退中性灰（图表语义），与 Web 端 STATUS_SEMANTIC.other 对齐
    return toneColors.firstOrNull { it.first == text }?.second ?: ChartStatusOther
}

/**
 * 统计分析页 —— 对接后端 GET /statistics/overview：
 * 状态分布环形图（name/value）+ 核心指标 + 月度趋势柱状图（month/newCount/completedCount）。
 * 纯 Canvas 绘制（无图表库依赖）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val overview = state.overview

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计分析", fontWeight = FontWeight.SemiBold) },
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
            overview == null -> LoadingState(Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ===== 核心指标 =====
                SectionHeader(title = "核心指标")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        label = "项目总数",
                        value = "${overview.totalProjects}",
                        icon = Icons.Outlined.Folder,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "进行中",
                        value = "${overview.activeProjects}",
                        icon = Icons.AutoMirrored.Outlined.TrendingUp,
                        iconBg = Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        label = "已完成",
                        value = "${overview.completedProjects}",
                        icon = Icons.Outlined.CheckCircle,
                        iconBg = Success,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "已归档",
                        value = "${overview.archivedProjects}",
                        icon = Icons.Outlined.Archive,
                        iconBg = Info,
                        modifier = Modifier.weight(1f)
                    )
                }
                overview.totalContractAmount?.let { amount ->
                    StatCard(
                        label = "合同总金额",
                        value = formatAmount(amount),
                        icon = Icons.Outlined.Payments
                    )
                }

                // ===== 项目状态分布（环形图 + 图例） =====
                SectionHeader(title = "项目状态分布")
                PanelCard {
                    val dist = overview.statusDistribution.filter { it.value > 0 }
                    val total = dist.sumOf { it.value }.toFloat()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val stroke = size.minDimension * 0.16f
                                var startAngle = -90f
                                dist.forEach { item ->
                                    val sweep = if (total > 0) item.value / total * 360f else 0f
                                    drawArc(
                                        color = statusColor(item.name),
                                        startAngle = startAngle,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                                        topLeft = Offset(stroke / 2, stroke / 2),
                                        size = Size(
                                            size.width - stroke,
                                            size.height - stroke
                                        )
                                    )
                                    startAngle += sweep
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${total.toInt()}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "在建项目",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            dist.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(statusColor(item.name), CircleShape)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "${statusTextOf(item.name)} · ${item.value} 个",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            if (dist.isEmpty()) {
                                Text(
                                    "暂无数据",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ===== 整体进度 =====
                SectionHeader(title = "整体进度")
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${overview.avgProgress}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "全部在建项目平均进度",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            StatusTag(text = "进行中 ${overview.activeProjects} 个", tone = statusToneOf("in_progress"))
                        }
                    }
                }

                // ===== 当年月度趋势（Vico 交互式双柱图：入场动画 + 触摸提示） =====
                SectionHeader(title = "月度趋势（新增 / 完成）")
                PanelCard {
                    val trend = overview.monthlyTrend
                    if (trend.isEmpty()) {
                        Text(
                            "暂无趋势数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // 两个 series（新增/完成）按月份 x=0..n-1 建 entry
                        val newEntries = trend.mapIndexed { i, it -> entryOf(i.toFloat(), it.newCount.toFloat()) }
                        val doneEntries = trend.mapIndexed { i, it -> entryOf(i.toFloat(), it.completedCount.toFloat()) }
                        val chartModel = entryModelOf(newEntries, doneEntries)
                        val monthLabels = trend.map { monthLabel(it.month) }
                        val monthFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                            monthLabels.getOrElse(value.toInt()) { "" }
                        }
                        Chart(
                            chart = columnChart(
                                // 低饱和双柱：新增=主色（核心数据），完成=状态绿（对齐 Web 趋势图）
                                columns = listOf(
                                    LineComponent(color = ChartPrimary.toArgb(), thicknessDp = 12f),
                                    LineComponent(color = ChartStatusDone.toArgb(), thicknessDp = 12f)
                                )
                            ),
                            model = chartModel,
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis(valueFormatter = monthFormatter),
                            // 注：Vico 1.15 的 marker() 工厂为 internal，触摸提示暂不可用；
                            // 图表仍保留入场动画、Y 轴刻度、缩放等核心增量
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Legend(color = ChartPrimary, label = "新增")
                            Legend(color = ChartStatusDone, label = "完成")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 简化卡片容器：统一 surface + 圆角 */
@Composable
private fun PanelCard(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 月度标签："2024-01" → "1月"（去掉前导零）；null/格式异常时显示 "-" */
private fun monthLabel(month: String?): String {
    val m = month?.substringAfterLast('-', "")?.trimStart('0')
    return if (m.isNullOrEmpty()) "-" else "${m}月"
}
