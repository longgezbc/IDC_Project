package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.hbsz_idc_system_end.dto.ProjectSaveDTO;
import com.example.hbsz_idc_system_end.entity.BizCustomer;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.BizArchiveRecordMapper;
import com.example.hbsz_idc_system_end.mapper.BizCustomerMapper;
import com.example.hbsz_idc_system_end.mapper.BizDesignDrawingMapper;
import com.example.hbsz_idc_system_end.mapper.BizDocumentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectAssignmentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizTaskMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.IFileStorageService;
import com.example.hbsz_idc_system_end.service.INotificationService;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.service.IProjectAuthorizationService;
import com.example.hbsz_idc_system_end.service.IProjectTeamService;
import com.example.hbsz_idc_system_end.service.ProjectAccessGuard;
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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectServiceImpl} 项目经理用户自动关联（resolveManagerIdByName）测试。
 * <p>
 * 修复背景：biz_project.manager_name 为自由文本，前端未显式选择用户时 manager_id 为 NULL，
 * 导致进度/工作量审核通知、每日待办摘要、审批中心归属对该项目静默失效（配套 V20260822 回填迁移）。
 * 语义：
 * <ul>
 *   <li>DTO 未传 managerId 且姓名与启用用户唯一匹配 → 自动关联该用户 ID；</li>
 *   <li>DTO 显式传 managerId → 直接使用，不做姓名匹配；</li>
 *   <li>姓名重名（同名多用户）→ 不关联（避免错关联），保持 null；</li>
 *   <li>姓名无匹配/为空 → 保持 null（原有可空语义）。</li>
 * </ul>
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("项目-项目经理用户自动关联测试")
class ProjectServiceImplManagerLinkTest {

    private static final Long CURRENT_USER_ID = 500L;
    private static final Long MANAGER_USER_ID = 16L;

    @Mock
    private BizProjectMapper projectMapper;
    @Mock
    private BizCustomerMapper customerMapper;
    @Mock
    private BizDesignDrawingMapper drawingMapper;
    @Mock
    private BizArchiveRecordMapper archiveRecordMapper;
    @Mock
    private IPermissionService permissionService;
    @Mock
    private BizTaskMapper taskMapper;
    @Mock
    private BizDocumentMapper documentMapper;
    @Mock
    private INotificationService notificationService;
    @Mock
    private IProjectAuthorizationService projectAuthorizationService;
    @Mock
    private IProjectTeamService projectTeamService;
    @Mock
    private IFileStorageService fileStorageService;
    @Mock
    private BizProjectAssignmentMapper assignmentMapper;
    @Mock
    private SysUserMapper userMapper;

    private ProjectServiceImpl projectService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizProject.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUser.class);
    }

    @BeforeEach
    void setUp() {
        projectService = new ProjectServiceImpl(projectMapper, customerMapper, drawingMapper,
                archiveRecordMapper, permissionService, taskMapper, documentMapper,
                notificationService, projectAuthorizationService, projectTeamService, fileStorageService,
                new ProjectAccessGuard(projectMapper, assignmentMapper, permissionService), userMapper);
        UserContext.setUserId(CURRENT_USER_ID);
        UserContext.setUsername("测试用户");

        // create() 前置路径通用 stub：编号/名称唯一性通过 + 客户已存在 + 插入成功
        when(projectMapper.selectCount(any())).thenReturn(0L);
        BizCustomer customer = new BizCustomer();
        customer.setId(10L);
        when(customerMapper.selectOne(any())).thenReturn(customer);
        when(projectMapper.insert(any(BizProject.class))).thenReturn(1);
        // create() 末尾 getDetail() 的访问校验与详情查询
        when(projectAuthorizationService.canAccessProject(any())).thenReturn(true);
        when(projectMapper.selectProjectDetailById(any())).thenReturn(new com.example.hbsz_idc_system_end.dto.ProjectVO());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 构造最小创建 DTO（经理姓名文本，不传 managerId） */
    private ProjectSaveDTO createDTO(String managerName) {
        ProjectSaveDTO dto = new ProjectSaveDTO();
        dto.setName("自动关联回归项目-" + System.nanoTime());
        dto.setCode("AUTO-" + System.nanoTime());
        dto.setType("扩容");
        dto.setPriority("高");
        dto.setStatus("进行中");
        dto.setStartDate(java.time.LocalDate.of(2026, 8, 22));
        dto.setEndDate(java.time.LocalDate.of(2026, 9, 22));
        dto.setContractAmount(java.math.BigDecimal.TEN);
        dto.setCustomerName("回归客户");
        dto.setCustomerContact("王经理");
        dto.setCustomerPhone("13800000000");
        dto.setManager(managerName);
        return dto;
    }

    private SysUser activeUser(Long id, String name) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user" + id);
        user.setName(name);
        user.setStatus(1);
        return user;
    }

    @Test
    @DisplayName("未传managerId且姓名唯一匹配启用用户 → 自动关联该用户")
    void create_managerNameUniqueMatch_linksUserId() {
        when(userMapper.selectList(any())).thenReturn(List.of(activeUser(MANAGER_USER_ID, "王彦")));

        projectService.create(createDTO("王彦"));

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).insert(captor.capture());
        assertEquals(MANAGER_USER_ID, captor.getValue().getManagerId(), "唯一匹配时应自动关联用户ID");
    }

    @Test
    @DisplayName("显式传managerId → 直接使用不做姓名匹配")
    void create_explicitManagerId_usedDirectly() {
        ProjectSaveDTO dto = createDTO("王彦");
        dto.setManagerId(99L);

        projectService.create(dto);

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).insert(captor.capture());
        assertEquals(99L, captor.getValue().getManagerId(), "显式ID优先");
    }

    @Test
    @DisplayName("姓名重名 → 不关联保持null（防错关联）")
    void create_duplicateName_keepsNull() {
        when(userMapper.selectList(any())).thenReturn(List.of(
                activeUser(16L, "王彦"), activeUser(21L, "王彦")));

        projectService.create(createDTO("王彦"));

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).insert(captor.capture());
        assertNull(captor.getValue().getManagerId(), "重名时不得猜测关联");
    }

    @Test
    @DisplayName("姓名无匹配用户 → 保持null（原可空语义）")
    void create_noMatch_keepsNull() {
        when(userMapper.selectList(any())).thenReturn(Collections.emptyList());

        projectService.create(createDTO("不存在的经理"));

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).insert(captor.capture());
        assertNull(captor.getValue().getManagerId(), "无匹配时保持null");
    }

    /* ====== update 路径：姓名文本变更的重新关联（2026-08-24 行动项#3） ====== */

    /** 构造已存在的项目实体（update 路径）：id=24，managerId=16（王彦） */
    private BizProject existingProject() {
        BizProject project = new BizProject();
        project.setId(24L);
        project.setProjectCode("P-24");
        project.setName("存量项目");
        project.setStatus("进行中");
        project.setManagerId(MANAGER_USER_ID);
        project.setManagerName("王彦");
        return project;
    }

    /** 构造最小更新 DTO（仅经理姓名文本，不传 managerId） */
    private ProjectSaveDTO updateDTO(String managerName) {
        ProjectSaveDTO dto = new ProjectSaveDTO();
        dto.setManager(managerName);
        return dto;
    }

    @Test
    @DisplayName("update：姓名文本指名换人（唯一解析到不同用户）→ 委托transferLeader单点换帅")
    void update_managerTextChangedToUniqueUser_delegatesTransferLeader() {
        when(projectMapper.selectById(24L)).thenReturn(existingProject());
        when(projectMapper.updateById(any(BizProject.class))).thenReturn(1);
        when(userMapper.selectList(any())).thenReturn(List.of(activeUser(20L, "陈前龙")));

        projectService.update(24L, updateDTO("陈前龙"));

        // 走 transferLeader 单点（同步组长/授权/审计），目标为姓名解析出的用户
        verify(projectTeamService).transferLeader(24L, 20L);
        // updateById 不得直接改写 managerId（防双写路径分叉），由 transferLeader 统一落库
        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertEquals(MANAGER_USER_ID, captor.getValue().getManagerId(), "pmChanged时不得直接改写managerId");
    }

    @Test
    @DisplayName("update：姓名重名 → 不换帅不重链，仅更新姓名文本")
    void update_managerTextAmbiguous_keepsLink() {
        when(projectMapper.selectById(24L)).thenReturn(existingProject());
        when(projectMapper.updateById(any(BizProject.class))).thenReturn(1);
        when(userMapper.selectList(any())).thenReturn(List.of(
                activeUser(20L, "陈前龙"), activeUser(21L, "陈前龙")));

        projectService.update(24L, updateDTO("陈前龙"));

        verify(projectTeamService, never()).transferLeader(any(), any());
        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertEquals(MANAGER_USER_ID, captor.getValue().getManagerId(), "重名保持原关联");
        assertEquals("陈前龙", captor.getValue().getManagerName(), "姓名文本正常更新");
    }

    @Test
    @DisplayName("update：姓名文本未变 → 不做姓名解析，保持原关联")
    void update_managerTextUnchanged_noResolve() {
        when(projectMapper.selectById(24L)).thenReturn(existingProject());
        when(projectMapper.updateById(any(BizProject.class))).thenReturn(1);

        projectService.update(24L, updateDTO("王彦"));

        verify(userMapper, never()).selectList(any());
        verify(projectTeamService, never()).transferLeader(any(), any());
        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertEquals(MANAGER_USER_ID, captor.getValue().getManagerId());
    }

    @Test
    @DisplayName("update：姓名文本变化但解析到同一用户 → 不换帅，仅刷新姓名")
    void update_managerTextChangedSameUser_refreshNameOnly() {
        when(projectMapper.selectById(24L)).thenReturn(existingProject());
        when(projectMapper.updateById(any(BizProject.class))).thenReturn(1);
        when(userMapper.selectList(any())).thenReturn(List.of(activeUser(MANAGER_USER_ID, "王彦(组长)")));

        projectService.update(24L, updateDTO("王彦(组长)"));

        verify(projectTeamService, never()).transferLeader(any(), any());
        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertEquals(MANAGER_USER_ID, captor.getValue().getManagerId(), "同一用户不触发换帅");
        assertEquals("王彦(组长)", captor.getValue().getManagerName());
    }
}
