package com.example.hbsz_app_android.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 工作量统计 + 审批待办 DTO —— 字段与后端 VO/DTO 一一对应（已核实 Controller/VO 源码）。
 *
 * 业务规则（与 Web/uni-app 端共享同一后端契约）：
 * - 填报人员只能是项目组授权成员（后端校验），填报后进入待审核（pending）状态；
 * - PM 批量审核采用 CAS 语义（仅 pending 可审，返回实际成功条数）；
 * - 审批待办中心为四类审核（进度/工作量/文档/授权）的只读聚合，
 *   审核动作复用各业务模块接口（progress 复用 ApiService.reviewProgress）。
 *
 * 注意：后端 BigDecimal 默认序列化为 JSON 数值，Double 字段统一使用
 * [FlexibleDoubleSerializer] 兼容数值/字符串两种形态。
 */

// ==================== 工作量统计 ====================

/** GET /workload/workers 响应：项目组在组成员（填报人员来源，组长置前） */
@Serializable
data class WorkloadWorkerDto(
    val userId: Long = 0,
    val name: String = "",
    val roleInTeam: String? = null
)

/** GET /workload/items 响应：清单项（含已完成/剩余/当日完成实时聚合） */
@Serializable
data class WorkloadItemDto(
    val id: Long = 0,
    val projectId: Long? = null,
    val category: String? = null,
    val workScope: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val ratedWorkload: Double? = null,
    val unit: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val performanceScore: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val totalQuantity: Double? = null,
    val remark: String? = null,
    val sortOrder: Int? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val completedQuantity: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val remainingQuantity: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val todayQuantity: Double? = null
)

/** GET /workload/records/pending 响应：填报明细（联表含作业上下文与审核信息） */
@Serializable
data class WorkloadRecordDto(
    val id: Long = 0,
    val projectId: Long? = null,
    val itemId: Long? = null,
    val category: String? = null,
    val workScope: String? = null,
    val unit: String? = null,
    val recordDate: String? = null,
    val workerName: String? = null,
    val workerUserId: Long? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val quantity: Double? = null,
    val reviewStatus: String = "pending",
    val reviewRemark: String? = null,
    val reviewerName: String? = null,
    val reviewTime: String? = null,
    val createdAt: String? = null
)

/** POST /workload/records 单条明细：清单项 × 人员 × 数量 */
@Serializable
data class WorkloadRecordEntry(
    val itemId: Long,
    val workerUserId: Long,
    val quantity: Double
)

/** POST /workload/records 请求：某项目某日批量填报（保存后进入待审核） */
@Serializable
data class WorkloadRecordBatchRequest(
    val projectId: Long,
    val recordDate: String,
    val records: List<WorkloadRecordEntry>
)

/** POST /workload/records/review 请求：批量审核（CAS，仅 pending 可审） */
@Serializable
data class WorkloadReviewRequest(
    val ids: List<Long>,
    val approve: Boolean,
    val remark: String? = null
)

// ==================== 审批待办中心 ====================

/** GET /approval/todos 列表项：四类审核待办的聚合展示模型 */
@Serializable
data class TodoItemDto(
    val source: String = "",
    val bizId: Long = 0,
    val projectId: Long? = null,
    val projectName: String? = null,
    val title: String? = null,
    val submitter: String? = null,
    val submitTime: String? = null,
    val detail: String? = null
)

/** GET /approval/todos/count 响应：按来源计数（进度/工作量/文档/授权/合计） */
@Serializable
data class TodoCountDto(
    val progress: Long = 0,
    val workload: Long = 0,
    val document: Long = 0,
    val auth: Long = 0,
    val total: Long = 0
)

// ==================== 审批分发请求（复用各业务模块审核接口） ====================

/** POST /documents/{id}/review 请求（审核通过=已审核，驳回=已驳回） */
@Serializable
data class DocumentReviewRequest(
    val approve: Boolean,
    val remark: String? = null
)

/** POST /project-auth/requests/{id}/approve 请求（approve=false 即驳回） */
@Serializable
data class AuthApproveRequest(
    val approve: Boolean,
    val comment: String? = null
)
