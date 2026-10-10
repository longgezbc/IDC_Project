package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.OperationLog;
import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.TeamMemberAddDTO;
import com.example.hbsz_idc_system_end.dto.TeamMemberQueryDTO;
import com.example.hbsz_idc_system_end.dto.TeamMemberVO;
import com.example.hbsz_idc_system_end.service.IProjectTeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目组管理控制器
 * <p>
 * 阶段4：项目组跨部门协作。基础路径：{@code /api/project-teams}。
 * <p>
 * 提供项目组成员的添加、移除、查询、转让组长等接口。
 * 权限要求：
 * <ul>
 *   <li>添加/移除/转让组长：{@code project:team:manage}；</li>
 *   <li>查询成员列表：{@code project:team:manage}；</li>
 *   <li>查询我参与的项目组：登录用户均可访问。</li>
 * </ul>
 *
 * @author hbsz
 */
@Tag(name = "项目组管理", description = "项目组成员管理、转让组长等接口")
@RestController
@RequestMapping("/project-teams")
public class ProjectTeamController {
    // 说明：context-path 已配置 /api（application.yml），故类级 @RequestMapping 不再重复 /api 前缀，
    // 实际访问路径为 /api/project-teams/xxx，与接口文档及前端调用保持一致。

    private final IProjectTeamService projectTeamService;

    public ProjectTeamController(IProjectTeamService projectTeamService) {
        this.projectTeamService = projectTeamService;
    }

    /**
     * 添加项目组成员。
     * <p>HTTP：POST /api/project-teams/members。
     * 权限：{@code project:team:manage}；记录操作日志。
     *
     * @param dto 添加参数
     * @return 添加后的成员 VO
     */
    @Operation(summary = "添加项目组成员", description = "添加成员到项目组，同时创建授权记录")
    @RequiresPermission("project:team:manage")
    @OperationLog(module = "项目组", type = "添加成员", description = "添加项目组成员")
    @PostMapping("/members")
    public Result<TeamMemberVO> addMember(@Valid @RequestBody TeamMemberAddDTO dto) {
        return Result.success("成员添加成功", projectTeamService.addMember(dto));
    }

    /**
     * 分页查询项目组成员列表。
     * <p>HTTP：GET /api/project-teams/members。
     * 权限：{@code project:team:manage}。
     *
     * @param query 查询条件（projectId / userId / roleInTeam / isActive）
     * @return 分页结果
     */
    @Operation(summary = "项目组成员列表", description = "分页查询项目组成员")
    @RequiresPermission("project:team:manage")
    @GetMapping("/members")
    public Result<PageResult<TeamMemberVO>> listMembers(TeamMemberQueryDTO query) {
        return Result.success(projectTeamService.listMembers(query));
    }

    /**
     * 移除项目组成员。
     * <p>HTTP：DELETE /api/project-teams/members/{id}。
     * 权限：{@code project:team:manage}；记录操作日志。
     *
     * @param id     记录ID
     * @param reason 移除原因（query 参数）
     * @return 无业务数据
     */
    @Operation(summary = "移除项目组成员", description = "移除项目组成员，同时撤销授权")
    @RequiresPermission("project:team:manage")
    @OperationLog(module = "项目组", type = "移除成员", description = "移除项目组成员")
    @DeleteMapping("/members/{id}")
    public Result<Void> removeMember(@Parameter(description = "记录ID", required = true) @PathVariable Long id,
                                      @RequestParam(required = false) String reason) {
        projectTeamService.removeMember(id, reason);
        return Result.success("成员已移除", null);
    }

    /**
     * 查询当前用户参与的项目组。
     * <p>HTTP：GET /api/project-teams/my。
     * 权限：登录用户均可访问（无 @RequiresPermission 注解，由 JWT 拦截器统一鉴权）。
     *
     * @return 项目组成员 VO 列表
     */
    @Operation(summary = "我参与的项目组", description = "查询当前用户参与的所有项目组")
    @GetMapping("/my")
    public Result<List<TeamMemberVO>> myTeams() {
        return Result.success(projectTeamService.listMyTeams());
    }

    /**
     * 转让组长。
     * <p>HTTP：POST /api/project-teams/{projectId}/transfer-leader。
     * 权限：{@code project:team:manage}；记录操作日志。
     *
     * @param projectId   项目ID
     * @param newLeaderId 新组长用户ID（query 参数）
     * @return 无业务数据
     */
    @Operation(summary = "转让组长", description = "转让项目组长，同步更新项目负责人")
    @RequiresPermission("project:team:manage")
    @OperationLog(module = "项目组", type = "转让组长", description = "转让项目组长")
    @PostMapping("/{projectId}/transfer-leader")
    public Result<Void> transferLeader(@Parameter(description = "项目ID", required = true) @PathVariable Long projectId,
                                        @RequestParam Long newLeaderId) {
        projectTeamService.transferLeader(projectId, newLeaderId);
        return Result.success("组长已转让", null);
    }

}
