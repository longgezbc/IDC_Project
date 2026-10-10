package com.example.hbsz_app_android.data.repository

import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.common.runCatchingApi
import com.example.hbsz_app_android.data.remote.ApiService
import com.example.hbsz_app_android.data.remote.ApiResponse
import com.example.hbsz_app_android.data.remote.dto.ArchiveStatisticsDto
import com.example.hbsz_app_android.data.remote.dto.ChangePasswordRequest
import com.example.hbsz_app_android.data.remote.dto.CustomerDto
import com.example.hbsz_app_android.data.remote.dto.DailyReportHistoryDto
import com.example.hbsz_app_android.data.remote.dto.DailyReportPreviewDto
import com.example.hbsz_app_android.data.remote.dto.DashboardSummaryDto
import com.example.hbsz_app_android.data.remote.dto.DocumentDto
import com.example.hbsz_app_android.data.remote.dto.ExportRecordDto
import com.example.hbsz_app_android.data.remote.dto.MyTaskItemDto
import com.example.hbsz_app_android.data.remote.dto.NotificationDto
import com.example.hbsz_app_android.data.remote.dto.NotificationSettingsDto
import com.example.hbsz_app_android.data.remote.dto.PageResult
import com.example.hbsz_app_android.data.remote.dto.ProgressLogDto
import com.example.hbsz_app_android.data.remote.dto.ProgressReportRequest
import com.example.hbsz_app_android.data.remote.dto.ProgressReviewRequest
import com.example.hbsz_app_android.data.remote.dto.ActiveUserDto
import com.example.hbsz_app_android.data.remote.dto.ProjectAssignmentDto
import com.example.hbsz_app_android.data.remote.dto.ProjectDto
import com.example.hbsz_app_android.data.remote.dto.ProjectTransferRequest
import com.example.hbsz_app_android.data.remote.dto.TransferLogDto
import com.example.hbsz_app_android.data.remote.dto.ProjectProgressDto
import com.example.hbsz_app_android.data.remote.dto.ProjectStatisticsDto
import com.example.hbsz_app_android.data.remote.dto.StatisticsOverviewDto
import com.example.hbsz_app_android.data.remote.dto.TaskDto
import com.example.hbsz_app_android.data.remote.dto.TodoCountDto
import com.example.hbsz_app_android.data.remote.dto.TodoItemDto
import com.example.hbsz_app_android.data.remote.dto.UpdateProfileRequest
import com.example.hbsz_app_android.data.remote.dto.UserInfoDto
import com.example.hbsz_app_android.data.remote.dto.WorkloadItemDto
import com.example.hbsz_app_android.data.remote.dto.WorkloadMyReportContextDto
import com.example.hbsz_app_android.data.remote.dto.WorkloadRecordDto
import com.example.hbsz_app_android.data.remote.dto.WorkloadRecordEntry
import com.example.hbsz_app_android.data.remote.dto.WorkloadRecordBatchRequest
import com.example.hbsz_app_android.data.remote.dto.WorkloadReviewRequest
import com.example.hbsz_app_android.data.remote.dto.WorkloadWorkerDto
import com.example.hbsz_app_android.data.remote.dto.DocumentReviewRequest
import com.example.hbsz_app_android.data.remote.dto.AuthApproveRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** ApiResponse → Resource 统一转换 */
private fun <T> ApiResponse<T>.toResource(default: T? = null): Resource<T> =
    if (isSuccess) {
        Resource.Success(data ?: default ?: error("响应数据为空"))
    } else {
        Resource.Error(message ?: "请求失败")
    }

/** 无数据体响应（code=200 即成功） */
private fun ApiResponse<Unit>.toUnitResource(): Resource<Unit> =
    if (isSuccess) Resource.Success(Unit) else Resource.Error(message ?: "请求失败")

// ==================== 工作台 ====================

@Singleton
class DashboardRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun getSummary(): Resource<DashboardSummaryDto> = runCatchingApi {
        api.getDashboardSummary().toResource(DashboardSummaryDto())
    }

    suspend fun getMyTasks(
        pageNum: Int = 1,
        pageSize: Int = 20,
        status: String? = null
    ): Resource<PageResult<MyTaskItemDto>> = runCatchingApi {
        api.getMyTasks(pageNum, pageSize, status).toResource(PageResult())
    }

    suspend fun getMySubmissions(
        pageNum: Int = 1,
        pageSize: Int = 20,
        reviewStatus: String? = null
    ): Resource<PageResult<ProgressLogDto>> = runCatchingApi {
        api.getMySubmissions(pageNum, pageSize, reviewStatus).toResource(PageResult())
    }
}

// ==================== 项目 ====================

@Singleton
class ProjectRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun getProjects(
        pageNum: Int = 1,
        pageSize: Int = 20,
        keyword: String? = null,
        status: String? = null
    ): Resource<PageResult<ProjectDto>> = runCatchingApi {
        api.getProjects(pageNum, pageSize, keyword, status, archived = false).toResource(PageResult())
    }

    suspend fun getProjectDetail(id: Long): Resource<ProjectDto> = runCatchingApi {
        api.getProjectDetail(id).toResource()
    }

    suspend fun getStatistics(): Resource<ProjectStatisticsDto> = runCatchingApi {
        api.getProjectStatistics().toResource(ProjectStatisticsDto())
    }

    suspend fun getArchivedProjects(
        pageNum: Int = 1,
        pageSize: Int = 20,
        keyword: String? = null
    ): Resource<PageResult<ProjectDto>> = runCatchingApi {
        api.getArchivedProjects(pageNum, pageSize, keyword).toResource(PageResult())
    }

    suspend fun getArchiveStatistics(): Resource<ArchiveStatisticsDto> = runCatchingApi {
        api.getArchiveStatistics().toResource(ArchiveStatisticsDto())
    }

    suspend fun getProjectProgress(projectId: Long): Resource<ProjectProgressDto> = runCatchingApi {
        api.getProjectProgress(projectId).toResource(ProjectProgressDto())
    }
}

// ==================== 任务与进度 ====================

@Singleton
class TaskRepository @Inject constructor(
    private val api: ApiService,
    private val json: Json
) {
    suspend fun getProjectTasks(projectId: Long): Resource<List<TaskDto>> = runCatchingApi {
        api.getProjectTasks(projectId).toResource(emptyList())
    }

    /** 提交进度填报 */
    suspend fun reportProgress(taskId: Long, body: ProgressReportRequest): Resource<ProgressLogDto> =
        runCatchingApi { api.reportProgress(taskId, body).toResource() }

    /** 待审核列表（data 为数组=旧版全量；为对象=新版分页，两种格式兼容） */
    suspend fun getPendingProgress(
        pageNum: Int = 1,
        pageSize: Int = 20
    ): Resource<PageResult<ProgressLogDto>> = runCatchingApi {
        val res = api.getPendingProgress(pageNum, pageSize)
        if (!res.isSuccess) return@runCatchingApi Resource.Error(res.message ?: "请求失败")
        Resource.Success(parsePendingPage(res.data))
    }

    /**
     * 兼容解析待审核列表响应：
     * - JsonArray（旧版未分页）→ 包装为"完整单页"（pages=1），
     *   ViewModel 据此判定无更多页，避免后端忽略分页参数时重复拉取全量造成列表重复
     * - JsonObject（新版分页）→ 按标准 PageResult 透传
     */
    private fun parsePendingPage(data: JsonElement?): PageResult<ProgressLogDto> = when (data) {
        is JsonArray -> PageResult(
            records = data.mapNotNull { json.decodeFromJsonElement(ProgressLogDto.serializer(), it) },
            total = data.size.toLong(),
            size = data.size.toLong(),
            current = 1,
            pages = 1
        )
        is JsonObject -> json.decodeFromJsonElement(
            PageResult.serializer(ProgressLogDto.serializer()), data
        )
        else -> PageResult()
    }

    /** 审核 */
    suspend fun reviewProgress(logId: Long, approve: Boolean, remark: String?): Resource<ProgressLogDto> =
        runCatchingApi { api.reviewProgress(logId, ProgressReviewRequest(approve, remark)).toResource() }

    /** 上传施工图片，返回相对路径 */
    suspend fun uploadImage(file: File, mime: String): Resource<String> = runCatchingApi {
        val part = MultipartBody.Part.createFormData(
            "file", file.name,
            file.asRequestBody(mime.toMediaTypeOrNull())
        )
        val resp = api.uploadFile(part)
        if (resp.isSuccess && resp.data?.url != null) {
            Resource.Success(resp.data.url)
        } else {
            Resource.Error(resp.message ?: "图片上传失败")
        }
    }
}

// ==================== 通知 ====================

@Singleton
class NotificationRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun getNotifications(
        pageNum: Int = 1,
        pageSize: Int = 50,
        notifyType: String? = null
    ): Resource<PageResult<NotificationDto>> = runCatchingApi {
        api.getNotifications(pageNum, pageSize, notifyType).toResource(PageResult())
    }

    suspend fun getUnreadCount(): Resource<Int> = runCatchingApi {
        api.getUnreadCount().toResource(0)
    }

    suspend fun markAsRead(id: Long): Resource<Unit> = runCatchingApi {
        api.markAsRead(id).toUnitResource()
    }

    suspend fun markAllAsRead(): Resource<Unit> = runCatchingApi {
        api.markAllAsRead().toUnitResource()
    }

    suspend fun delete(id: Long): Resource<Unit> = runCatchingApi {
        api.deleteNotification(id).toUnitResource()
    }
}

// ==================== 文档 ====================

@Singleton
class DocumentRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun getDocuments(
        pageNum: Int = 1,
        pageSize: Int = 20,
        keyword: String? = null,
        status: String? = null
    ): Resource<PageResult<DocumentDto>> = runCatchingApi {
        api.getDocuments(pageNum, pageSize, keyword, null, null, status).toResource(PageResult())
    }

    suspend fun delete(id: Long): Resource<Unit> = runCatchingApi {
        api.deleteDocument(id).toUnitResource()
    }

    /** 文档下载（返回字节流），调用方负责写文件 */
    suspend fun download(id: Long): Resource<okhttp3.ResponseBody> = runCatchingApi {
        Resource.Success(api.downloadDocument(id))
    }
}

// ==================== 客户 ====================

@Singleton
class CustomerRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun getCustomers(
        pageNum: Int = 1,
        pageSize: Int = 20,
        keyword: String? = null
    ): Resource<PageResult<CustomerDto>> = runCatchingApi {
        api.getCustomers(pageNum, pageSize, keyword).toResource(PageResult())
    }

    suspend fun getDetail(id: Long): Resource<CustomerDto> = runCatchingApi {
        api.getCustomerDetail(id).toResource()
    }
}

// ==================== 统计与日报 ====================

@Singleton
class StatisticsRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun getOverview(): Resource<StatisticsOverviewDto> = runCatchingApi {
        api.getStatisticsOverview().toResource(StatisticsOverviewDto())
    }

    suspend fun getDailyReportPreview(
        startDate: String,
        endDate: String
    ): Resource<DailyReportPreviewDto> = runCatchingApi {
        api.getDailyReportPreview(startDate, endDate).toResource(DailyReportPreviewDto())
    }

    /** POST /daily-report/export 生成 Excel 并返回导出记录 */
    suspend fun exportDailyReport(startDate: String, endDate: String): Resource<ExportRecordDto> =
        runCatchingApi { api.exportDailyReport(startDate, endDate).toResource() }

    /** GET /daily-report/history 当前用户导出历史 */
    suspend fun getExportHistory(page: Int = 1, size: Int = 10): Resource<DailyReportHistoryDto> =
        runCatchingApi { api.getExportHistory(page, size).toResource(DailyReportHistoryDto()) }

    /** GET /daily-report/download/{id} 下载导出文件（字节流），调用方负责写文件 */
    suspend fun downloadExport(id: Long): Resource<okhttp3.ResponseBody> = runCatchingApi {
        Resource.Success(api.downloadExport(id))
    }
}

// ==================== 用户中心 ====================

@Singleton
class UserRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun getUserInfo(): Resource<UserInfoDto> = runCatchingApi {
        api.getUserInfo().toResource()
    }

    suspend fun getProfile(): Resource<UserInfoDto> = runCatchingApi {
        api.getProfile().toResource()
    }

    suspend fun updateProfile(body: UpdateProfileRequest): Resource<UserInfoDto> = runCatchingApi {
        api.updateProfile(body).toResource()
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Resource<Unit> =
        runCatchingApi { api.changePassword(ChangePasswordRequest(oldPassword, newPassword)).toUnitResource() }

    suspend fun getNotificationSettings(): Resource<NotificationSettingsDto> = runCatchingApi {
        api.getNotificationSettings().toResource(NotificationSettingsDto())
    }

    suspend fun updateNotificationSettings(body: NotificationSettingsDto): Resource<NotificationSettingsDto> =
        runCatchingApi { api.updateNotificationSettings(body).toResource(body) }
}

// ==================== 工作量统计 ====================

@Singleton
class WorkloadRepository @Inject constructor(
    private val api: ApiService
) {
    /** 项目可填报人员（项目组在组成员，组长置前；填报人员只能从中选择） */
    suspend fun listWorkers(projectId: Long): Resource<List<WorkloadWorkerDto>> = runCatchingApi {
        api.listWorkloadWorkers(projectId).toResource(emptyList())
    }

    /** 工作量清单（含已完成/剩余/当日完成实时聚合，statisticsDate 决定"当日"归属日） */
    suspend fun listItems(
        projectId: Long,
        statisticsDate: String
    ): Resource<List<WorkloadItemDto>> = runCatchingApi {
        api.getWorkloadItems(projectId, statisticsDate).toResource(emptyList())
    }

    /** 批量保存某日填报（保存后进入待审核；填报人必须是项目组在组成员） */
    suspend fun saveRecords(
        projectId: Long,
        recordDate: String,
        records: List<WorkloadRecordEntry>
    ): Resource<Unit> = runCatchingApi {
        api.saveWorkloadRecords(WorkloadRecordBatchRequest(projectId, recordDate, records))
            .toUnitResource()
    }

    /** 填报明细审核列表（PM 审核用；reviewStatus 默认 pending） */
    suspend fun listReviewRecords(
        projectId: Long,
        recordDate: String? = null,
        reviewStatus: String? = null
    ): Resource<List<WorkloadRecordDto>> = runCatchingApi {
        api.getWorkloadPendingRecords(projectId, recordDate, reviewStatus).toResource(emptyList())
    }

    /**
     * 我的快速填报上下文（2026-08 重构）：常用项（近 14 天频次序）+
     * 今日已有填报（含审核状态）+ 昨日数量（供"复制昨天"）。
     * projectId 为空时仅返回我参与的项目列表。
     */
    suspend fun getMyReportContext(
        projectId: Long? = null,
        date: String
    ): Resource<WorkloadMyReportContextDto> = runCatchingApi {
        api.getMyReportContext(projectId, date).toResource(WorkloadMyReportContextDto())
    }

    /** 批量审核填报（CAS 语义：仅待审核可审；返回实际审核成功条数） */
    suspend fun reviewRecords(
        ids: List<Long>,
        approve: Boolean,
        remark: String?
    ): Resource<Int> = runCatchingApi {
        api.reviewWorkloadRecords(WorkloadReviewRequest(ids, approve, remark)).toResource(0)
    }
}

// ==================== 审批待办中心 ====================

@Singleton
class ApprovalTodoRepository @Inject constructor(
    private val api: ApiService
) {
    /** 分页查询当前用户审批待办（source 为空查全部；提交时间倒序） */
    suspend fun listTodos(
        pageNum: Int = 1,
        pageSize: Int = 20,
        source: String? = null
    ): Resource<PageResult<TodoItemDto>> = runCatchingApi {
        api.getApprovalTodos(source, pageNum, pageSize).toResource(PageResult())
    }

    /** 待办分类计数（进度/工作量/文档/授权/合计，供统计卡片与角标） */
    suspend fun getCounts(): Resource<TodoCountDto> = runCatchingApi {
        api.getTodoCounts().toResource(TodoCountDto())
    }

    /**
     * 快捷审核：按来源分发到对应业务模块的审核接口（与 Web/uni-app 端分发规则一致）。
     * 后端各接口均含 CAS 状态机 + 归属校验，此处仅做参数适配，不新建审核写路径：
     * - progress：PUT /tasks/progress/{logId}/review
     * - workload：POST /workload/records/review（批量接口传单条 ID）
     * - document：POST /documents/{id}/review
     * - auth：POST /project-auth/requests/{id}/approve（approve=false 即驳回）
     */
    suspend fun reviewTodo(
        source: String,
        bizId: Long,
        approve: Boolean,
        remark: String?
    ): Resource<Unit> = runCatchingApi {
        val res = when (source) {
            "progress" -> api.reviewProgress(bizId, ProgressReviewRequest(approve, remark))
            "workload" -> api.reviewWorkloadRecords(
                WorkloadReviewRequest(listOf(bizId), approve, remark)
            )
            "document" -> api.reviewDocument(bizId, DocumentReviewRequest(approve, remark))
            "auth" -> api.approveAuthRequest(bizId, AuthApproveRequest(approve, remark))
            else -> return@runCatchingApi Resource.Error("未知的待办来源：$source")
        }
        if (res.isSuccess) Resource.Success(Unit) else Resource.Error(res.message ?: "审核失败")
    }
}

// ==================== 项目授权与转移 ====================

/**
 * 项目授权与转移仓库 —— 对齐 Web 端 projectAuthorization.js 的移动端写/读路径。
 * 后端写操作均含 @Idempotent 幂等保护 + ProjectAccessGuard 所有权校验（admin/项目负责人），
 * 授权写入统一走申请-审批链路单点（biz_project_auth_request 留痕），此处不做本地兜底。
 */
@Singleton
class ProjectAuthRepository @Inject constructor(
    private val api: ApiService
) {
    /** 项目转移：负责人移交（toManagerId）和/或工程师替换（toEngineerId），至少一项 */
    suspend fun transferProject(
        projectId: Long,
        toManagerId: Long? = null,
        toEngineerId: Long? = null,
        remark: String? = null
    ): Resource<Unit> = runCatchingApi {
        val res = api.transferProject(
            projectId,
            ProjectTransferRequest(toEngineerId = toEngineerId, toManagerId = toManagerId, remark = remark)
        )
        if (res.isSuccess) Resource.Success(Unit) else Resource.Error(res.message ?: "项目转移失败")
    }

    /** 新增一名工程师授权（不影响其他已授权工程师） */
    suspend fun assignEngineer(
        projectId: Long,
        engineerId: Long,
        remark: String? = null
    ): Resource<Unit> = runCatchingApi {
        val res = api.assignEngineer(
            projectId,
            ProjectTransferRequest(toEngineerId = engineerId, remark = remark)
        )
        if (res.isSuccess) Resource.Success(Unit) else Resource.Error(res.message ?: "授权失败")
    }

    /** 撤销指定工程师授权（后端 CAS 软删，无有效授权时返回 DATA_NOT_FOUND） */
    suspend fun revokeEngineer(
        projectId: Long,
        engineerId: Long,
        remark: String? = null
    ): Resource<Unit> = runCatchingApi {
        val res = api.revokeEngineer(projectId, engineerId, remark)
        if (res.isSuccess) Resource.Success(Unit) else Resource.Error(res.message ?: "撤销失败")
    }

    /** 项目下有效授权工程师列表 */
    suspend fun listProjectEngineers(projectId: Long): Resource<List<ProjectAssignmentDto>> =
        runCatchingApi {
            api.listProjectEngineers(projectId).toResource(emptyList())
        }

    /** 活跃工程师下拉（替换/新增授权选择） */
    suspend fun listActiveEngineers(): Resource<List<ActiveUserDto>> = runCatchingApi {
        api.listActiveEngineers().toResource(emptyList())
    }

    /** 活跃项目经理下拉（负责人移交选择） */
    suspend fun listActiveManagers(): Resource<List<ActiveUserDto>> = runCatchingApi {
        api.listActiveManagers().toResource(emptyList())
    }

    /** 转移/授权审计日志（分页；非特权用户须传 projectId，由后端做可见范围控制） */
    suspend fun getTransferLogs(
        pageNum: Int = 1,
        pageSize: Int = 20,
        projectId: Long? = null,
        actionType: String? = null
    ): Resource<PageResult<TransferLogDto>> = runCatchingApi {
        api.getTransferLogs(pageNum, pageSize, projectId, actionType).toResource(PageResult())
    }
}
