package com.example.hbsz_app_android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hbsz_app_android.ui.theme.DangerContainer
import com.example.hbsz_app_android.ui.theme.DangerText
import com.example.hbsz_app_android.ui.theme.InfoContainer
import com.example.hbsz_app_android.ui.theme.InfoText
import com.example.hbsz_app_android.ui.theme.OnPrimaryContainer
import com.example.hbsz_app_android.ui.theme.PrimaryContainer
import com.example.hbsz_app_android.ui.theme.SuccessContainer
import com.example.hbsz_app_android.ui.theme.SuccessText
import com.example.hbsz_app_android.ui.theme.WarningContainer
import com.example.hbsz_app_android.ui.theme.WarningText

/**
 * 语义色调 —— 对齐 Web 端 el-tag 的五种 type（primary/success/warning/danger/info）。
 * 浅底 + 深文本的组合满足 WCAG 2.1 AA 对比度要求（与 Web global.css plain 按钮配色一致）。
 */
enum class TagTone(val container: Color, val content: Color) {
    PRIMARY(PrimaryContainer, OnPrimaryContainer),
    SUCCESS(SuccessContainer, SuccessText),
    WARNING(WarningContainer, WarningText),
    DANGER(DangerContainer, DangerText),
    INFO(InfoContainer, InfoText)
}

/**
 * 状态标签 —— 对齐 Web 端 StatusTag 组件 / statusMap.js 的状态映射。
 *
 * 用法：StatusTag(text = "进行中", tone = TagTone.PRIMARY)
 * 状态文本与色调的映射关系见 [com.example.hbsz_app_android.ui.components.statusOf]。
 */
@Composable
fun StatusTag(
    text: String,
    tone: TagTone,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = tone.content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(tone.container, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

// ===== 状态映射（对齐 Web src/utils/statusMap.js） =====

/** 项目/任务状态 → 中文文本（兼容英文枚举与中文值两种后端形态） */
fun statusTextOf(status: String?): String = when (status) {
    null, "" -> "-"
    "completed" -> "已完成"
    "in_progress" -> "进行中"
    "pending" -> "未开始"
    "delayed" -> "已延期"
    "paused" -> "已暂停"
    else -> status
}

/** 项目/任务状态 → 标签色调（对齐 STATUS_TAG_TYPE_MAP，未知返回 INFO） */
fun statusToneOf(status: String?): TagTone = when (status) {
    "completed", "已完成" -> TagTone.SUCCESS
    "in_progress", "进行中" -> TagTone.PRIMARY
    "delayed", "已逾期", "已延期" -> TagTone.DANGER
    "pending", "待开始", "未开始" -> TagTone.INFO
    "paused", "已暂停" -> TagTone.WARNING
    else -> TagTone.INFO
}

/** 审核状态 → 中文文本（REVIEW_STATUS_TEXT_MAP） */
fun reviewStatusTextOf(status: String?): String = when (status) {
    "pending" -> "待审核"
    "approved" -> "已通过"
    "rejected" -> "已驳回"
    else -> status ?: "-"
}

/** 审核状态 → 标签色调（REVIEW_STATUS_TAG_MAP） */
fun reviewStatusToneOf(status: String?): TagTone = when (status) {
    "pending" -> TagTone.WARNING
    "approved" -> TagTone.SUCCESS
    "rejected" -> TagTone.DANGER
    else -> TagTone.INFO
}

/** 风险等级 → 中文文本（getRiskLevelText，未知按低风险处理） */
fun riskTextOf(level: String?): String = when (level) {
    "high" -> "高风险"
    "medium" -> "中等"
    "low" -> "低风险"
    else -> "低风险"
}

/** 风险等级 → 标签色调（getRiskTagType） */
fun riskToneOf(level: String?): TagTone = when (level) {
    "high" -> TagTone.DANGER
    "medium" -> TagTone.WARNING
    else -> TagTone.SUCCESS
}

/** 通知类型 → 中文文本（getNotifyTypeText，未知返回"其他"） */
fun notifyTypeTextOf(type: String?): String = when (type) {
    "progress" -> "进度"
    "task_due" -> "到期"
    "doc_update" -> "文档"
    "archive" -> "归档"
    "system" -> "系统"
    else -> "其他"
}

/** 通知类型 → 标签色调（getNotifyTypeTagType） */
fun notifyTypeToneOf(type: String?): TagTone = when (type) {
    "progress" -> TagTone.PRIMARY
    "task_due" -> TagTone.WARNING
    "doc_update", "system" -> TagTone.INFO
    "archive" -> TagTone.SUCCESS
    else -> TagTone.INFO
}

/** 审批待办来源 → 中文文本（对齐 Web/uni-app 端待办中心来源映射） */
fun todoSourceTextOf(source: String?): String = when (source) {
    "progress" -> "进度"
    "workload" -> "工作量"
    "document" -> "文档"
    "auth" -> "授权"
    else -> source ?: "-"
}

/** 审批待办来源 → 标签色调（四类来源各自区分） */
fun todoSourceToneOf(source: String?): TagTone = when (source) {
    "progress" -> TagTone.PRIMARY
    "workload" -> TagTone.SUCCESS
    "document" -> TagTone.INFO
    "auth" -> TagTone.WARNING
    else -> TagTone.INFO
}

// ===== 转移/授权审计日志操作类型映射（对齐 Web statusMap.js getActionText/getActionTagType） =====

/**
 * 审计操作类型 → 中文文本。
 * transfer_leader：负责人移交（项目所有权与审批责任转移，与后端 transferLeader 单点对应）；
 * operatedBy 为 null 的记录（如授权过期自动回收）由调用方直接展示 operatorName。
 */
fun actionTextOf(type: String?): String = when (type) {
    "transfer" -> "转移"
    "transfer_leader" -> "负责人移交"
    "assign" -> "直接授权"
    "revoke" -> "撤销"
    "auth_request" -> "授权申请"
    "approve" -> "审批通过"
    "reject" -> "审批驳回"
    "extend" -> "延期"
    "team_add" -> "项目组添加成员"
    "team_remove" -> "项目组移除成员"
    else -> type ?: "-"
}

/** 审计操作类型 → 标签色调（未知返回 INFO） */
fun actionToneOf(type: String?): TagTone = when (type) {
    "transfer" -> TagTone.WARNING
    "transfer_leader" -> TagTone.PRIMARY
    "assign" -> TagTone.SUCCESS
    "revoke" -> TagTone.DANGER
    "auth_request" -> TagTone.PRIMARY
    "approve" -> TagTone.SUCCESS
    "reject" -> TagTone.DANGER
    "extend" -> TagTone.WARNING
    "team_add" -> TagTone.SUCCESS
    "team_remove" -> TagTone.DANGER
    else -> TagTone.INFO
}
