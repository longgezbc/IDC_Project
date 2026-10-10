package com.example.hbsz_idc_system_end.service.impl;

import com.example.hbsz_idc_system_end.common.exception.BusinessException;
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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectAuthorizationServiceImpl#revokeEngineer(Long, Long, String)} 委托语义测试。
 * <p>
 * 撤销双入口收敛：PM 撤销入口不再自行软删 assignment + 写审计，
 * 而是委托 {@link IProjectAuthService#revokeByProjectAndEngineer} 统一完成：
 * <ul>
 *   <li>正常路径恰好调用一次被委托方法，remark 原样透传；</li>
 *   <li>绝不直接 update assignment / 写审计日志（软删写路径单点收敛）；</li>
 *   <li>越权（非 admin 且非项目负责人）→ 拒绝，不进入委托链路。</li>
 * </ul>
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PM 撤销授权-委托统一软删路径测试")
class ProjectAuthorizationServiceImplRevokeEngineerTest {

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

        SysUser pm = new SysUser();
        pm.setId(PM_ID);
        pm.setName("项目负责人");
        when(userMapper.selectById(PM_ID)).thenReturn(pm);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("正常路径：委托 revokeByProjectAndEngineer 一次，remark 原样透传，不直接软删/写审计")
    void revokeEngineer_delegatesToUnifiedSoftDelete() {
        assertDoesNotThrow(() -> service.revokeEngineer(PROJECT_ID, ENGINEER_ID, "人员调整"));

        verify(projectAuthService).revokeByProjectAndEngineer(PROJECT_ID, ENGINEER_ID, "人员调整");
        // 关键语义：绝不绕过统一软删路径直接 update assignment / 写审计（写路径单点收敛）
        verify(assignmentMapper, never()).update(any(), any());
        verify(transferLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("越权拒绝：非 admin 且非项目负责人不进入委托链路")
    void revokeEngineer_rejectsNonOwner() {
        when(permissionService.getCurrentUserRoles()).thenReturn(Set.of("engineer"));
        BizProject othersProject = new BizProject();
        othersProject.setId(PROJECT_ID);
        othersProject.setName("他人负责的项目");
        othersProject.setManagerId(999L);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(othersProject);

        assertThrows(BusinessException.class,
                () -> service.revokeEngineer(PROJECT_ID, ENGINEER_ID, null));

        verify(projectAuthService, never()).revokeByProjectAndEngineer(any(), any(), any());
        verify(assignmentMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("无有效授权：被委托方异常向上传播（由调用方决定重试/提示）")
    void revokeEngineer_propagatesDelegateFailure() {
        doThrow(new BusinessException(com.example.hbsz_idc_system_end.common.result.ResultCode.DATA_NOT_FOUND,
                "该项目下未找到该工程师的有效授权"))
                .when(projectAuthService).revokeByProjectAndEngineer(PROJECT_ID, ENGINEER_ID, null);

        assertThrows(BusinessException.class,
                () -> service.revokeEngineer(PROJECT_ID, ENGINEER_ID, null));
    }
}
