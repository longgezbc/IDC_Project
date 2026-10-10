package com.example.hbsz_app_android.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 快速填报上下文 DTO —— 与后端 WorkloadMyReportVO 一一对应。
 *
 * 对应 GET /workload/my/report-context（2026-08 工作量模块重构）：
 * 为"快速填报"卡片流一次性提供全部上下文 —— 我参与的项目组、
 * 今日已有填报（含审核状态）、昨日数量（供"复制昨天"）、
 * 最近 14 天常用清单项（按填报频次降序）。
 * 保存路径复用 POST /workload/records（审核链路/成员校验单点不变）。
 */

/** GET /workload/my/report-context 响应 */
@Serializable
data class WorkloadMyReportContextDto(
    val myUserId: Long = 0,
    val myName: String? = null,
    /** 我作为在组成员参与的项目（未归档） */
    val projects: List<MyProjectBrief> = emptyList(),
    val projectId: Long? = null,
    val projectName: String? = null,
    /** 填报日期（yyyy-MM-dd） */
    val reportDate: String? = null,
    /** 我今日已有填报：itemId → 单元格 */
    val todayRecords: Map<String, WorkloadCellDto> = emptyMap(),
    /** 我昨日填报数量：itemId（字符串键）→ quantity */
    val yesterdayRecords: Map<String, Double> = emptyMap(),
    /** 最近 14 天常用清单项（按填报频次降序） */
    val frequentItems: List<FrequentItemDto> = emptyList(),
    /** 项目清单总项数 */
    val totalItemCount: Int = 0
)

/** 我参与的项目简要信息 */
@Serializable
data class MyProjectBrief(
    val projectId: Long = 0,
    val projectName: String = "",
    /** 我在该项目组的角色（member/leader） */
    val roleInTeam: String? = null
)

/** 今日填报单元格：数量 + 审核状态（approved 锁定 / pending 待审 / rejected 驳回） */
@Serializable
data class WorkloadCellDto(
    @Serializable(with = FlexibleDoubleSerializer::class) val quantity: Double = 0.0,
    val reviewStatus: String = "pending",
    val reviewRemark: String? = null
)

/** 常用清单项：清单详情 + 我的填报统计（近 14 天） */
@Serializable
data class FrequentItemDto(
    val itemId: Long = 0,
    val category: String? = null,
    val workScope: String? = null,
    val unit: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val ratedWorkload: Double? = null,
    /** 最近 14 天内我的填报天数（频次） */
    val fillCount: Int = 0,
    /** 我最后一次填报数量（步进默认值） */
    @Serializable(with = FlexibleDoubleSerializer::class) val lastQuantity: Double? = null,
    /** 最后填报日期（yyyy-MM-dd） */
    val lastDate: String? = null
)
