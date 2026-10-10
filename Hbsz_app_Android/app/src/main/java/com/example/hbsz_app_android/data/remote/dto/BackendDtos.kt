package com.example.hbsz_app_android.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * 兼容 JSON 数值与字符串的 Double 序列化器。
 * 后端 BigDecimal 默认序列化为 JSON 数值（未配置 ToStringSerializer），
 * 若后端未来改为字符串序列化，此序列化器仍可正常解析。
 */
object FlexibleDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val element: JsonElement = jsonDecoder.decodeJsonElement()
        return (element as? JsonPrimitive)?.doubleOrNull ?: 0.0
    }

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeDouble(value)
    }
}

/**
 * 后端业务 DTO —— 字段与后端 VO/DTO 一一对应（已逐个核实 Controller/VO 源码）。
 *
 * 注意：后端部分 VO 的 id 为 String（ProjectVO/TaskVO/DocumentVO/ProgressLogVO），
 * MyTaskItemVO/NotificationVO/CustomerVO 的 id 为 Long，此处保持一致。
 */

// ==================== 通用分页 ====================

@Serializable
data class PageResult<T>(
    val records: List<T> = emptyList(),
    val total: Long = 0,
    val size: Long = 0,
    val current: Long = 0,
    val pages: Long = 0
)

// ==================== 用户信息 ====================

/** GET /auth/info、GET /user/profile 响应 */
@Serializable
data class UserInfoDto(
    val id: Long? = null,
    val username: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val department: String? = null,
    val position: String? = null,
    val bio: String? = null,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList()
)

/** PUT /user/profile 请求 */
@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val nickname: String? = null,
    val gender: Int? = null,
    val avatar: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val position: String? = null,
    val bio: String? = null
)

/** PUT /user/password 请求 */
@Serializable
data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)

/** GET/PUT /user/notifications 通知设置 */
@Serializable
data class NotificationSettingsDto(
    val inApp: Boolean = true,
    val email: Boolean = false,
    val sms: Boolean = false,
    val progress: Boolean = true,
    val taskDue: Boolean = true,
    val docUpdate: Boolean = true,
    val archive: Boolean = true
)

/** POST /auth/refresh 请求 */
@Serializable
data class RefreshTokenRequest(val refreshToken: String)

// ==================== 工作台 ====================

/** GET /dashboard/summary 响应 */
@Serializable
data class DashboardSummaryDto(
    val role: String? = null,
    val myTaskCount: Int = 0,
    val myPendingTaskCount: Int = 0,
    val myOverdueTaskCount: Int = 0,
    val myUpcomingTaskCount: Int = 0,
    val mySubmissionCount: Int = 0,
    val myPendingSubmissionCount: Int = 0,
    val myApprovedSubmissionCount: Int = 0,
    val myRejectedSubmissionCount: Int = 0,
    val pendingReviewCount: Int = 0,
    val managedProjectCount: Int = 0,
    val unreadNotificationCount: Int = 0
)

/** GET /dashboard/my-tasks 列表项 */
@Serializable
data class MyTaskItemDto(
    val id: Long,
    val name: String = "",
    val projectId: Long? = null,
    val projectName: String? = null,
    val status: String = "",
    val progress: Int = 0,
    val startDate: String? = null,
    val endDate: String? = null,
    val isMilestone: Int = 0,
    val isKeyTask: Int = 0
)

// ==================== 进度日志 ====================

/** 进度日志（我的填报 / 待审核 / 任务日志共用） */
@Serializable
data class ProgressLogDto(
    val id: String,
    val taskId: String? = null,
    val taskName: String? = null,
    val projectId: String? = null,
    val projectName: String? = null,
    val progress: Int = 0,
    val previousProgress: Int = 0,
    val remark: String? = null,
    val images: List<String> = emptyList(),
    val actualStartDate: String? = null,
    val actualEndDate: String? = null,
    val reviewStatus: String = "pending",
    val reviewerId: String? = null,
    val reviewerName: String? = null,
    val reviewTime: String? = null,
    val reviewRemark: String? = null,
    val reporterId: String? = null,
    val reporterName: String? = null,
    val reportTime: String? = null
)

/** PUT /tasks/{id}/progress 请求 */
@Serializable
data class ProgressReportRequest(
    val progress: Int,
    val remark: String? = null,
    val images: List<String> = emptyList(),
    val actualStartDate: String,
    val actualEndDate: String
)

/** PUT /tasks/progress/{logId}/review 请求 */
@Serializable
data class ProgressReviewRequest(
    val approve: Boolean,
    val remark: String? = null
)

// ==================== 项目 ====================

/** 项目 VO（列表/详情/归档共用） */
@Serializable
data class ProjectDto(
    val id: String,
    val name: String = "",
    val code: String? = null,
    val status: String = "",
    val priority: String? = null,
    val type: String? = null,
    val customerId: String? = null,
    val customerName: String? = null,
    val customerContact: String? = null,
    val customerPhone: String? = null,
    val customerEmail: String? = null,
    val manager: String? = null,
    val managerPhone: String? = null,
    val address: String? = null,
    val region: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val contractAmount: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val budget: Double? = null,
    val progress: Int = 0,
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val archived: Boolean = false
)

/** GET /projects/statistics 响应 */
@Serializable
data class ProjectStatisticsDto(
    val activeCount: Int = 0,
    val completedCount: Int = 0,
    val archivedCount: Int = 0,
    val totalCount: Int = 0,
    @Serializable(with = FlexibleDoubleSerializer::class) val totalContractAmount: Double? = null,
    val avgProgress: Int = 0,
    val documentCount: Int = 0,
    val upcomingKeyTaskCount: Int = 0
)

/** GET /projects/archived/statistics 响应 */
@Serializable
data class ArchiveStatisticsDto(
    val totalCount: Int = 0,
    @Serializable(with = FlexibleDoubleSerializer::class) val totalContractAmount: Double? = null,
    val currentYearCount: Int = 0,
    val currentMonthCount: Int = 0
)

// ==================== 任务 ====================

/** 任务 VO */
@Serializable
data class TaskDto(
    val id: String,
    val projectId: String? = null,
    val name: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val actualStartDate: String? = null,
    val actualEndDate: String? = null,
    val progress: Int = 0,
    val status: String = "",
    val assignee: String? = null,
    val assigneeId: Long? = null,
    val isMilestone: Boolean = false,
    val isKey: Boolean = false,
    val parentId: String? = null,
    val description: String? = null,
    val remark: String? = null
)

/** GET /projects/{projectId}/progress 响应 */
@Serializable
data class ProjectProgressDto(
    val projectId: String? = null,
    val projectName: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val overallProgress: Int = 0,
    val tasks: List<TaskDto> = emptyList()
)

// ==================== 通知 ====================

/** 通知 VO */
@Serializable
data class NotificationDto(
    val id: Long,
    val userId: Long? = null,
    val title: String = "",
    val content: String? = null,
    val notifyType: String? = null,
    val bizType: String? = null,
    val bizId: Long? = null,
    val priority: Int = 1,
    val isRead: Int = 0,
    val readTime: String? = null,
    val createdAt: String? = null
)

// ==================== 文档 ====================

/** 文档 VO */
@Serializable
data class DocumentDto(
    val id: String,
    val projectId: String? = null,
    val projectName: String? = null,
    val name: String = "",
    val category: String? = null,
    val version: String? = null,
    val status: String = "待审核",
    val reviewerName: String? = null,
    val reviewTime: String? = null,
    val reviewRemark: String? = null,
    val size: String? = null,
    val fileType: String? = null,
    val uploader: String? = null,
    val uploadDate: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList()
)

// ==================== 客户 ====================

/** 客户 VO */
@Serializable
data class CustomerDto(
    val id: Long,
    val customerCode: String? = null,
    val name: String = "",
    val contactPerson: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    val address: String? = null,
    val status: Int = 1,
    val remark: String? = null,
    val projectCount: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

// ==================== 统计 ====================

/** GET /statistics/overview 响应 */
@Serializable
data class StatisticsOverviewDto(
    val totalProjects: Int = 0,
    val activeProjects: Int = 0,
    val completedProjects: Int = 0,
    val archivedProjects: Int = 0,
    @Serializable(with = FlexibleDoubleSerializer::class) val totalContractAmount: Double? = null,
    val avgProgress: Int = 0,
    val statusDistribution: List<NameValueItem> = emptyList(),
    val typeDistribution: List<NameValueItem> = emptyList(),
    val priorityDistribution: List<NameValueItem> = emptyList(),
    val monthlyTrend: List<NameValueItem> = emptyList()
)

/** 统计分布项（后端返回 Map<String,Object>：name/value 键；月度趋势为 month/newCount/completedCount 键） */
@Serializable
data class NameValueItem(
    val name: String? = null,
    val value: Int = 0,
    val month: String? = null,
    val newCount: Int = 0,
    val completedCount: Int = 0
)

// ==================== 日报 ====================

/** POST /files/upload 响应 */
@Serializable
data class FileUploadDto(
    val url: String,
    val previewUrl: String? = null
)

/** POST /daily-report/export 响应 / GET /daily-report/history 列表项（对齐后端 ExportRecordVO） */
@Serializable
data class ExportRecordDto(
    val id: Long? = null,
    val userName: String? = null,
    val reportType: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val format: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val fileSizeText: String? = null,
    val status: String? = null,
    val createdAt: String? = null
)

/** GET /daily-report/history 响应（records + total + page + size） */
@Serializable
data class DailyReportHistoryDto(
    val records: List<ExportRecordDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val size: Int = 10
)

/** GET /daily-report/preview 响应（甘特图日报）；后端返回 Map<String,Object>，值为混合类型，用 JsonElement 承载 */
@Serializable
data class DailyReportPreviewDto(
    val summary: Map<String, JsonElement> = emptyMap(),
    val projectSummary: List<Map<String, JsonElement>> = emptyList(),
    val taskProgress: List<Map<String, JsonElement>> = emptyList(),
    val reportTitle: String? = null,
    val projectId: Long? = null,
    val projectName: String? = null
)
