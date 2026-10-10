package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.dto.AuthRequestApproveDTO;
import com.example.hbsz_idc_system_end.dto.AuthRequestVO;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizProjectAssignment;
import com.example.hbsz_idc_system_end.entity.BizProjectAuthRequest;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.BizProjectAssignmentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectAuthRequestMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectTransferLogMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.service.ProjectTransferLogHelper;
import com.example.hbsz_idc_system_end.utils.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectAuthServiceImpl#approveAuthRequest}/{@link ProjectAuthServiceImpl#rejectAuthRequest}
 * CAS 审核语义测试。
 * <p>
 * 语义（与 TaskServiceImpl.reviewProgress / WorkloadServiceImpl.reviewRecords 的 CAS 审核范式一致）：
 * <ul>
 *   <li>状态流转使用 CAS 原子更新（UPDATE ... WHERE status='pending'），
 *       受影响行数为 0 表示已被并发审批/驳回，必须抛异常且不得写 assignment / 审计日志；</li>
 *   <li>审批时对目标工程师做完整校验（存在 + 启用 + engineer 角色），
 *       覆盖"申请创建后工程师被禁用"场景（含 PM 直接授权复用分支）；</li>
 *   <li>审批通过时按申请的 scope/expireAt 写入 assignment。</li>
 * </ul>
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("授权申请审批 CAS 语义测试")
class ProjectAuthServiceImplApproveTest {

    private static final Long REQUEST_ID = 500L;
    private static final Long PROJECT_ID = 100L;
    private static final Long PM_ID = 10L;
    private static final Long ENGINEER_ID = 20L;

    @Mock
    private BizProjectAuthRequestMapper authRequestMapper;
    @Mock
    private BizProjectAssignmentMapper assignmentMapper;
    @Mock
    private BizProjectMapper projectMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private BizProjectTransferLogMapper transferLogMapper;
    @Mock
    private IPermissionService permissionService;

    private ProjectAuthServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaUpdateWrapper 依赖实体表元数据（lambda cache），纯 Mockito 环境需手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                BizProjectAuthRequest.class);
    }

    @BeforeEach
    void setUp() {
        service = new ProjectAuthServiceImpl(authRequestMapper, assignmentMapper,
                projectMapper, userMapper, permissionService,
                new ProjectTransferLogHelper(userMapper, transferLogMapper));
        UserContext.setUserId(PM_ID);
        UserContext.setUsername("审批人张某");

        // 审批人角色（validateApproverRole 放行）
        when(permissionService.getCurrentUserRoles()).thenReturn(Set.of("project_manager"));

        // 待审批申请
        when(authRequestMapper.selectById(REQUEST_ID)).thenReturn(pendingRequest());

        // 项目
        BizProject project = new BizProject();
        project.setId(PROJECT_ID);
        project.setName("测试项目");
        project.setManagerId(PM_ID);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        // 目标工程师（默认启用 + engineer 角色）
        SysUser engineer = new SysUser();
        engineer.setId(ENGINEER_ID);
        engineer.setName("工程师小王");
        engineer.setStatus(1);
        when(userMapper.selectById(ENGINEER_ID)).thenReturn(engineer);
        when(userMapper.selectRoleCodesByUserId(ENGINEER_ID)).thenReturn(List.of("engineer"));

        // 默认无有效授权
        when(assignmentMapper.countActiveAssignment(PROJECT_ID, ENGINEER_ID)).thenReturn(0);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private BizProjectAuthRequest pendingRequest() {
        BizProjectAuthRequest request = new BizProjectAuthRequest();
        request.setId(REQUEST_ID);
        request.setProjectId(PROJECT_ID);
        request.setApplicantId(ENGINEER_ID);
        request.setTargetEngineerId(ENGINEER_ID);
        request.setScope("read_only");
        request.setStatus("pending");
        return request;
    }

    private AuthRequestApproveDTO approveDTO(Boolean approve) {
        AuthRequestApproveDTO dto = new AuthRequestApproveDTO();
        dto.setApprove(approve);
        dto.setComment("同意");
        return dto;
    }

    @Test
    @DisplayName("审批成功：CAS 生效 1 行，按申请 scope 写 assignment 并留审计")
    void approve_casWins_writesAssignmentWithRequestScope() {
        when(authRequestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        AuthRequestVO result = service.approveAuthRequest(REQUEST_ID, approveDTO(true));

        // assignment 按申请的 scope/expireAt 写入（而非默认值）
        ArgumentCaptor<BizProjectAssignment> captor = ArgumentCaptor.forClass(BizProjectAssignment.class);
        verify(assignmentMapper).insert(captor.capture());
        assertEquals(PROJECT_ID, captor.getValue().getProjectId());
        assertEquals(ENGINEER_ID, captor.getValue().getEngineerId());
        assertEquals("read_only", captor.getValue().getPermissionScope());
        assertEquals(1, captor.getValue().getIsActive());
        // 审计日志（approve 条目）写入
        verify(transferLogMapper).insert(any());
        assertEquals(REQUEST_ID, result.getId());
    }

    @Test
    @DisplayName("并发审批冲突：CAS 0 行抛异常，不写 assignment 与审计")
    void approve_casLoses_throwsAndSkipsSideEffects() {
        // 模拟另一审批者已先行完成状态流转
        when(authRequestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.approveAuthRequest(REQUEST_ID, approveDTO(true)));

        verify(assignmentMapper, never()).insert(any(BizProjectAssignment.class));
        verify(transferLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("工程师已禁用：审批拒绝，不执行状态流转")
    void approve_engineerDisabled_rejectedBeforeCas() {
        SysUser disabled = new SysUser();
        disabled.setId(ENGINEER_ID);
        disabled.setName("被禁用工程师");
        disabled.setStatus(0);
        when(userMapper.selectById(ENGINEER_ID)).thenReturn(disabled);

        assertThrows(BusinessException.class,
                () -> service.approveAuthRequest(REQUEST_ID, approveDTO(true)));

        verify(authRequestMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        verify(assignmentMapper, never()).insert(any(BizProjectAssignment.class));
    }

    @Test
    @DisplayName("已授权过：countActiveAssignment>0 拒绝，不执行 CAS")
    void approve_alreadyAuthorized_rejectedBeforeCas() {
        when(assignmentMapper.countActiveAssignment(PROJECT_ID, ENGINEER_ID)).thenReturn(1);

        assertThrows(BusinessException.class,
                () -> service.approveAuthRequest(REQUEST_ID, approveDTO(true)));

        verify(authRequestMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("驳回 CAS 冲突：0 行抛异常，不写审计")
    void reject_casLoses_throwsAndSkipsAudit() {
        when(authRequestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.approveAuthRequest(REQUEST_ID, approveDTO(false)));

        verify(transferLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("驳回成功：CAS 生效，不写 assignment")
    void reject_casWins_noAssignmentWritten() {
        when(authRequestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        AuthRequestVO result = service.approveAuthRequest(REQUEST_ID, approveDTO(false));

        verify(assignmentMapper, never()).insert(any(BizProjectAssignment.class));
        verify(transferLogMapper).insert(any());
        assertEquals(REQUEST_ID, result.getId());
    }
}
