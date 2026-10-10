package com.example.hbsz_app_android.ui.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hbsz_app_android.data.remote.dto.ActiveUserDto
import com.example.hbsz_app_android.ui.components.DropdownField

/**
 * 项目转移/授权弹窗 —— 对齐 Web 端 ProjectList.vue 转移对话框的三种模式：
 * - 移交负责人：toManagerId（项目所有权 + 审批待办实时流向新负责人）；
 * - 替换工程师：toEngineerId（撤销现有全部工程师授权并授权目标工程师）；
 * - 新增授权：toEngineerId（不撤销现有授权，仅追加一名工程师）。
 *
 * 写路径语义由后端单点保证（申请-审批链路留痕 + @Idempotent 幂等 + 所有权校验），
 * 弹窗仅做模式选择与参数组装；提交结果经 Snackbar 反馈。
 *
 * @param projectName 项目名（弹窗标题展示）
 * @param currentManagerName 当前负责人姓名（移交模式下拉过滤本人，后端兜底校验）
 * @param engineers 活跃工程师下拉数据
 * @param managers 活跃项目经理下拉数据
 * @param optionsLoading 下拉数据加载中
 * @param submitting 提交中（按钮防重复点击）
 * @param onSubmit 模式 + 目标 + 备注 回调
 * @param onDismiss 关闭弹窗（含点遮罩/取消）
 */
@Composable
fun ProjectTransferDialog(
    projectName: String?,
    currentManagerName: String?,
    engineers: List<ActiveUserDto>,
    managers: List<ActiveUserDto>,
    optionsLoading: Boolean,
    submitting: Boolean,
    onSubmit: (mode: TransferMode, target: ActiveUserDto, remark: String?) -> Unit,
    onDismiss: () -> Unit
) {
    /** 三种模式：进入弹窗默认"移交负责人"（对应后端 2026-08 重设计主场景） */
    var mode by remember { mutableStateOf(TransferMode.MANAGER) }
    var selectedManager by remember { mutableStateOf<ActiveUserDto?>(null) }
    var selectedEngineer by remember { mutableStateOf<ActiveUserDto?>(null) }
    var remark by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("项目转移 / 授权", fontWeight = FontWeight.SemiBold)
                projectName?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // ===== 模式选择 =====
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == TransferMode.MANAGER,
                        onClick = { mode = TransferMode.MANAGER },
                        label = { Text("移交负责人") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = mode == TransferMode.REPLACE,
                        onClick = { mode = TransferMode.REPLACE },
                        label = { Text("替换工程师") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
                Spacer(Modifier.height(4.dp))
                FilterChip(
                    selected = mode == TransferMode.ASSIGN,
                    onClick = { mode = TransferMode.ASSIGN },
                    label = { Text("新增授权（不撤销现有授权）") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                Spacer(Modifier.height(12.dp))

                // ===== 模式说明（对齐 Web 端各模式的提示文案） =====
                Text(
                    text = when (mode) {
                        TransferMode.MANAGER -> "移交后项目全部审批（进度/工作量/文档/授权）自动流向新负责人"
                        TransferMode.REPLACE -> "将撤销该项目现有全部工程师授权，并授权所选工程师"
                        TransferMode.ASSIGN -> "为项目追加一名授权工程师，不影响现有授权"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                when {
                    optionsLoading -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(20.dp)
                                .height(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "加载人员列表…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    mode == TransferMode.MANAGER -> {
                        // 过滤本人（后端拒绝自移交，前端提前规避减少无效请求）
                        val candidates = managers.filterNot { it.name == currentManagerName }
                        DropdownField(
                            label = "新负责人",
                            options = candidates,
                            selected = selectedManager,
                            textOf = { it.name ?: it.username ?: "-" },
                            onSelect = { selectedManager = it }
                        )
                    }

                    else -> DropdownField(
                        label = if (mode == TransferMode.REPLACE) "新工程师" else "授权工程师",
                        options = engineers,
                        selected = selectedEngineer,
                        textOf = { it.name ?: it.username ?: "-" },
                        onSelect = { selectedEngineer = it }
                    )
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    placeholder = { Text("备注（选填，写入审计日志）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val target = if (mode == TransferMode.MANAGER) selectedManager else selectedEngineer
            Button(
                enabled = !submitting && !optionsLoading && target != null,
                onClick = {
                    target?.let { onSubmit(mode, it, remark.trim().takeIf { r -> r.isNotEmpty() }) }
                }
            ) { Text(if (submitting) "提交中…" else "确认${mode.label}") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 转移模式（对齐 Web transferMode：manager / replace / assign） */
enum class TransferMode(val label: String) {
    MANAGER("移交"),
    REPLACE("替换"),
    ASSIGN("授权")
}
