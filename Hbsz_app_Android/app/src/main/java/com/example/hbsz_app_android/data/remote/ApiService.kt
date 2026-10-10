package com.example.hbsz_app_android.data.remote

import com.example.hbsz_app_android.data.remote.dto.ArchiveStatisticsDto
import com.example.hbsz_app_android.data.remote.dto.AuthApproveRequest
import com.example.hbsz_app_android.data.remote.dto.ChangePasswordRequest
import com.example.hbsz_app_android.data.remote.dto.CaptchaResponse
import com.example.hbsz_app_android.data.remote.dto.CustomerDto
import com.example.hbsz_app_android.data.remote.dto.DailyReportHistoryDto
import com.example.hbsz_app_android.data.remote.dto.DailyReportPreviewDto
import com.example.hbsz_app_android.data.remote.dto.DashboardSummaryDto
import com.example.hbsz_app_android.data.remote.dto.DocumentDto
import com.example.hbsz_app_android.data.remote.dto.DocumentReviewRequest
import com.example.hbsz_app_android.data.remote.dto.ExportRecordDto
import com.example.hbsz_app_android.data.remote.dto.FileUploadDto
import com.example.hbsz_app_android.data.remote.dto.LoginRequest
import com.example.hbsz_app_android.data.remote.dto.LoginResponse
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
import com.example.hbsz_app_android.data.remote.dto.WorkloadRecordBatchRequest
import com.example.hbsz_app_android.data.remote.dto.WorkloadRecordDto
import com.example.hbsz_app_android.data.remote.dto.WorkloadReviewRequest
import com.example.hbsz_app_android.data.remote.dto.WorkloadWorkerDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * 后端 API 定义（路径已逐个核实后端 Controller）。
 * BASE_URL 已含 /api 上下文路径，此处只写相对路径。
 */
interface ApiService {

    // ==================== 认证（AuthController + CaptchaController） ====================

    /** GET /captcha 图形验证码 */
    @GET("captcha")
    suspend fun getCaptcha(): ApiResponse<CaptchaResponse>

    /** POST /auth/login 登录 */
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): ApiResponse<LoginResponse>

    /** POST /auth/logout 登出 */
    @POST("auth/logout")
    suspend fun logout(): ApiResponse<Unit>

    /** GET /auth/info 当前用户信息 */
    @GET("auth/info")
    suspend fun getUserInfo(): ApiResponse<UserInfoDto>

    // ==================== 用户设置（UserController /user） ====================

    /** GET /user/profile 个人资料 */
    @GET("user/profile")
    suspend fun getProfile(): ApiResponse<UserInfoDto>

    /** PUT /user/profile 更新个人资料 */
    @PUT("user/profile")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ApiResponse<UserInfoDto>

    /** PUT /user/password 修改密码 */
    @PUT("user/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): ApiResponse<Unit>

    /** GET /user/notifications 通知设置 */
    @GET("user/notifications")
    suspend fun getNotificationSettings(): ApiResponse<NotificationSettingsDto>

    /** PUT /user/notifications 更新通知设置 */
    @PUT("user/notifications")
    suspend fun updateNotificationSettings(@Body body: NotificationSettingsDto): ApiResponse<NotificationSettingsDto>

    // ==================== 工作台（DashboardController /dashboard） ====================

    /** GET /dashboard/summary 工作台汇总 */
    @GET("dashboard/summary")
    suspend fun getDashboardSummary(): ApiResponse<DashboardSummaryDto>

    /** GET /dashboard/my-tasks 我的任务（分页，status 可空） */
    @GET("dashboard/my-tasks")
    suspend fun getMyTasks(
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("status") status: String? = null
    ): ApiResponse<PageResult<MyTaskItemDto>>

    /** GET /dashboard/my-submissions 我的填报（分页，reviewStatus 可空） */
    @GET("dashboard/my-submissions")
    suspend fun getMySubmissions(
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("reviewStatus") reviewStatus: String? = null
    ): ApiResponse<PageResult<ProgressLogDto>>

    // ==================== 项目（ProjectController + ArchiveController /projects） ====================

    /** GET /projects 项目列表（分页 + 筛选） */
    @GET("projects")
    suspend fun getProjects(
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("keyword") keyword: String? = null,
        @Query("status") status: String? = null,
        @Query("archived") archived: Boolean? = null
    ): ApiResponse<PageResult<ProjectDto>>

    /** GET /projects/{id} 项目详情 */
    @GET("projects/{id}")
    suspend fun getProjectDetail(@Path("id") id: Long): ApiResponse<ProjectDto>

    /** GET /projects/statistics 项目统计概览 */
    @GET("projects/statistics")
    suspend fun getProjectStatistics(): ApiResponse<ProjectStatisticsDto>

    /** GET /projects/archived 归档项目列表 */
    @GET("projects/archived")
    suspend fun getArchivedProjects(
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("keyword") keyword: String? = null
    ): ApiResponse<PageResult<ProjectDto>>

    /** GET /projects/archived/statistics 归档统计 */
    @GET("projects/archived/statistics")
    suspend fun getArchiveStatistics(): ApiResponse<ArchiveStatisticsDto>

    /** PUT /projects/{id}/archive 归档项目 */
    @PUT("projects/{id}/archive")
    suspend fun archiveProject(@Path("id") id: Long): ApiResponse<ProjectDto>

    // ==================== 任务与进度（TaskController） ====================

    /** GET /projects/{projectId}/tasks 项目任务列表 */
    @GET("projects/{projectId}/tasks")
    suspend fun getProjectTasks(@Path("projectId") projectId: Long): ApiResponse<List<TaskDto>>

    /** GET /projects/{projectId}/progress 项目整体进度（含任务） */
    @GET("projects/{projectId}/progress")
    suspend fun getProjectProgress(@Path("projectId") projectId: Long): ApiResponse<ProjectProgressDto>

    /** PUT /tasks/{id}/progress 提交进度填报 */
    @PUT("tasks/{id}/progress")
    suspend fun reportProgress(
        @Path("id") taskId: Long,
        @Body body: ProgressReportRequest
    ): ApiResponse<ProgressLogDto>

    /**
     * GET /tasks/progress/pending 待审核进度日志列表
     * data 存在两种格式，故收 JsonElement 由仓库层统一兼容解析：
     * - 旧版：data 为数组（后端未分页，一次返回全量）
     * - 新版：data 为 PageResult 对象（records/total/current/pages）
     */
    @GET("tasks/progress/pending")
    suspend fun getPendingProgress(
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("projectId") projectId: Long? = null
    ): ApiResponse<kotlinx.serialization.json.JsonElement>

    /** PUT /tasks/progress/{logId}/review 审核进度填报 */
    @PUT("tasks/progress/{logId}/review")
    suspend fun reviewProgress(
        @Path("logId") logId: Long,
        @Body body: ProgressReviewRequest
    ): ApiResponse<ProgressLogDto>

    /** GET /tasks/{id}/logs 任务进度日志历史 */
    @GET("tasks/{id}/logs")
    suspend fun getTaskProgressLogs(@Path("id") taskId: Long): ApiResponse<List<ProgressLogDto>>

    /** GET /tasks/upcoming 全部关键节点提醒 */
    @GET("tasks/upcoming")
    suspend fun getUpcomingKeyTasks(): ApiResponse<List<TaskDto>>

    // ==================== 通知（NotificationController /notifications） ====================

    /** GET /notifications 通知列表（分页） */
    @GET("notifications")
    suspend fun getNotifications(
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("notifyType") notifyType: String? = null,
        @Query("isRead") isRead: Boolean? = null
    ): ApiResponse<PageResult<NotificationDto>>

    /** GET /notifications/unread-count 未读数 */
    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): ApiResponse<Int>

    /** PUT /notifications/{id}/read 标记已读 */
    @PUT("notifications/{id}/read")
    suspend fun markAsRead(@Path("id") id: Long): ApiResponse<Unit>

    /** PUT /notifications/read-all 全部已读 */
    @PUT("notifications/read-all")
    suspend fun markAllAsRead(): ApiResponse<Unit>

    /** DELETE /notifications/{id} 删除通知 */
    @DELETE("notifications/{id}")
    suspend fun deleteNotification(@Path("id") id: Long): ApiResponse<Unit>

    // ==================== 文档（DocumentController /documents） ====================

    /** GET /documents 文档列表（分页 + 筛选） */
    @GET("documents")
    suspend fun getDocuments(
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("keyword") keyword: String? = null,
        @Query("category") category: String? = null,
        @Query("projectId") projectId: Long? = null,
        @Query("status") status: String? = null
    ): ApiResponse<PageResult<DocumentDto>>

    /** GET /documents/{id} 文档详情 */
    @GET("documents/{id}")
    suspend fun getDocumentDetail(@Path("id") id: Long): ApiResponse<DocumentDto>

    /** DELETE /documents/{id} 删除文档 */
    @DELETE("documents/{id}")
    suspend fun deleteDocument(@Path("id") id: Long): ApiResponse<Unit>

    /** GET /documents/{id}/download 文档下载（二进制流，document:download 权限） */
    @Streaming
    @GET("documents/{id}/download")
    suspend fun downloadDocument(@Path("id") id: Long): okhttp3.ResponseBody

    // ==================== 客户（CustomerController /customers） ====================

    /** GET /customers 客户列表（分页） */
    @GET("customers")
    suspend fun getCustomers(
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("keyword") keyword: String? = null,
        @Query("status") status: Int? = null
    ): ApiResponse<PageResult<CustomerDto>>

    /** GET /customers/{id} 客户详情 */
    @GET("customers/{id}")
    suspend fun getCustomerDetail(@Path("id") id: Long): ApiResponse<CustomerDto>

    // ==================== 统计（StatisticsController /statistics） ====================

    /** GET /statistics/overview 综合统计概览 */
    @GET("statistics/overview")
    suspend fun getStatisticsOverview(): ApiResponse<StatisticsOverviewDto>

    // ==================== 通用文件（FileController /files） ====================

    /** POST /files/upload 上传文件（施工图片），返回相对路径 */
    @Multipart
    @POST("files/upload")
    suspend fun uploadFile(@Part file: MultipartBody.Part): ApiResponse<FileUploadDto>

    // ==================== 甘特图日报（DailyReportController /daily-report） ====================

    /** GET /daily-report/preview 日报预览数据 */
    @GET("daily-report/preview")
    suspend fun getDailyReportPreview(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("projectId") projectId: Long? = null
    ): ApiResponse<DailyReportPreviewDto>

    /** POST /daily-report/export 导出日报 Excel */
    @POST("daily-report/export")
    suspend fun exportDailyReport(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("projectId") projectId: Long? = null
    ): ApiResponse<ExportRecordDto>

    /** GET /daily-report/history 导出历史（分页，仅当前用户） */
    @GET("daily-report/history")
    suspend fun getExportHistory(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 10
    ): ApiResponse<DailyReportHistoryDto>

    /** GET /daily-report/download/{id} 重新下载导出文件（xlsx 二进制流） */
    @Streaming
    @GET("daily-report/download/{id}")
    suspend fun downloadExport(@Path("id") id: Long): okhttp3.ResponseBody

    // ==================== 工作量统计（WorkloadController /workload） ====================

    /** GET /workload/workers 项目可填报人员（项目组在组成员，组长置前） */
    @GET("workload/workers")
    suspend fun listWorkloadWorkers(
        @Query("projectId") projectId: Long
    ): ApiResponse<List<WorkloadWorkerDto>>

    /** GET /workload/items 工作量清单（含已完成/剩余/当日完成实时聚合） */
    @GET("workload/items")
    suspend fun getWorkloadItems(
        @Query("projectId") projectId: Long,
        @Query("statisticsDate") statisticsDate: String? = null
    ): ApiResponse<List<WorkloadItemDto>>

    /** POST /workload/records 批量保存某日填报（保存后进入待审核状态） */
    @POST("workload/records")
    suspend fun saveWorkloadRecords(
        @Body body: WorkloadRecordBatchRequest
    ): ApiResponse<Unit>

    /** GET /workload/records/pending 填报明细审核列表（按项目/日期/状态筛选） */
    @GET("workload/records/pending")
    suspend fun getWorkloadPendingRecords(
        @Query("projectId") projectId: Long,
        @Query("recordDate") recordDate: String? = null,
        @Query("reviewStatus") reviewStatus: String? = null
    ): ApiResponse<List<WorkloadRecordDto>>

    /**
     * GET /workload/my/report-context 我的快速填报上下文（2026-08 重构）：
     * 常用清单项（近 14 天频次序）+ 今日已有填报（含审核状态）+ 昨日数量（复制昨天）。
     * projectId 为空时仅返回我参与的项目列表。
     */
    @GET("workload/my/report-context")
    suspend fun getMyReportContext(
        @Query("projectId") projectId: Long? = null,
        @Query("date") date: String? = null
    ): ApiResponse<WorkloadMyReportContextDto>

    /** POST /workload/records/review 批量审核填报（CAS 语义，返回实际成功条数） */
    @POST("workload/records/review")
    suspend fun reviewWorkloadRecords(
        @Body body: WorkloadReviewRequest
    ): ApiResponse<Int>

    // ==================== 审批待办中心（ApprovalTodoController /approval/todos） ====================

    /** GET /approval/todos 分页查询当前用户审批待办（source 为空查全部） */
    @GET("approval/todos")
    suspend fun getApprovalTodos(
        @Query("source") source: String? = null,
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<PageResult<TodoItemDto>>

    /** GET /approval/todos/count 待办分类计数（进度/工作量/文档/授权/合计） */
    @GET("approval/todos/count")
    suspend fun getTodoCounts(): ApiResponse<TodoCountDto>

    // ==================== 审批分发（复用各业务模块审核接口） ====================
    // 待办中心快捷审核按 source 分发：progress 复用上方 reviewProgress，
    // workload 复用上方 reviewWorkloadRecords，document/auth 如下。

    /** POST /documents/{id}/review 审核文档（通过=已审核，驳回=已驳回） */
    @POST("documents/{id}/review")
    suspend fun reviewDocument(
        @Path("id") id: Long,
        @Body body: DocumentReviewRequest
    ): ApiResponse<kotlinx.serialization.json.JsonElement>

    /** POST /project-auth/requests/{id}/approve 审批授权申请（approve=false 即驳回） */
    @POST("project-auth/requests/{id}/approve")
    suspend fun approveAuthRequest(
        @Path("id") id: Long,
        @Body body: AuthApproveRequest
    ): ApiResponse<kotlinx.serialization.json.JsonElement>

    // ==================== 项目授权与转移（ProjectAuthorizationController） ====================
    // 2026-08 后端重设计：转移支持负责人移交（toManagerId）与工程师替换（toEngineerId），
    // 两者可组合；写操作均含 @Idempotent 幂等保护 + 所有权校验（admin/项目负责人）。

    /**
     * POST /projects/{projectId}/authorization/transfer 项目转移。
     * toManagerId：移交项目所有权（审批待办实时流向新负责人）；
     * toEngineerId：撤销全部现有工程师授权并授权目标工程师。
     * 仅移交负责人时后端 data 为 null，故泛型收 ProjectAssignmentDto（data 可空）。
     */
    @POST("projects/{projectId}/authorization/transfer")
    suspend fun transferProject(
        @Path("projectId") projectId: Long,
        @Body body: ProjectTransferRequest
    ): ApiResponse<ProjectAssignmentDto>

    /** POST /projects/{projectId}/authorization 新增一名工程师授权（不影响其他已授权工程师） */
    @POST("projects/{projectId}/authorization")
    suspend fun assignEngineer(
        @Path("projectId") projectId: Long,
        @Body body: ProjectTransferRequest
    ): ApiResponse<ProjectAssignmentDto>

    /** DELETE /projects/{projectId}/authorization/{engineerId} 撤销指定工程师授权 */
    @DELETE("projects/{projectId}/authorization/{engineerId}")
    suspend fun revokeEngineer(
        @Path("projectId") projectId: Long,
        @Path("engineerId") engineerId: Long,
        @Query("remark") remark: String? = null
    ): ApiResponse<Unit>

    /** GET /projects/{projectId}/authorization 项目下有效授权工程师列表 */
    @GET("projects/{projectId}/authorization")
    suspend fun listProjectEngineers(
        @Path("projectId") projectId: Long
    ): ApiResponse<List<ProjectAssignmentDto>>

    /** GET /projects/authorization/engineers 活跃工程师下拉（转移/授权选择） */
    @GET("projects/authorization/engineers")
    suspend fun listActiveEngineers(): ApiResponse<List<ActiveUserDto>>

    /** GET /projects/authorization/managers 活跃项目经理下拉（负责人移交选择） */
    @GET("projects/authorization/managers")
    suspend fun listActiveManagers(): ApiResponse<List<ActiveUserDto>>

    /** GET /projects/authorization/logs 转移/授权审计日志（分页，actionType 可空查全部） */
    @GET("projects/authorization/logs")
    suspend fun getTransferLogs(
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("projectId") projectId: Long? = null,
        @Query("actionType") actionType: String? = null
    ): ApiResponse<PageResult<TransferLogDto>>
}
