package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.ApprovalFlowVO;
import com.example.hbsz_idc_system_end.service.IApprovalFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 审批流程图控制器。
 * <p>
 * 仅负责 HTTP 参数接收与响应包装；流程数据组装与查询
 * 委托 {@link IApprovalFlowService}（架构修复：不再直接注入 Mapper）。
 * <p>
 * 基础路径：{@code /approval-flow}。
 *
 * @author hbsz
 */
@Tag(name = "审批流程图", description = "将审批业务数据转换为流程图可视化数据")
@RestController
@RequestMapping("/approval-flow")
public class ApprovalFlowController {

    private final IApprovalFlowService approvalFlowService;

    public ApprovalFlowController(IApprovalFlowService approvalFlowService) {
        this.approvalFlowService = approvalFlowService;
    }

    // ==================== 项目授权审批流程 ====================

    /**
     * 获取项目授权申请的审批流程图数据。
     * <p>HTTP：GET /approval-flow/auth-request/{id}
     *
     * @param id 授权申请ID
     * @return 流程图 VO
     */
    @Operation(summary = "授权审批流程图", description = "获取项目授权申请的审批流程图数据")
    // H1修复：流程图详情接口缺少权限校验，添加 @RequiresPermission 防止越权访问审批数据
    @RequiresPermission("project:query")
    @GetMapping("/auth-request/{id}")
    public Result<ApprovalFlowVO> getAuthRequestFlow(@PathVariable Long id) {
        return Result.success(approvalFlowService.getAuthRequestFlow(id));
    }

    // ==================== 任务进度审核流程 ====================

    /**
     * 获取任务进度审核的审批流程图数据。
     * <p>HTTP：GET /approval-flow/progress-review/{id}
     *
     * @param id 进度填报记录ID
     * @return 流程图 VO
     */
    @Operation(summary = "进度审核流程图", description = "获取任务进度审核的审批流程图数据")
    // H1修复：流程图详情接口缺少权限校验，添加 @RequiresPermission 防止越权访问审批数据
    @RequiresPermission("project:query")
    @GetMapping("/progress-review/{id}")
    public Result<ApprovalFlowVO> getProgressReviewFlow(@PathVariable Long id) {
        return Result.success(approvalFlowService.getProgressReviewFlow(id));
    }

    // ==================== 项目转移审批流程 ====================

    /**
     * 获取项目转移的审批流程图数据。
     * <p>HTTP：GET /approval-flow/project-transfer/{id}
     *
     * @param id 转移日志ID
     * @return 流程图 VO
     */
    @Operation(summary = "项目转移流程图", description = "获取项目转移操作的审批流程图数据")
    // H1修复：流程图详情接口缺少权限校验，添加 @RequiresPermission 防止越权访问审批数据
    @RequiresPermission("project:query")
    @GetMapping("/project-transfer/{id}")
    public Result<ApprovalFlowVO> getProjectTransferFlow(@PathVariable Long id) {
        return Result.success(approvalFlowService.getProjectTransferFlow(id));
    }

    // ==================== 列表查询 ====================

    /**
     * 获取待审批的授权申请列表（供流程图列表页使用）。
     * <p>HTTP：GET /approval-flow/auth-requests
     *
     * @return 授权申请简要列表
     */
    @Operation(summary = "待审批授权申请列表", description = "获取待审批的授权申请简要列表")
    // H1修复：列表查询接口缺少权限校验，添加 @RequiresPermission 防止越权获取审批列表
    @RequiresPermission("project:query")
    @GetMapping("/auth-requests")
    public Result<List<Map<String, Object>>> listAuthRequests() {
        return Result.success(approvalFlowService.listAuthRequests());
    }

    /**
     * 获取待审核的进度填报列表（供流程图列表页使用）。
     * <p>HTTP：GET /approval-flow/progress-reviews
     *
     * @return 进度填报简要列表
     */
    @Operation(summary = "待审核进度列表", description = "获取待审核的进度填报简要列表")
    // H1修复：列表查询接口缺少权限校验，添加 @RequiresPermission 防止越权获取审批列表
    @RequiresPermission("project:query")
    @GetMapping("/progress-reviews")
    public Result<List<Map<String, Object>>> listProgressReviews() {
        return Result.success(approvalFlowService.listProgressReviews());
    }

    /**
     * 获取项目转移日志列表（供流程图列表页使用）。
     * <p>HTTP：GET /approval-flow/project-transfers
     *
     * @return 转移日志简要列表
     */
    @Operation(summary = "项目转移日志列表", description = "获取项目转移日志简要列表")
    // H1修复：列表查询接口缺少权限校验，添加 @RequiresPermission 防止越权获取审批列表
    @RequiresPermission("project:query")
    @GetMapping("/project-transfers")
    public Result<List<Map<String, Object>>> listProjectTransfers() {
        return Result.success(approvalFlowService.listProjectTransfers());
    }
}
