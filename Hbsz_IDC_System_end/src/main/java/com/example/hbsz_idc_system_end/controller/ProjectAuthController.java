package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.OperationLog;
import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.AssignmentQueryDTO;
import com.example.hbsz_idc_system_end.dto.AssignmentVO;
import com.example.hbsz_idc_system_end.dto.AuthRequestApproveDTO;
import com.example.hbsz_idc_system_end.dto.AuthRequestCreateDTO;
import com.example.hbsz_idc_system_end.dto.AuthRequestQueryDTO;
import com.example.hbsz_idc_system_end.dto.AuthRequestVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.service.IProjectAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目授权申请与授权管理控制器
 * <p>
 * 阶段3：项目授权规范化。基础路径：{@code /api/project-auth}。
 * <p>
 * 提供项目授权申请的创建、查询、审批，以及授权关系的查询、撤销、延期等接口。
 * 权限要求：
 * <ul>
 *   <li>申请/查询授权：{@code project:auth:request}；</li>
 *   <li>审批/撤销/延期：{@code project:auth:approve}。</li>
 * </ul>
 *
 * @author hbsz
 */
@Tag(name = "项目授权申请", description = "项目授权申请创建、审批、授权关系管理")
@RestController
@RequestMapping("/project-auth")
public class ProjectAuthController {
    // 说明：context-path 已配置 /api（application.yml），故类级 @RequestMapping 不再重复 /api 前缀，
    // 实际访问路径为 /api/project-auth/xxx，与接口文档及前端调用保持一致。

    private final IProjectAuthService projectAuthService;

    public ProjectAuthController(IProjectAuthService projectAuthService) {
        this.projectAuthService = projectAuthService;
    }

    /* ====== 授权申请 ====== */

    /**
     * 创建项目授权申请。
     * <p>HTTP：POST /api/project-auth/requests。
     * 权限：{@code project:auth:request}；记录操作日志。
     *
     * @param dto 申请参数
     * @return 创建后的申请 VO
     */
    @Operation(summary = "创建授权申请", description = "创建项目授权申请，状态为 pending")
    @RequiresPermission("project:auth:request")
    @OperationLog(module = "项目授权", type = "申请", description = "创建授权申请")
    @PostMapping("/requests")
    public Result<AuthRequestVO> createAuthRequest(@Valid @RequestBody AuthRequestCreateDTO dto) {
        return Result.success("授权申请已创建", projectAuthService.createAuthRequest(dto));
    }

    /**
     * 分页查询项目授权申请列表。
     * <p>HTTP：GET /api/project-auth/requests。
     * 权限：{@code project:auth:request}。
     *
     * @param query 查询条件（status / projectId / applicantId）
     * @return 分页结果
     */
    @Operation(summary = "授权申请列表", description = "分页查询项目授权申请列表")
    @RequiresPermission("project:auth:request")
    @GetMapping("/requests")
    public Result<PageResult<AuthRequestVO>> listAuthRequests(AuthRequestQueryDTO query) {
        return Result.success(projectAuthService.listAuthRequests(query));
    }

    /**
     * 查询当前用户申请的授权列表。
     * <p>HTTP：GET /api/project-auth/requests/my。
     * 权限：{@code project:auth:request}。
     *
     * @return 申请 VO 列表
     */
    @Operation(summary = "我的授权申请", description = "查询当前用户申请的授权列表")
    @RequiresPermission("project:auth:request")
    @GetMapping("/requests/my")
    public Result<List<AuthRequestVO>> myAuthRequests() {
        return Result.success(projectAuthService.getMyAuthRequests());
    }

    /**
     * 查询待当前用户审批的申请列表。
     * <p>HTTP：GET /api/project-auth/requests/pending。
     * 权限：{@code project:auth:request}。
     *
     * @return 待审批申请 VO 列表
     */
    @Operation(summary = "待我审批的申请", description = "查询待当前用户审批的申请列表")
    @RequiresPermission("project:auth:request")
    @GetMapping("/requests/pending")
    public Result<List<AuthRequestVO>> pendingAuthRequests() {
        return Result.success(projectAuthService.getPendingAuthRequests());
    }

    /**
     * 审批授权申请（通过/驳回二合一接口）。
     * <p>HTTP：POST /api/project-auth/requests/{id}/approve。
     * 权限：{@code project:auth:approve}；记录操作日志。
     * <p>
     * 注：dto.approve=true 时审批通过，dto.approve=false 时驳回。
     *
     * @param id  申请ID
     * @param dto 审批参数
     * @return 审批后的申请 VO
     */
    @Operation(summary = "审批授权申请", description = "审批通过或驳回授权申请")
    @RequiresPermission("project:auth:approve")
    @OperationLog(module = "项目授权", type = "审批", description = "审批授权申请")
    @PostMapping("/requests/{id}/approve")
    public Result<AuthRequestVO> approveAuthRequest(@Parameter(description = "申请ID", required = true) @PathVariable Long id,
                                                     @Valid @RequestBody AuthRequestApproveDTO dto) {
        return Result.success("审批完成", projectAuthService.approveAuthRequest(id, dto));
    }

    /**
     * 驳回授权申请。
     * <p>HTTP：POST /api/project-auth/requests/{id}/reject。
     * 权限：{@code project:auth:approve}；记录操作日志。
     *
     * @param id  申请ID
     * @param dto 审批参数（approve=false、驳回意见）
     * @return 驳回后的申请 VO
     */
    @Operation(summary = "驳回授权申请", description = "驳回授权申请")
    @RequiresPermission("project:auth:approve")
    @OperationLog(module = "项目授权", type = "驳回", description = "驳回授权申请")
    @PostMapping("/requests/{id}/reject")
    public Result<AuthRequestVO> rejectAuthRequest(@Parameter(description = "申请ID", required = true) @PathVariable Long id,
                                                   @Valid @RequestBody AuthRequestApproveDTO dto) {
        return Result.success("已驳回", projectAuthService.rejectAuthRequest(id, dto));
    }

    /* ====== 授权关系管理 ====== */

    /**
     * 分页查询项目授权关系列表。
     * <p>HTTP：GET /api/project-auth/assignments。
     * 权限：{@code project:auth:request}。
     *
     * @param query 查询条件（projectId / engineerId / isActive / expireSoon）
     * @return 分页结果
     */
    @Operation(summary = "授权关系列表", description = "分页查询项目-工程师授权关系")
    @RequiresPermission("project:auth:request")
    @GetMapping("/assignments")
    public Result<PageResult<AssignmentVO>> listAssignments(AssignmentQueryDTO query) {
        return Result.success(projectAuthService.listAssignments(query));
    }

    /**
     * 撤销授权。
     * <p>HTTP：DELETE /api/project-auth/assignments/{id}。
     * 权限：{@code project:auth:approve}；记录操作日志。
     *
     * @param id     授权记录ID
     * @param reason 撤销原因（query 参数）
     * @return 无业务数据
     */
    @Operation(summary = "撤销授权", description = "撤销项目-工程师授权关系")
    @RequiresPermission("project:auth:approve")
    @OperationLog(module = "项目授权", type = "撤销", description = "撤销授权")
    @DeleteMapping("/assignments/{id}")
    public Result<Void> revokeAssignment(@Parameter(description = "授权记录ID", required = true) @PathVariable Long id,
                                        @RequestParam(required = false) String reason) {
        projectAuthService.revokeAssignment(id, reason);
        return Result.success("已撤销授权", null);
    }

    /**
     * 延期授权。
     * <p>HTTP：PUT /api/project-auth/assignments/{id}/extend。
     * 权限：{@code project:auth:approve}；记录操作日志。
     *
     * @param id          授权记录ID
     * @param newExpireAt 新的过期时间（query 参数，格式 yyyy-MM-dd'T'HH:mm:ss）
     * @return 无业务数据
     */
    @Operation(summary = "延期授权", description = "延长项目-工程师授权的有效期")
    @RequiresPermission("project:auth:approve")
    @OperationLog(module = "项目授权", type = "延期", description = "延期授权")
    @PutMapping("/assignments/{id}/extend")
    public Result<Void> extendAssignment(@Parameter(description = "授权记录ID", required = true) @PathVariable Long id,
                                         @RequestParam LocalDateTime newExpireAt) {
        projectAuthService.extendAssignment(id, newExpireAt);
        return Result.success("授权已延期", null);
    }

}
