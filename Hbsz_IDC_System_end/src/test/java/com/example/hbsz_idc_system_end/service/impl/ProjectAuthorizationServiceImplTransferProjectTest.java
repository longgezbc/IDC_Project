package com.example.hbsz_idc_system_end.service.impl;

import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.dto.ProjectAssignmentVO;
import com.example.hbsz_idc_system_end.dto.ProjectTransferDTO;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizProjectAssignment;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.BizProjectAssignmentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectTransferLogMapper;
import com.example.hbsz_idc_system_end.mapper.BizTaskMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.INotificationService;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.service.IProjectAuthService;
import com.example.hbsz_idc_system_end.service.IProjectTeamService;
import com.example.hbsz_idc_system_end.service.ProjectAccessGuard;
import com.example.hbsz_idc_system_end.service.ProjectTransferLogHelper;
import com.example.hbsz_idc_system_end.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectAuthorizationServiceImpl#transferProject(Long, ProjectTransferDTO)} 委托语义测试。
 * <p>
 * assignment 写路径收敛（debt-2）：项目转移不再 buildAssignment 直写授权，
 * 而是 撤销全部（softDeleteAllAssignmentsByProject）+ 直接授权（grantDirectAuthorization）：
 * <ul>
 *   <li>撤销与授权均委托 IProjectAuthService 单点，转移产生申请-审批留痕；</li>
 *   <li>remark 透传为授权理由（空时兜底 "项目转移自动授权"）；</li>
 *   <li>绝不直接 insert assignment / 调用 mapper 级 deactivateAllByProject；</li>
 *   <li>越权（非 admin 且非项目负责人）→ 拒绝，不进入委托链路。</li>
 * </ul>
 * 负责人移交（2026-08 重设计）：toManagerId 非空时委托 IProjectTeamService.transferLeader
 * 单点完成 manager_id 变更（审批待办实时流向新负责人），与工程师替换正交、可组合。
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("项目转移-委托授权写路径单点测试")
class ProjectAuthorizationServiceImplTransferProjectTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long PM_ID = 10L;
    private static final Long FROM_ENGINEER_ID = 30L;
    private static final Long TO_ENGINEER_ID = 20L;
    private static final Long TO_MANAGER_ID = 40L;

    @Mock
    private BizProjectAssignmentMapper assignmentMapper;
    @Mock
    private BizProjectTransferLogMapper transferLogMapper;
    @Mock
    private BizProjectMapper projectMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private BizTaskMapper taskMapper;
    @Mock
    private IPermissionService permissionService;
    @Mock
    private INotificationService notificationService;
    @Mock
    private IProjectAuthService projectAuthService;
    @Mock
    private IProjectTeamService projectTeamService;

    private ProjectAuthorizationServiceImpl service;

    @BeforeEach
    void setUp() {
        // 所有权校验委托共享 ProjectAccessGuard——真实 guard + mock 依赖穿透越权校验逻辑
        service = new ProjectAuthorizationServiceImpl(assignmentMapper, transferLogMapper,
                projectMapper, userMapper, taskMapper, permissionService, notificationService,
                projectAuthService, projectTeamService, new ProjectTransferLogHelper(userMapper, transferLogMapper),
                new ProjectAccessGuard(projectMapper, assignmentMapper, permissionService));
        UserContext.setUserId(PM_ID);
        UserContext.setUsername("项目负责人");

        when(permissionService.getCurrentUserRoles()).thenReturn(Set.of("project_manager"));
        BizProject project = new BizProject();
        project.setId(PROJECT_ID);
        project.setName("测试项目");
        project.setManagerId(PM_ID);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        SysUser pm = new SysUser();
        pm.setId(PM_ID);
        pm.setName("项目负责人");
        when(userMapper.selectById(PM_ID)).thenReturn(pm);

        // 转移目标工程师（validateEngineer 放行）
        SysUser toEngineer = new SysUser();
        toEngineer.setId(TO_ENGINEER_ID);
        toEngineer.setName("新工程师小王");
        toEngineer.setStatus(1);
        when(userMapper.selectById(TO_ENGINEER_ID)).thenReturn(toEngineer);
        when(userMapper.selectRoleCodesByUserId(TO_ENGINEER_ID)).thenReturn(List.of("engineer"));

        // 原授权工程师（用于审计 from 记录）
        SysUser fromEngineer = new SysUser();
        fromEngineer.setId(FROM_ENGINEER_ID);
        fromEngineer.setName("原工程师老李");
        fromEngineer.setStatus(1);
        when(userMapper.selectById(FROM_ENGINEER_ID)).thenReturn(fromEngineer);

        // 移交目标负责人（validateManager 放行：启用 + project_manager 角色）
        SysUser toManager = new SysUser();
        toManager.setId(TO_MANAGER_ID);
        toManager.setName("新负责人小张");
        toManager.setStatus(1);
        when(userMapper.selectById(TO_MANAGER_ID)).thenReturn(toManager);
        when(userMapper.selectRoleCodesByUserId(TO_MANAGER_ID)).thenReturn(List.of("project_manager"));

        ProjectAssignmentVO oldAssignment = new ProjectAssignmentVO();
        oldAssignment.setId(1L);
        oldAssignment.setProjectId(PROJECT_ID);
        oldAssignment.setEngineerId(FROM_ENGINEER_ID);
        when(assignmentMapper.selectAssignmentsByProject(PROJECT_ID))
                .thenReturn(List.of(oldAssignment))
                .thenReturn(List.of(newAssignmentVO()));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private ProjectAssignmentVO newAssignmentVO() {
        ProjectAssignmentVO vo = new ProjectAssignmentVO();
        vo.setId(2L);
        vo.setProjectId(PROJECT_ID);
        vo.setEngineerId(TO_ENGINEER_ID);
        vo.setIsActive(1);
        return vo;
    }

    private ProjectTransferDTO transferDTO(String remark) {
        ProjectTransferDTO dto = new ProjectTransferDTO();
        dto.setToEngineerId(TO_ENGINEER_ID);
        dto.setRemark(remark);
        return dto;
    }

    private ProjectTransferDTO managerDTO(Long toManagerId, String remark) {
        ProjectTransferDTO dto = new ProjectTransferDTO();
        dto.setToManagerId(toManagerId);
        dto.setRemark(remark);
        return dto;
    }

    @Test
    @DisplayName("正常路径：先批量软删再直接授权，remark 透传，不直写 assignment/mapper 级软删")
    void transferProject_delegatesRevokeAndGrant() {
        ProjectAssignmentVO result = service.transferProject(PROJECT_ID, transferDTO("移交给小王"));

        // 撤销与授权均委托单点
        verify(projectAuthService).softDeleteAllAssignmentsByProject(PROJECT_ID, "项目转移替换");
        verify(projectAuthService).grantDirectAuthorization(PROJECT_ID, TO_ENGINEER_ID, "移交给小王");
        // 关键语义：绝不绕过单点直写 assignment / 调 mapper 级批量软删
        verify(assignmentMapper, never()).insert(any(BizProjectAssignment.class));
        verify(assignmentMapper, never()).deactivateAllByProject(anyLong(), any());
        // 转移审计 + 静默通知照常
        verify(transferLogMapper).insert(any());
        verify(notificationService).sendNotification(any());
        assertEquals(TO_ENGINEER_ID, result.getEngineerId());
    }

    @Test
    @DisplayName("remark 为空：授权理由兜底 '项目转移自动授权'")
    void transferProject_blankRemarkFallsBack() {
        when(assignmentMapper.selectAssignmentsByProject(PROJECT_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(newAssignmentVO()));

        service.transferProject(PROJECT_ID, transferDTO(null));

        verify(projectAuthService).grantDirectAuthorization(PROJECT_ID, TO_ENGINEER_ID, "项目转移自动授权");
    }

    @Test
    @DisplayName("越权拒绝：非 admin 且非项目负责人不进入委托链路")
    void transferProject_rejectsNonOwner() {
        when(permissionService.getCurrentUserRoles()).thenReturn(Set.of("engineer"));
        BizProject othersProject = new BizProject();
        othersProject.setId(PROJECT_ID);
        othersProject.setName("他人负责的项目");
        othersProject.setManagerId(999L);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(othersProject);

        assertThrows(BusinessException.class,
                () -> service.transferProject(PROJECT_ID, transferDTO("备注")));

        verify(projectAuthService, never()).softDeleteAllAssignmentsByProject(anyLong(), any());
        verify(projectAuthService, never()).grantDirectAuthorization(anyLong(), anyLong(), any());
    }

    /* ====== 负责人移交（2026-08 重设计） ====== */

    @Test
    @DisplayName("仅移交负责人：委托 transferLeader 单点，不动工程师授权，静默通知新负责人")
    void transferProject_managerOnly_delegatesTransferLeader() {
        ProjectAssignmentVO result = service.transferProject(PROJECT_ID, managerDTO(TO_MANAGER_ID, "移交负责人"));

        // 所有权变更委托团队服务单点（manager_id + 组长 + 自动授权 + 审计）
        verify(projectTeamService).transferLeader(PROJECT_ID, TO_MANAGER_ID);
        // 工程师授权链路完全不触发
        verify(projectAuthService, never()).softDeleteAllAssignmentsByProject(anyLong(), any());
        verify(projectAuthService, never()).grantDirectAuthorization(anyLong(), anyLong(), any());
        // 静默通知新负责人（审批责任转移）
        verify(notificationService).sendNotification(any());
        // 仅移交时无新授权记录可返回
        assertEquals(null, result);
    }

    @Test
    @DisplayName("移交校验：新负责人不能是当前负责人本人")
    void transferProject_rejectsManagerSelfTransfer() {
        assertThrows(BusinessException.class,
                () -> service.transferProject(PROJECT_ID, managerDTO(PM_ID, "移交给自己")));

        verify(projectTeamService, never()).transferLeader(anyLong(), anyLong());
    }

    @Test
    @DisplayName("移交校验：目标用户不具备项目经理角色时拒绝")
    void transferProject_rejectsTargetWithoutManagerRole() {
        when(userMapper.selectRoleCodesByUserId(TO_MANAGER_ID)).thenReturn(List.of("engineer"));

        assertThrows(BusinessException.class,
                () -> service.transferProject(PROJECT_ID, managerDTO(TO_MANAGER_ID, "角色不符")));

        verify(projectTeamService, never()).transferLeader(anyLong(), anyLong());
    }

    @Test
    @DisplayName("参数校验：toManagerId 与 toEngineerId 至少传一项")
    void transferProject_rejectsEmptyTargets() {
        assertThrows(BusinessException.class,
                () -> service.transferProject(PROJECT_ID, managerDTO(null, "两个都空")));

        verify(projectTeamService, never()).transferLeader(anyLong(), anyLong());
        verify(projectAuthService, never()).grantDirectAuthorization(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("组合路径：同时移交负责人 + 替换工程师，两条链路均执行")
    void transferProject_managerAndEngineer_bothApplied() {
        ProjectTransferDTO dto = managerDTO(TO_MANAGER_ID, "移交并换人");
        dto.setToEngineerId(TO_ENGINEER_ID);

        ProjectAssignmentVO result = service.transferProject(PROJECT_ID, dto);

        // 负责人移交 + 工程师替换均生效
        verify(projectTeamService).transferLeader(PROJECT_ID, TO_MANAGER_ID);
        verify(projectAuthService).softDeleteAllAssignmentsByProject(PROJECT_ID, "项目转移替换");
        verify(projectAuthService).grantDirectAuthorization(eq(PROJECT_ID), eq(TO_ENGINEER_ID), any());
        assertEquals(TO_ENGINEER_ID, result.getEngineerId());
    }
}
