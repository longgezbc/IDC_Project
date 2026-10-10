package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.OperationLog;
import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.ProjectAssignmentVO;
import com.example.hbsz_idc_system_end.dto.ProjectTransferDTO;
import com.example.hbsz_idc_system_end.dto.TransferLogQueryDTO;
import com.example.hbsz_idc_system_end.dto.TransferLogVO;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.service.IProjectAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目授权与转移控制器
 * <p>
 * 基础路径：{@code /projects/{projectId}/authorization}（项目级授权操作）
 * 与 {@code /projects/authorization}（全局审计日志查询）。
 * <p>
 * 权限要求：
 * <ul>
 *   <li>转移/授权/撤销：{@code project:transfer}（项目经理专属）；</li>
 *   <li>查询授权工程师列表：{@code project:query}；</li>
 *   <li>查询活跃工程师下拉：{@code project:transfer}；</li>
 *   <li>查询转移审计日志：{@code project:query}。</li>
 * </ul>
 *
 * @author hbsz
 */
@Tag(name = "项目授权与转移", description = "PM 将项目授权/转移给工程师，维护授权列表与审计日志")
@RestController
public class ProjectAuthorizationController {

    private final IProjectAuthorizationService authorizationService;

    public ProjectAuthorizationController(IProjectAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /* ====== 项目级授权操作 ====== */

    /**
     * 项目转移：支持负责人移交与工程师替换（至少一项）。
     * <p>HTTP：POST /projects/{projectId}/authorization/transfer。
     * 权限：{@code project:transfer}；记录操作日志。
     * <ul>
     *   <li>toManagerId 非空：移交项目所有权，项目全部审批自动流向新负责人；</li>
     *   <li>toEngineerId 非空：撤销现有全部工程师授权并授权目标工程师。</li>
     * </ul>
     *
     * @param projectId 项目ID
     * @param dto       转移参数（新负责人ID / 新工程师ID + 备注，至少一项）
     * @return 转移后的授权记录（仅移交负责人时为 null）
     */
    @Operation(summary = "项目转移", description = "移交项目负责人（审批随之转移）和/或替换工程师，两者至少一项")
    @RequiresPermission("project:transfer")
    @OperationLog(module = "项目授权", type = "转移", description = "项目转移")
    @PostMapping("/projects/{projectId}/authorization/transfer")
    public Result<ProjectAssignmentVO> transfer(@Parameter(description = "项目ID", required = true) @PathVariable Long projectId,
                                                @Valid @RequestBody ProjectTransferDTO dto) {
        return Result.success("项目转移成功", authorizationService.transferProject(projectId, dto));
    }

    /**
     * 项目授权：为项目新增一名工程师授权（不影响其他已授权工程师）。
     * <p>HTTP：POST /projects/{projectId}/authorization。
     * 权限：{@code project:transfer}；记录操作日志。
     *
     * @param projectId  项目ID
     * @param dto        授权参数
     * @return 新建的授权记录
     */
    @Operation(summary = "项目授权", description = "为项目新增一名工程师授权")
    @RequiresPermission("project:transfer")
    @OperationLog(module = "项目授权", type = "授权", description = "项目授权")
    @PostMapping("/projects/{projectId}/authorization")
    public Result<ProjectAssignmentVO> assign(@Parameter(description = "项目ID", required = true) @PathVariable Long projectId,
                                              @Valid @RequestBody ProjectTransferDTO dto) {
        // ProjectTransferDTO.toEngineerId 已放宽为可选（转移支持仅移交负责人），授权接口单独保持必填契约
        if (dto.getToEngineerId() == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "目标工程师不能为空");
        }
        return Result.success("项目授权成功", authorizationService.assignEngineer(projectId, dto.getToEngineerId(), dto.getRemark()));
    }

    /**
     * 撤销授权：移除项目对指定工程师的授权。
     * <p>HTTP：DELETE /projects/{projectId}/authorization/{engineerId}。
     * 权限：{@code project:transfer}；记录操作日志。
     *
     * @param projectId   项目ID
     * @param engineerId  工程师ID
     * @param remark      撤销备注（query 参数）
     * @return 无业务数据
     */
    @Operation(summary = "撤销授权", description = "移除项目对指定工程师的授权")
    @RequiresPermission("project:transfer")
    @OperationLog(module = "项目授权", type = "撤销", description = "撤销授权")
    @DeleteMapping("/projects/{projectId}/authorization/{engineerId}")
    public Result<Void> revoke(@PathVariable Long projectId,
                               @PathVariable Long engineerId,
                               @RequestParam(required = false) String remark) {
        authorizationService.revokeEngineer(projectId, engineerId, remark);
        return Result.success("已撤销授权", null);
    }

    /**
     * 查询项目下的有效授权工程师列表。
     * <p>HTTP：GET /projects/{projectId}/authorization。
     * 权限：{@code project:query}。
     *
     * @param projectId 项目ID
     * @return 授权工程师 VO 列表
     */
    @Operation(summary = "项目授权工程师列表", description = "查询项目下所有有效授权工程师")
    @RequiresPermission("project:query")
    @GetMapping("/projects/{projectId}/authorization")
    public Result<List<ProjectAssignmentVO>> listProjectEngineers(@PathVariable Long projectId) {
        return Result.success(authorizationService.listProjectEngineers(projectId));
    }

    /* ====== 全局查询接口 ====== */

    /**
     * 查询所有启用状态的工程师（用于转移/授权下拉选择）。
     * <p>HTTP：GET /projects/authorization/engineers。
     * 权限：{@code project:transfer}。
     *
     * @return 启用工程师精简列表（id/username/name/phone）
     */
    @Operation(summary = "活跃工程师下拉", description = "查询所有启用状态的工程师，用于转移/授权下拉")
    @RequiresPermission("project:transfer")
    @GetMapping("/projects/authorization/engineers")
    public Result<List<Map<String, Object>>> listActiveEngineers() {
        List<SysUser> engineers = authorizationService.listActiveEngineers();
        // 精简输出，避免泄露密码等敏感字段
        List<Map<String, Object>> result = engineers.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("name", u.getName());
            m.put("phone", u.getPhone());
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    /**
     * 活跃项目经理下拉：查询所有启用状态的项目经理。
     * <p>HTTP：GET /projects/authorization/managers。
     * 权限：{@code project:transfer}。
     *
     * @return 启用项目经理列表（精简字段）
     */
    @Operation(summary = "活跃项目经理下拉", description = "查询所有启用状态的项目经理，用于项目转移时选择新负责人")
    @RequiresPermission("project:transfer")
    @GetMapping("/projects/authorization/managers")
    public Result<List<Map<String, Object>>> listActiveManagers() {
        List<SysUser> managers = authorizationService.listActiveManagers();
        // 精简输出，避免泄露密码等敏感字段
        List<Map<String, Object>> result = managers.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("name", u.getName());
            m.put("phone", u.getPhone());
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    /**
     * 分页查询项目转移审计日志。
     * <p>HTTP：GET /projects/authorization/logs。
     * 权限：{@code project:query}。
     *
     * @param query 查询条件（projectId / operatedBy / actionType / 时间范围）
     * @return 分页审计日志
     */
    @Operation(summary = "转移审计日志", description = "分页查询项目授权/转移审计日志")
    @RequiresPermission("project:query")
    @GetMapping("/projects/authorization/logs")
    public Result<PageResult<TransferLogVO>> transferLogs(TransferLogQueryDTO query) {
        return Result.success(authorizationService.queryTransferLogs(query));
    }

}
