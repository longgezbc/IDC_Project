package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.dto.TeamMemberAddDTO;
import com.example.hbsz_idc_system_end.dto.TeamMemberVO;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizProjectTeam;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.BizProjectAssignmentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectTeamMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectTransferLogMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.service.IProjectAuthService;
import com.example.hbsz_idc_system_end.service.ProjectAccessGuard;
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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectTeamServiceImpl} 授权写路径委托测试（debt-3）。
 * <p>
 * 项目组的 assignment 写操作统一委托 {@link IProjectAuthService}（单点收敛）：
 * <ul>
 *   <li>addMember：创建授权委托 createDirectAssignment（成员可为非工程师，
 *       不走申请-审批链路），查重由单点内部处理；</li>
 *   <li>removeMember：撤销授权委托 softDeleteActiveAssignments（容错，0 行不阻断移除）；</li>
 *   <li>transferLeader：新组长自动入组后授权委托 createDirectAssignment；</li>
 *   <li>业务审计（team_add/team_remove/transfer_leader）由本服务自行记录，授权单点不重复写审计。</li>
 * </ul>
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("项目组-授权写路径委托测试")
class ProjectTeamServiceImplDelegationTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long PM_ID = 10L;
    private static final Long MEMBER_ID = 20L;
    private static final Long TEAM_ID = 77L;

    @Mock
    private BizProjectTeamMapper teamMapper;
    @Mock
    private BizProjectMapper projectMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private IProjectAuthService projectAuthService;
    @Mock
    private BizProjectTransferLogMapper transferLogMapper;
    @Mock
    private BizProjectAssignmentMapper assignmentMapper;
    @Mock
    private IPermissionService permissionService;

    private ProjectTeamServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaUpdateWrapper.set() 立即解析列名（removeMember/transferLeader 路径），
        // 依赖 MyBatis-Plus TableInfo 缓存，纯 Mockito 环境需手动初始化表元数据
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                BizProjectTeam.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                BizProject.class);
    }

    @BeforeEach
    void setUp() {
        // 所有权/访问校验委托共享 ProjectAccessGuard——测试以真实 guard + mock 依赖穿透校验逻辑
        service = new ProjectTeamServiceImpl(teamMapper, projectMapper, userMapper,
                projectAuthService, new ProjectTransferLogHelper(userMapper, transferLogMapper),
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

        SysUser member = new SysUser();
        member.setId(MEMBER_ID);
        member.setName("组员小王");
        member.setStatus(1);
        when(userMapper.selectById(MEMBER_ID)).thenReturn(member);

        // 默认无重复成员
        when(teamMapper.selectCount(any())).thenReturn(0L);
        // 团队记录 insert 回填自增 ID
        when(teamMapper.insert(any(BizProjectTeam.class))).thenAnswer(inv -> {
            BizProjectTeam t = inv.getArgument(0);
            t.setId(TEAM_ID);
            return 1;
        });
        // addMember 返回值（按 id 精确查询）
        TeamMemberVO vo = new TeamMemberVO();
        vo.setId(TEAM_ID);
        when(teamMapper.selectTeamMemberById(TEAM_ID)).thenReturn(vo);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private TeamMemberAddDTO addDTO() {
        TeamMemberAddDTO dto = new TeamMemberAddDTO();
        dto.setProjectId(PROJECT_ID);
        dto.setUserId(MEMBER_ID);
        dto.setRoleInTeam("member");
        dto.setRemark("新成员");
        return dto;
    }

    @Test
    @DisplayName("添加成员：授权委托 createDirectAssignment（read_write + '项目组加入自动授权'）")
    void addMember_delegatesAssignmentCreation() {
        TeamMemberVO result = service.addMember(addDTO());

        verify(projectAuthService).createDirectAssignment(PROJECT_ID, MEMBER_ID, PM_ID,
                "read_write", "项目组加入自动授权");
        // 业务审计由本服务记录（team_add）
        verify(transferLogMapper).insert(any());
        assertEquals(TEAM_ID, result.getId());
    }

    @Test
    @DisplayName("移除成员：授权撤销委托 softDeleteActiveAssignments，reason 带移除原因")
    void removeMember_delegatesAssignmentSoftDelete() {
        BizProjectTeam team = new BizProjectTeam();
        team.setId(TEAM_ID);
        team.setProjectId(PROJECT_ID);
        team.setUserId(MEMBER_ID);
        team.setIsActive(1);
        when(teamMapper.selectById(TEAM_ID)).thenReturn(team);

        service.removeMember(TEAM_ID, "长期未参与");

        verify(projectAuthService).softDeleteActiveAssignments(PROJECT_ID, MEMBER_ID, "项目组移除：长期未参与");
        // 团队记录软删 + 业务审计（team_remove）由本服务完成
        verify(teamMapper).update(any(), any());
        verify(transferLogMapper).insert(any());
    }

    @Test
    @DisplayName("移除成员（无授权记录）：软删 0 行容错，不阻断移除流程")
    void removeMember_toleratesMissingAssignment() {
        BizProjectTeam team = new BizProjectTeam();
        team.setId(TEAM_ID);
        team.setProjectId(PROJECT_ID);
        team.setUserId(MEMBER_ID);
        team.setIsActive(1);
        when(teamMapper.selectById(TEAM_ID)).thenReturn(team);
        // 模拟成员无有效授权（如已被 PM 单独撤销）——委托返回 0 行
        when(projectAuthService.softDeleteActiveAssignments(PROJECT_ID, MEMBER_ID, "项目组移除")).thenReturn(0);

        assertDoesNotThrow(() -> service.removeMember(TEAM_ID, null));
        verify(teamMapper).update(any(), any());
    }

    @Test
    @DisplayName("转让组长（新组长不在组）：入组后授权委托 createDirectAssignment")
    void transferLeader_delegatesAssignmentCreation() {
        // 无现任 leader、新组长不在组（selectOne 均返回 null）
        when(teamMapper.selectOne(any())).thenReturn(null);

        service.transferLeader(PROJECT_ID, MEMBER_ID);

        verify(projectAuthService).createDirectAssignment(PROJECT_ID, MEMBER_ID, PM_ID,
                "read_write", "转让组长自动授权");
        // 新组长入组 + 项目负责人更新 + 业务审计（transfer_leader）
        verify(teamMapper).insert(any(BizProjectTeam.class));
        verify(projectMapper).update(any(), any());
        verify(transferLogMapper).insert(any());
    }

    @Test
    @DisplayName("越权拒绝：非 admin 且非项目负责人不进入授权委托链路")
    void addMember_rejectsNonOwner() {
        when(permissionService.getCurrentUserRoles()).thenReturn(Set.of("engineer"));
        BizProject othersProject = new BizProject();
        othersProject.setId(PROJECT_ID);
        othersProject.setName("他人负责的项目");
        othersProject.setManagerId(999L);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(othersProject);

        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.addMember(addDTO()));

        verify(projectAuthService, never()).createDirectAssignment(any(), any(), any(), any(), any());
    }
}
