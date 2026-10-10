package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
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
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectAuthServiceImpl#grantDirectAuthorization} 语义测试。
 * <p>
 * assignment 写路径收敛的核心原语（代提申请 + 自动审批），语义：
 * <ul>
 *   <li>幂等：已存在有效授权（countActiveAssignment>0）→ 跳过返回 null，不创建申请；</li>
 *   <li>代提：无待审批申请时代提新申请，scope 不显式设置（由 DEFAULT_SCOPE 兜底）、reason 透传；</li>
 *   <li>复用：已有待审批申请时复用，不重复创建（申请 reason 保留工程师原内容）；</li>
 *   <li>自动审批意见保留操作者意图：comment = "直接授权（自动审批）：" + reason；</li>
 *   <li>审批通过后 assignment 由 approveAuthRequest 统一写入（scope/expireAt 按申请透传）。</li>
 * </ul>
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("直接授权（代提+自动审批）语义测试")
class ProjectAuthServiceImplGrantTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long PM_ID = 10L;
    private static final Long ENGINEER_ID = 20L;
    private static final Long NEW_REQUEST_ID = 500L;
    private static final Long EXISTING_REQUEST_ID = 900L;

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
        // LambdaWrapper 依赖实体表元数据（lambda cache），纯 Mockito 环境需手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                BizProjectAuthRequest.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                BizProjectAssignment.class);
    }

    @BeforeEach
    void setUp() {
        service = new ProjectAuthServiceImpl(authRequestMapper, assignmentMapper,
                projectMapper, userMapper, permissionService,
                new ProjectTransferLogHelper(userMapper, transferLogMapper));
        UserContext.setUserId(PM_ID);
        UserContext.setUsername("项目负责人");

        // 角色：PM（validateCanApplyForProject / validateApproverRole 放行）
        when(permissionService.getCurrentUserRoles()).thenReturn(Set.of("project_manager"));

        // 项目
        BizProject project = new BizProject();
        project.setId(PROJECT_ID);
        project.setName("测试项目");
        project.setManagerId(PM_ID);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        // 操作人（getUserName）
        SysUser pm = new SysUser();
        pm.setId(PM_ID);
        pm.setName("项目负责人");
        when(userMapper.selectById(PM_ID)).thenReturn(pm);

        // 目标工程师（validateEngineer 放行：存在 + 启用 + engineer 角色）
        SysUser engineer = new SysUser();
        engineer.setId(ENGINEER_ID);
        engineer.setName("工程师小王");
        engineer.setStatus(1);
        when(userMapper.selectById(ENGINEER_ID)).thenReturn(engineer);
        when(userMapper.selectRoleCodesByUserId(ENGINEER_ID)).thenReturn(List.of("engineer"));

        // 默认无有效授权、无待审批申请
        when(assignmentMapper.countActiveAssignment(PROJECT_ID, ENGINEER_ID)).thenReturn(0);
        when(authRequestMapper.selectOne(any())).thenReturn(null);
        // createAuthRequest 内 pending 幂等校验
        when(authRequestMapper.selectCount(any())).thenReturn(0L);
        // insert 申请后回填自增 ID（模拟 MyBatis-Plus @TableId AUTO 行为）
        when(authRequestMapper.insert(any(BizProjectAuthRequest.class))).thenAnswer(inv -> {
            BizProjectAuthRequest r = inv.getArgument(0);
            r.setId(NEW_REQUEST_ID);
            return 1;
        });
        // 审批环节：待审批申请存在
        when(authRequestMapper.selectById(NEW_REQUEST_ID)).thenReturn(pendingRequest(NEW_REQUEST_ID));
        when(authRequestMapper.selectById(EXISTING_REQUEST_ID)).thenReturn(pendingRequest(EXISTING_REQUEST_ID));
        // 审批 CAS 成功
        when(authRequestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private BizProjectAuthRequest pendingRequest(Long id) {
        BizProjectAuthRequest request = new BizProjectAuthRequest();
        request.setId(id);
        request.setProjectId(PROJECT_ID);
        request.setApplicantId(ENGINEER_ID);
        request.setTargetEngineerId(ENGINEER_ID);
        request.setScope("read_only");
        request.setStatus("pending");
        return request;
    }

    @Test
    @DisplayName("幂等跳过：已存在有效授权，不创建申请、不写 assignment")
    void grant_alreadyAuthorized_skipsSilently() {
        when(assignmentMapper.countActiveAssignment(PROJECT_ID, ENGINEER_ID)).thenReturn(1);

        AuthRequestVO result = service.grantDirectAuthorization(PROJECT_ID, ENGINEER_ID, "现场施工需要");

        assertNull(result);
        verify(authRequestMapper, never()).insert(any(BizProjectAuthRequest.class));
        verify(assignmentMapper, never()).insert(any(BizProjectAssignment.class));
    }

    @Test
    @DisplayName("代提新申请：scope 不显式设置（DEFAULT_SCOPE 兜底）、reason 透传、自动审批")
    void grant_createsAndAutoApprovesNewRequest() {
        AuthRequestVO result = service.grantDirectAuthorization(PROJECT_ID, ENGINEER_ID, "现场施工需要");

        // 代提申请字段：reason 透传；scope 由 createAuthRequest 的 DEFAULT_SCOPE 兜底为 read_write
        // （默认授权范围单点定义在 ProjectAuthServiceImpl，grant 不自行设置）
        ArgumentCaptor<BizProjectAuthRequest> requestCaptor = ArgumentCaptor.forClass(BizProjectAuthRequest.class);
        verify(authRequestMapper).insert(requestCaptor.capture());
        BizProjectAuthRequest created = requestCaptor.getValue();
        assertEquals(PROJECT_ID, created.getProjectId());
        assertEquals(ENGINEER_ID, created.getTargetEngineerId());
        assertEquals("现场施工需要", created.getReason());
        assertEquals("read_write", created.getScope());

        // CAS 审批意见保留操作者意图
        verify(authRequestMapper).update(any(), any(LambdaUpdateWrapper.class));

        // assignment 由审批环节统一写入
        verify(assignmentMapper).insert(any(BizProjectAssignment.class));
        assertNotNull(result);
        assertEquals(NEW_REQUEST_ID, result.getId());
    }

    @Test
    @DisplayName("复用已有待审批申请：不重复创建，申请 reason 保留原内容（不覆盖为 PM 理由）")
    void grant_reusesExistingPendingRequest() {
        BizProjectAuthRequest existing = pendingRequest(EXISTING_REQUEST_ID);
        existing.setReason("工程师自行申请的理由");
        when(authRequestMapper.selectOne(any())).thenReturn(existing);

        service.grantDirectAuthorization(PROJECT_ID, ENGINEER_ID, "PM 的授权理由");

        // 不重复创建申请
        verify(authRequestMapper, never()).insert(any(BizProjectAuthRequest.class));
        // 对已有申请执行 CAS 审批（EXISTING_REQUEST_ID）
        verify(authRequestMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("转移/PM 入口共用：remark 为空时审批意见退化为纯自动审批标识")
    void grant_blankReasonStillApproves() {
        AuthRequestVO result = service.grantDirectAuthorization(PROJECT_ID, ENGINEER_ID, null);

        verify(assignmentMapper).insert(any(BizProjectAssignment.class));
        assertNotNull(result);
    }

    @Test
    @DisplayName("并发兜底：代提申请撞 pending 唯一索引（uk_auth_request_pending）转 DATA_ALREADY_EXISTS，不进入审批")
    void grant_duplicateKeyOnInsert_throwsBusinessException() {
        // selectCount 前置检查放行（并发竞态场景），insert 撞唯一索引
        when(authRequestMapper.insert(any(BizProjectAuthRequest.class)))
                .thenThrow(new DuplicateKeyException("uk_auth_request_pending"));

        assertThrows(BusinessException.class,
                () -> service.grantDirectAuthorization(PROJECT_ID, ENGINEER_ID, "现场施工需要"));

        // 申请未落库，不进入审批链路、不写 assignment
        verify(authRequestMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        verify(assignmentMapper, never()).insert(any(BizProjectAssignment.class));
    }
}
