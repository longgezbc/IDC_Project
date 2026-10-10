package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectAuthServiceImpl#revokeByProjectAndEngineer} CAS 软删语义测试。
 * <p>
 * 撤销双入口收敛：本方法是 assignment 软删的单点写路径（revokeEngineer 委托至此，
 * 与 revokeAssignment 共享），核心语义：
 * <ul>
 *   <li>CAS 软删（UPDATE ... WHERE is_active=1），受影响行数为 0（无有效授权/并发已撤销）
 *       抛 DATA_NOT_FOUND，且不写审计日志；</li>
 *   <li>撤销场景不校验工程师启用状态（禁用后追回授权合法），仅校验存在；</li>
 *   <li>软删成功时写 revoke 审计日志。</li>
 * </ul>
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("按项目+工程师撤销授权 CAS 语义测试")
class ProjectAuthServiceImplRevokeTest {

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
                BizProjectAssignment.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                BizProjectAuthRequest.class);
    }

    @BeforeEach
    void setUp() {
        service = new ProjectAuthServiceImpl(authRequestMapper, assignmentMapper,
                projectMapper, userMapper, permissionService,
                new ProjectTransferLogHelper(userMapper, transferLogMapper));
        UserContext.setUserId(PM_ID);
        UserContext.setUsername("项目负责人");

        when(permissionService.getCurrentUserRoles()).thenReturn(Set.of("project_manager"));

        BizProject project = new BizProject();
        project.setId(PROJECT_ID);
        project.setName("测试项目");
        project.setManagerId(PM_ID);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        // 撤销场景：工程师存在即可，允许已禁用
        SysUser engineer = new SysUser();
        engineer.setId(ENGINEER_ID);
        engineer.setName("工程师小王");
        engineer.setStatus(1);
        when(userMapper.selectById(ENGINEER_ID)).thenReturn(engineer);
        when(userMapper.selectRoleCodesByUserId(ENGINEER_ID)).thenReturn(List.of("engineer"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("CAS 软删成功：写 revoke 审计日志")
    void revoke_casWins_writesAudit() {
        when(assignmentMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.revokeByProjectAndEngineer(PROJECT_ID, ENGINEER_ID, "人员调整"));

        verify(transferLogMapper).insert(any());
    }

    @Test
    @DisplayName("无有效授权（CAS 0 行）：抛 DATA_NOT_FOUND，不写审计")
    void revoke_casZero_throwsAndSkipsAudit() {
        when(assignmentMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.revokeByProjectAndEngineer(PROJECT_ID, ENGINEER_ID, "人员调整"));

        verify(transferLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("工程师不存在：抛 DATA_NOT_FOUND，不执行软删")
    void revoke_engineerNotFound_throwsBeforeSoftDelete() {
        when(userMapper.selectById(ENGINEER_ID)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.revokeByProjectAndEngineer(PROJECT_ID, ENGINEER_ID, "人员调整"));

        verify(assignmentMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        verify(transferLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("工程师已禁用：仍可撤销（禁用后追回授权是合法场景），不校验角色")
    void revoke_disabledEngineer_stillRevocable() {
        SysUser disabled = new SysUser();
        disabled.setId(ENGINEER_ID);
        disabled.setName("被禁用工程师");
        disabled.setStatus(0);
        when(userMapper.selectById(ENGINEER_ID)).thenReturn(disabled);
        when(assignmentMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.revokeByProjectAndEngineer(PROJECT_ID, ENGINEER_ID, null));

        verify(transferLogMapper).insert(any());
    }

    @Test
    @DisplayName("项目不存在：抛 PROJECT_NOT_FOUND，不执行软删")
    void revoke_projectNotFound_throws() {
        when(projectMapper.selectById(anyLong())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.revokeByProjectAndEngineer(PROJECT_ID, ENGINEER_ID, "人员调整"));

        verify(assignmentMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }
}
