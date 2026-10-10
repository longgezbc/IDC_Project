package com.example.hbsz_idc_system_end.service.impl;

import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.dto.AuthRequestVO;
import com.example.hbsz_idc_system_end.dto.ProjectAssignmentVO;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectAuthorizationServiceImpl#assignEngineer(Long, Long, String)} 委托语义测试。
 * <p>
 * 授权写路径收敛（单点）：PM 直接授权入口委托
 * {@link IProjectAuthService#grantDirectAuthorization}（代提申请 + 自动审批）：
 * <ul>
 *   <li>reason 透传（remark 空时兜底 "PM 直接授权"），由被委托方统一完成
 *       复用/代提申请、自动审批、assignment 写入与审计；</li>
 *   <li>被委托方返回 null（已存在有效授权，幂等跳过）时仍返回既有 assignment；</li>
 *   <li>绝不直接 insert assignment / 写 assign 审计日志（写路径收敛为单点）；</li>
 *   <li>越权（非 admin 且非项目负责人）→ 拒绝，不进入委托链路。</li>
 * </ul>
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PM 直接授权-委托 grantDirectAuthorization 测试")
class ProjectAuthorizationServiceImplAssignEngineerTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long PM_ID = 10L;
    private static final Long ENGINEER_ID = 20L;

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

        // 通用 stub：当前用户为 PM 角色 + 项目负责人（validateOwnership 放行）
        when(permissionService.getCurrentUserRoles()).thenReturn(Set.of("project_manager"));
        BizProject project = new BizProject();
        project.setId(PROJECT_ID);
        project.setName("测试项目");
        project.setManagerId(PM_ID);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        // 操作人姓名查询（getCurrentUserName）
        SysUser pm = new SysUser();
        pm.setId(PM_ID);
        pm.setName("项目负责人");
        when(userMapper.selectById(PM_ID)).thenReturn(pm);

        // 通知所需的目标工程师实体（存在、启用）
        SysUser engineer = new SysUser();
        engineer.setId(ENGINEER_ID);
        engineer.setName("工程师小王");
        engineer.setStatus(1);
        when(userMapper.selectById(ENGINEER_ID)).thenReturn(engineer);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 构造授权后查询返回的 assignment VO */
    private ProjectAssignmentVO assignmentVO() {
        ProjectAssignmentVO vo = new ProjectAssignmentVO();
        vo.setId(1L);
        vo.setProjectId(PROJECT_ID);
        vo.setEngineerId(ENGINEER_ID);
        vo.setIsActive(1);
        return vo;
    }

    @Test
    @DisplayName("正常路径：委托 grantDirectAuthorization 一次，remark 原样透传，不直接写 assignment")
    void assignEngineer_delegatesToGrantDirectAuthorization() {
        AuthRequestVO granted = new AuthRequestVO();
        granted.setId(500L);
        when(projectAuthService.grantDirectAuthorization(PROJECT_ID, ENGINEER_ID, "现场施工需要"))
                .thenReturn(granted);
        when(assignmentMapper.selectAssignmentsByProject(PROJECT_ID))
                .thenReturn(List.of(assignmentVO()));

        ProjectAssignmentVO result = service.assignEngineer(PROJECT_ID, ENGINEER_ID, "现场施工需要");

        // 委托恰好一次，reason = 调用方 remark
        verify(projectAuthService).grantDirectAuthorization(PROJECT_ID, ENGINEER_ID, "现场施工需要");
        // 关键语义：绝不绕过单点直写 assignment / 写 assign 审计
        verify(assignmentMapper, never()).insert(any(BizProjectAssignment.class));
        verify(transferLogMapper, never()).insert(any());
        assertEquals(ENGINEER_ID, result.getEngineerId());
    }

    @Test
    @DisplayName("remark 为空：reason 兜底 'PM 直接授权'")
    void assignEngineer_blankRemarkFallsBackToDefaultReason() {
        when(projectAuthService.grantDirectAuthorization(PROJECT_ID, ENGINEER_ID, "PM 直接授权"))
                .thenReturn(null);
        when(assignmentMapper.selectAssignmentsByProject(PROJECT_ID))
                .thenReturn(List.of(assignmentVO()));

        service.assignEngineer(PROJECT_ID, ENGINEER_ID, null);

        verify(projectAuthService).grantDirectAuthorization(PROJECT_ID, ENGINEER_ID, "PM 直接授权");
    }

    @Test
    @DisplayName("已存在有效授权（grant 返回 null 幂等跳过）：仍返回既有 assignment 并静默通知")
    void assignEngineer_grantSkippedReturnsExistingAssignment() {
        when(projectAuthService.grantDirectAuthorization(anyLong(), anyLong(), any()))
                .thenReturn(null);
        when(assignmentMapper.selectAssignmentsByProject(PROJECT_ID))
                .thenReturn(List.of(assignmentVO()));

        ProjectAssignmentVO result = service.assignEngineer(PROJECT_ID, ENGINEER_ID, "备注");

        // 幂等跳过不视为失败：返回既有授权记录
        assertEquals(ENGINEER_ID, result.getEngineerId());
        verify(notificationService).sendNotification(any());
    }

    @Test
    @DisplayName("越权拒绝：非 admin 且非项目负责人不进入委托链路")
    void assignEngineer_rejectsNonOwner() {
        // 当前用户非 admin 角色，且项目负责另有其人（managerId≠当前用户）
        when(permissionService.getCurrentUserRoles()).thenReturn(Set.of("engineer"));
        BizProject othersProject = new BizProject();
        othersProject.setId(PROJECT_ID);
        othersProject.setName("他人负责的项目");
        othersProject.setManagerId(999L);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(othersProject);

        assertThrows(BusinessException.class,
                () -> service.assignEngineer(PROJECT_ID, ENGINEER_ID, null));

        verify(projectAuthService, never()).grantDirectAuthorization(anyLong(), anyLong(), any());
        verify(assignmentMapper, never()).insert(any(BizProjectAssignment.class));
    }

    @Test
    @DisplayName("通知失败不影响授权主流程（静默降级）")
    void assignEngineer_notificationFailureIsSilent() {
        AuthRequestVO granted = new AuthRequestVO();
        granted.setId(500L);
        when(projectAuthService.grantDirectAuthorization(anyLong(), anyLong(), any()))
                .thenReturn(granted);
        when(assignmentMapper.selectAssignmentsByProject(PROJECT_ID))
                .thenReturn(List.of(assignmentVO()));
        doThrow(new RuntimeException("WebSocket down")).when(notificationService).sendNotification(any());

        ProjectAssignmentVO result = service.assignEngineer(PROJECT_ID, ENGINEER_ID, null);

        // 通知抛异常仍正常返回授权结果
        assertEquals(ENGINEER_ID, result.getEngineerId());
    }
}
