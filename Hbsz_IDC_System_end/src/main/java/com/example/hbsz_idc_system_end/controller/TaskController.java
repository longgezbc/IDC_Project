package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RateLimit;
import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.*;
import com.example.hbsz_idc_system_end.service.ITaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务进度控制器。
 * <p>
 * 业务模块：项目进度管理。
 * 请求路径分布在 {@code /projects/{projectId}/tasks} 与 {@code /tasks} 两组路径下（未在类上统一声明 @RequestMapping）。
 * 提供项目任务的增删改查、进度填报、项目整体进度以及关键节点提醒等接口。
 *
 * @author hbsz
 */
@Tag(name = "进度管理", description = "项目任务、进度填报、关键节点提醒等接口")
@RestController
public class TaskController {

    /** 待审核进度日志列表：每页条数默认值 */
    private static final int DEFAULT_PENDING_PAGE_SIZE = 20;

    /** 待审核进度日志列表：每页条数上限，超过按 100 处理 */
    private static final int MAX_PENDING_PAGE_SIZE = 100;

    private final ITaskService taskService;

    public TaskController(ITaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 项目任务列表。
     * <p>HTTP：GET /projects/{projectId}/tasks。查询指定项目下的所有任务。
     * 权限：{@code task:query}。
     *
     * @param projectId 项目 ID
     * @return 该项目的任务列表
     */
    @Operation(summary = "项目任务列表", description = "查询指定项目的所有任务")
    @RequiresPermission("task:query")
    @GetMapping("/projects/{projectId}/tasks")
    public Result<List<TaskVO>> listByProject(@PathVariable Long projectId) {
        return Result.success(taskService.listByProject(projectId));
    }

    /**
     * 任务详情。
     * <p>HTTP：GET /tasks/{id}。
     * 权限：{@code task:query}。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @Operation(summary = "任务详情", description = "根据ID查询任务详情")
    @RequiresPermission("task:query")
    @GetMapping("/tasks/{id}")
    public Result<TaskVO> detail(@PathVariable Long id) {
        return Result.success(taskService.getDetail(id));
    }

    /**
     * 新增任务。
     * <p>HTTP：POST /projects/{projectId}/tasks。为指定项目创建任务。
     * 权限：{@code task:add}。
     *
     * @param projectId 项目 ID
     * @param dto       任务保存数据（经参数校验）
     * @return 新增后的任务
     */
    @Operation(summary = "新增任务", description = "为指定项目新增任务")
    @RequiresPermission("task:add")
    @RateLimit(key = "task_create", period = 60, count = 20)
    @PostMapping("/projects/{projectId}/tasks")
    public Result<TaskVO> create(@PathVariable Long projectId, @Valid @RequestBody TaskSaveDTO dto) {
        return Result.success("任务创建成功", taskService.create(projectId, dto));
    }

    /**
     * 更新任务。
     * <p>HTTP：PUT /tasks/{id}。
     * 权限：{@code task:edit}。
     *
     * @param id  任务 ID
     * @param dto 任务保存数据
     * @return 更新后的任务
     */
    @Operation(summary = "更新任务", description = "更新任务信息")
    @RequiresPermission("task:edit")
    @PutMapping("/tasks/{id}")
    // 启用 DTO 参数校验，防止非法输入
    public Result<TaskVO> update(@PathVariable Long id, @Valid @RequestBody TaskSaveDTO dto) {
        return Result.success("任务更新成功", taskService.update(id, dto));
    }

    /**
     * 删除任务。
     * <p>HTTP：DELETE /tasks/{id}。
     * 权限：{@code task:remove}。
     *
     * @param id 任务 ID
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "删除任务", description = "根据ID删除任务")
    @RequiresPermission("task:remove")
    @DeleteMapping("/tasks/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return Result.success("任务删除成功", null);
    }

    /**
     * 提交进度填报（工程师发起，进入项目经理审核流程）。
     * <p>HTTP：PUT /tasks/{id}/progress。
     * 提交后不会立即更新任务进度，而是创建一条 pending 状态的进度日志，
     * 等待项目经理审核通过后才会回写到 {@code biz_task.progress}。
     * 权限：{@code task:progress}。
     *
     * @param id  任务 ID
     * @param dto 进度填报数据（进度百分比、施工图片URL等）
     * @return 进度日志 VO（含日志ID与审核状态）
     */
    @Operation(summary = "提交进度填报", description = "工程师提交进度填报，进入项目经理审核流程")
    @RequiresPermission("task:progress")
    @RateLimit(key = "progress_report", period = 60, count = 30)
    @PutMapping("/tasks/{id}/progress")
    public Result<ProgressLogVO> reportProgress(@PathVariable Long id, @Valid @RequestBody ProgressReportDTO dto) {
        return Result.success("进度填报已提交，等待项目经理审核", taskService.reportProgress(id, dto));
    }

    /**
     * 审核进度填报（项目经理审核工程师提交的进度日志）。
     * <p>HTTP：PUT /tasks/progress/{logId}/review。
     * 审核通过时将进度值回写到 {@code biz_task.progress} 并同步任务状态与项目整体进度；
     * 审核驳回时仅将日志标记为 rejected，不影响任务实际进度。
     * 权限：{@code task:progress:review}。
     *
     * @param logId 进度日志 ID
     * @param dto   审核参数（是否通过、审核意见）
     * @return 审核后的进度日志 VO
     */
    @Operation(summary = "审核进度填报", description = "项目经理审核工程师提交的进度填报")
    @RequiresPermission("task:progress:review")
    @RateLimit(key = "progress_review", period = 60, count = 60)
    @PutMapping("/tasks/progress/{logId}/review")
    public Result<ProgressLogVO> reviewProgress(@PathVariable Long logId, @Valid @RequestBody ProgressReviewDTO dto) {
        return Result.success("审核完成", taskService.reviewProgress(logId, dto));
    }

    /**
     * 待审核进度日志列表。
     * <p>HTTP：GET /tasks/progress/pending。
     * 默认返回全部 review_status='pending' 的日志（data 为数组，兼容存量 Web / 小程序调用）；
     * 传 {@code paged=true} 时返回统一分页结构（data 为 {@code PageResult}，适配 Android 分页协议）。
     * 可选 projectId 限定范围。
     * 权限：{@code task:progress:review}。
     *
     * @param projectId 可选项目 ID，用于按项目筛选
     * @param paged     是否返回分页结构，默认 false（旧 List 格式）
     * @param pageNum   页码，从 1 开始，默认 1；非法值（&lt;1）按 1 处理（仅 paged=true 时生效）
     * @param pageSize  每页条数，默认 20，上限 100（超限按 100 处理）；非法值（&lt;1）按 20 处理（仅 paged=true 时生效）
     * @return 待审核进度日志列表（List）或分页结果（PageResult），取决于 paged 参数
     */
    @Operation(summary = "待审核进度日志列表", description = "查询待项目经理审核的进度填报列表；传 paged=true 返回分页结构（pageNum/pageSize），否则返回全量数组")
    @RequiresPermission("task:progress:review")
    @GetMapping("/tasks/progress/pending")
    public Result<?> listPendingProgress(
            @Parameter(description = "可选项目ID，按项目筛选") @RequestParam(value = "projectId", required = false) Long projectId,
            @Parameter(description = "是否返回分页结构，默认false返回List，true返回PageResult") @RequestParam(value = "paged", required = false, defaultValue = "false") boolean paged,
            @Parameter(description = "页码，从1开始，默认1") @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数，默认20，上限100") @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        if (!paged) {
            return Result.success(taskService.listPendingProgress(projectId));
        }
        // 分页参数容错：pageNum<1 由 PageQuery 校正为 1；pageSize<1 按本接口默认值 20（PageQuery 默认为 10，故在调用方校正），>100 按 100
        PageQuery query = new PageQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize < 1 ? DEFAULT_PENDING_PAGE_SIZE : Math.min(pageSize, MAX_PENDING_PAGE_SIZE));
        return Result.success(taskService.pagePendingProgress(projectId, query));
    }

    /**
     * 任务进度日志列表。
     * <p>HTTP：GET /tasks/{id}/logs。
     * 返回指定任务的历史进度填报记录（含审核状态），按填报时间倒序。
     * 权限：{@code task:query}。
     *
     * @param id 任务 ID
     * @return 该任务的进度日志列表
     */
    @Operation(summary = "任务进度日志", description = "查询指定任务的进度填报历史（含审核状态）")
    @RequiresPermission("task:query")
    @GetMapping("/tasks/{id}/logs")
    public Result<List<ProgressLogVO>> listProgressLogs(@PathVariable Long id) {
        return Result.success(taskService.listProgressLogs(id));
    }

    /**
     * 项目整体进度。
     * <p>HTTP：GET /projects/{projectId}/progress。获取项目整体进度及其任务列表。
     * 权限：{@code task:query}。
     *
     * @param projectId 项目 ID
     * @return 项目整体进度数据
     */
    @Operation(summary = "项目整体进度", description = "获取项目整体进度及任务列表")
    @RequiresPermission("task:query")
    @GetMapping("/projects/{projectId}/progress")
    public Result<ProjectProgressVO> getProjectProgress(@PathVariable Long projectId) {
        return Result.success(taskService.getProjectProgress(projectId));
    }

    /**
     * 项目关键节点提醒。
     * <p>HTTP：GET /projects/{projectId}/tasks/upcoming。获取指定项目未来 7 天内的关键节点任务。
     * 权限：{@code task:query}。
     *
     * @param projectId 项目 ID
     * @return 未来 7 天的关键节点任务列表
     */
    @Operation(summary = "项目关键节点提醒", description = "获取指定项目未来7天的关键节点")
    @RequiresPermission("task:query")
    @GetMapping("/projects/{projectId}/tasks/upcoming")
    public Result<List<TaskVO>> getUpcomingKeyTasks(@PathVariable Long projectId) {
        return Result.success(taskService.getUpcomingKeyTasks(projectId));
    }

    /**
     * 全部关键节点提醒。
     * <p>HTTP：GET /tasks/upcoming。获取所有项目未来 7 天内的关键节点任务。
     * 权限：{@code task:query}。
     *
     * @return 未来 7 天的关键节点任务列表
     */
    @Operation(summary = "全部关键节点提醒", description = "获取所有项目未来7天的关键节点")
    @RequiresPermission("task:query")
    @GetMapping("/tasks/upcoming")
    public Result<List<TaskVO>> getAllUpcomingKeyTasks() {
        return Result.success(taskService.getAllUpcomingKeyTasks());
    }

}
