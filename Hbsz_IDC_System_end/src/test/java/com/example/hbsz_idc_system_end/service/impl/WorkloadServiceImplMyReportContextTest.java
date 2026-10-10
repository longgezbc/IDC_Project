package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizProjectTeam;
import com.example.hbsz_idc_system_end.entity.BizWorkloadItem;
import com.example.hbsz_idc_system_end.entity.BizWorkloadRecord;
import com.example.hbsz_idc_system_end.mapper.BizExportRecordMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectAssignmentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectTeamMapper;
import com.example.hbsz_idc_system_end.mapper.BizWorkloadItemMapper;
import com.example.hbsz_idc_system_end.mapper.BizWorkloadRecordMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.INotificationService;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.service.IProjectAuthorizationService;
import com.example.hbsz_idc_system_end.service.ProjectAccessGuard;
import com.example.hbsz_idc_system_end.service.workload.WorkloadExcelExporter;
import com.example.hbsz_idc_system_end.service.workload.WorkloadItemManager;
import com.example.hbsz_idc_system_end.service.workload.WorkloadQuerySupport;
import com.example.hbsz_idc_system_end.service.workload.WorkloadReportManager;
import com.example.hbsz_idc_system_end.service.workload.WorkloadReviewManager;
import com.example.hbsz_idc_system_end.service.workload.WorkloadStatisticsManager;
import com.example.hbsz_idc_system_end.utils.UserContext;
import com.example.hbsz_idc_system_end.vo.WorkloadMyReportVO;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkloadServiceImpl#getMyReportContext(Long, LocalDate)} 特征测试。
 * <p>
 * 锁定"快速填报"上下文端点（GET /workload/my/report-context）的服务层契约：
 * <ul>
 *   <li>身份：依赖 UserContext；未登录抛 UNAUTHORIZED；myUserId/myName 原样回显；</li>
 *   <li>项目列表：仅活跃组员（isActive=1）+ 未归档项目；组长（leader）置前，
 *       同角色按项目名升序——前端"单项目自动选中"依赖该列表；</li>
 *   <li>projectId 为空：仅返回项目骨架（今日/昨日/常用项均为空、总项数 0），
 *       不触发记录查询（首屏轻量协议）；</li>
 *   <li>成员对称校验：与 saveRecords 一致——非项目组成员即使项目存在也拒绝
 *       （防止绕过前端下拉直接传 projectId 越权拉取他人上下文）；</li>
 *   <li>常用项：近 14 天记录按 itemId 聚合，fillCount=填报条数，
 *       lastQuantity/lastDate 取最新一天；排序 fillCount 降序 → lastDate 降序；
 *       已删除清单项跳过；</li>
 *   <li>今日/昨日记录：itemId → 单元格（数量/审核状态/意见），
 *       quantity>0 过滤由 SQL 完成，服务层不重复过滤（mock 直供即视为 SQL 结果）。</li>
 * </ul>
 * 技术说明：LambdaQueryWrapper 依赖 MyBatis-Plus TableInfo 缓存，
 * 纯 Mockito 环境需在 @BeforeAll 手动初始化三个实体的表元数据。
 * recordMapper.selectList 在一次调用中按"今日 → 昨日 → 近14天"顺序触发三次，
 * 测试用连续桩（thenReturn(a, b, c)）按调用序供数。
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("工作量快速填报上下文测试")
class WorkloadServiceImplMyReportContextTest {

    private static final Long PROJECT_ID = 300L;
    private static final Long ARCHIVED_PROJECT_ID = 400L;
    private static final Long USER_ID = 600L;
    private static final String USER_NAME = "施工员李四";
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 21);

    @Mock
    private BizWorkloadItemMapper itemMapper;
    @Mock
    private BizWorkloadRecordMapper recordMapper;
    @Mock
    private BizProjectMapper projectMapper;
    @Mock
    private BizExportRecordMapper exportRecordMapper;
    @Mock
    private BizProjectTeamMapper projectTeamMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private IProjectAuthorizationService projectAuthorizationService;
    @Mock
    private IPermissionService permissionService;
    @Mock
    private INotificationService notificationService;
    @Mock
    private BizProjectAssignmentMapper assignmentMapper;

    private WorkloadServiceImpl workloadService;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 解析实体列名依赖 TableInfo 缓存（无 Spring 上下文时需手动注册）
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizProjectTeam.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizWorkloadRecord.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizWorkloadItem.class);
    }

    @BeforeEach
    void setUp() {
        // 门面 + 真实协作类组装（mock mapper）：继续以黑盒方式覆盖 IWorkloadService 门面级契约
        WorkloadQuerySupport support = new WorkloadQuerySupport(itemMapper, recordMapper, projectMapper,
                projectTeamMapper, userMapper, projectAuthorizationService);
        WorkloadItemManager itemManager = new WorkloadItemManager(itemMapper, recordMapper, new ObjectMapper(), support);
        WorkloadReportManager reportManager = new WorkloadReportManager(itemMapper, recordMapper, projectMapper,
                projectTeamMapper, support, notificationService);
        WorkloadReviewManager reviewManager = new WorkloadReviewManager(recordMapper, support,
                new ProjectAccessGuard(projectMapper, assignmentMapper, permissionService), notificationService);
        WorkloadStatisticsManager statisticsManager = new WorkloadStatisticsManager(recordMapper, support);
        WorkloadExcelExporter excelExporter = new WorkloadExcelExporter(exportRecordMapper, statisticsManager, support);
        workloadService = new WorkloadServiceImpl(itemManager, reportManager, reviewManager,
                statisticsManager, excelExporter);
        UserContext.setUserId(USER_ID);
        UserContext.setUsername(USER_NAME);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ==================================================================
    // 构造测试数据
    // ==================================================================

    private BizProjectTeam team(Long projectId, String role, Integer isActive) {
        BizProjectTeam t = new BizProjectTeam();
        t.setProjectId(projectId);
        t.setUserId(USER_ID);
        t.setRoleInTeam(role);
        t.setIsActive(isActive);
        return t;
    }

    private BizProject project(Long id, String name, Integer isArchived) {
        BizProject p = new BizProject();
        p.setId(id);
        p.setName(name);
        p.setIsArchived(isArchived);
        return p;
    }

    private BizWorkloadItem item(Long id, String category, String scope, String unit) {
        BizWorkloadItem it = new BizWorkloadItem();
        it.setId(id);
        it.setProjectId(PROJECT_ID);
        it.setCategory(category);
        it.setWorkScope(scope);
        it.setUnit(unit);
        it.setRatedWorkload(new BigDecimal("100"));
        return it;
    }

    private BizWorkloadRecord record(Long itemId, LocalDate date, String qty, String reviewStatus) {
        BizWorkloadRecord r = new BizWorkloadRecord();
        r.setProjectId(PROJECT_ID);
        r.setItemId(itemId);
        r.setRecordDate(date);
        r.setWorkerUserId(USER_ID);
        r.setQuantity(new BigDecimal(qty));
        r.setReviewStatus(reviewStatus);
        return r;
    }

    // ==================================================================
    // 项目列表协议：活跃成员 + 未归档 + leader 置前 + 名称排序
    // ==================================================================

    @Test
    @DisplayName("projectId为空：仅返回项目骨架（今日/昨日/常用项为空、总项数0），不查记录表")
    void myReportContext_withoutProjectId_returnsProjectListOnly() {
        // 组员身份：项目A(member) + 项目B(leader) + 已归档项目C(leader) + 已退出项目D
        when(projectTeamMapper.selectList(any())).thenReturn(Arrays.asList(
                team(PROJECT_ID, "member", 1),
                team(200L, "leader", 1),
                team(ARCHIVED_PROJECT_ID, "leader", 1),
                team(500L, "member", 0)));
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(
                project(PROJECT_ID, "综布仓一期", 0),
                project(200L, "数据机房二期", 0),
                project(ARCHIVED_PROJECT_ID, "旧机房改造", 1)));

        WorkloadMyReportVO vo = workloadService.getMyReportContext(null, REPORT_DATE);

        assertEquals(USER_ID, vo.getMyUserId());
        assertEquals(USER_NAME, vo.getMyName());
        assertEquals(REPORT_DATE, vo.getReportDate());
        // 已归档项目被过滤；已退出组（isActive=0）不在查询结果中
        assertEquals(2, vo.getProjects().size(), "归档项目与退出组不应出现在可填报列表");
        // leader 置前：数据机房二期(leader) 在 综布仓一期(member) 之前
        assertEquals(200L, vo.getProjects().get(0).getProjectId());
        assertEquals("leader", vo.getProjects().get(0).getRoleInTeam());
        assertEquals(PROJECT_ID, vo.getProjects().get(1).getProjectId());
        assertEquals("member", vo.getProjects().get(1).getRoleInTeam());
        // 骨架空值：前端首屏协议依赖
        assertTrue(vo.getTodayRecords().isEmpty(), "未指定项目时今日记录应为空");
        assertTrue(vo.getYesterdayRecords().isEmpty(), "未指定项目时昨日记录应为空");
        assertTrue(vo.getFrequentItems().isEmpty(), "未指定项目时常用项应为空");
        assertEquals(0, vo.getTotalItemCount(), "未指定项目时总项数应为 0");
        verify(recordMapper, never()).selectList(any());
        verify(itemMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("同角色多项目按项目名升序：保证下拉与单项目自动选中顺序稳定")
    void myReportContext_memberOfMultipleProjects_sortsByName() {
        when(projectTeamMapper.selectList(any())).thenReturn(Arrays.asList(
                team(300L, "member", 1),
                team(200L, "member", 1),
                team(100L, "member", 1)));
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(
                project(300L, "C项目", 0),
                project(200L, "A项目", 0),
                project(100L, "B项目", 0)));

        WorkloadMyReportVO vo = workloadService.getMyReportContext(null, REPORT_DATE);

        assertEquals(List.of("A项目", "B项目", "C项目"),
                vo.getProjects().stream().map(WorkloadMyReportVO.ProjectBrief::getProjectName).toList(),
                "同角色（member）应按项目名升序");
    }

    // ==================================================================
    // 权限对称校验
    // ==================================================================

    @Test
    @DisplayName("非项目组成员：抛参数校验异常（与 saveRecords 成员校验对称，防越权）")
    void myReportContext_notTeamMember_throws() {
        // 用户只属于 200L 项目组，却请求 300L 项目的上下文
        when(projectTeamMapper.selectList(any())).thenReturn(
                Collections.singletonList(team(200L, "member", 1)));
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(
                Collections.singletonList(project(200L, "A项目", 0)));
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "综布仓一期", 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workloadService.getMyReportContext(PROJECT_ID, REPORT_DATE));

        assertEquals("您不是该项目组成员，无法填报；请先在项目组管理中添加您为成员", ex.getMessage());
        verify(recordMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("项目不存在：先于成员校验抛出项目不存在")
    void myReportContext_projectMissing_throwsProjectNotFound() {
        when(projectTeamMapper.selectList(any())).thenReturn(
                Collections.singletonList(team(PROJECT_ID, "member", 1)));
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(
                Collections.singletonList(project(PROJECT_ID, "综布仓一期", 0)));
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workloadService.getMyReportContext(PROJECT_ID, REPORT_DATE));

        assertEquals("项目不存在", ex.getMessage());
    }

    @Test
    @DisplayName("已归档项目被过滤后，再传其projectId同样按非成员拒绝")
    void myReportContext_archivedProject_rejectedAsNonMember() {
        // 活跃组员身份在归档项目上，但列表已过滤归档项目 → 以非成员拒绝
        when(projectTeamMapper.selectList(any())).thenReturn(
                Collections.singletonList(team(ARCHIVED_PROJECT_ID, "member", 1)));
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(
                Collections.singletonList(project(ARCHIVED_PROJECT_ID, "旧机房改造", 1)));
        when(projectMapper.selectById(ARCHIVED_PROJECT_ID)).thenReturn(
                project(ARCHIVED_PROJECT_ID, "旧机房改造", 1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workloadService.getMyReportContext(ARCHIVED_PROJECT_ID, REPORT_DATE));

        assertEquals("您不是该项目组成员，无法填报；请先在项目组管理中添加您为成员", ex.getMessage());
    }

    // ==================================================================
    // 完整上下文：今日回显 / 昨日复制源 / 常用项聚合排序 / 总项数
    // ==================================================================

    @Test
    @DisplayName("完整上下文：今日单元格回显 + 昨日数量 + 常用项频次聚合（fillCount/lastQuantity/lastDate）")
    void myReportContext_fullContext_assemblesTodayYesterdayAndFrequentItems() {
        stubTeamAndProject();
        LocalDate yesterday = REPORT_DATE.minusDays(1);
        LocalDate dayBefore = REPORT_DATE.minusDays(2);

        // selectList 依次触发：今日 → 昨日 → 近14天
        // 今日：项11 已通过(5m)、项12 待审核(3m)
        // 昨日：项11(5m)、项13(8m)
        // 近14天：项11 三天连填、项12 一次、项13 两次 → fillCount: 项11=3 > 项13=2 > 项12=1
        when(recordMapper.selectList(any())).thenReturn(
                Arrays.asList(
                        record(11L, REPORT_DATE, "5", "approved"),
                        record(12L, REPORT_DATE, "3", "pending")),
                Arrays.asList(
                        record(11L, yesterday, "5", "approved"),
                        record(13L, yesterday, "8", "approved")),
                Arrays.asList(
                        record(11L, REPORT_DATE, "5", "approved"),
                        record(11L, yesterday, "5", "approved"),
                        record(11L, dayBefore, "4", "approved"),
                        record(12L, REPORT_DATE, "3", "pending"),
                        record(13L, yesterday, "8", "approved"),
                        record(13L, dayBefore, "7", "approved")));
        when(itemMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(
                item(11L, "桥架安装", "机房A区桥架敷设", "米"),
                item(12L, "光缆熔接", "机房B区光缆熔接", "芯"),
                item(13L, "机柜安装", "机房A区机柜上架", "台")));
        when(itemMapper.selectCount(any())).thenReturn(106L);

        WorkloadMyReportVO vo = workloadService.getMyReportContext(PROJECT_ID, REPORT_DATE);

        // —— 基础回显 ——
        assertEquals(PROJECT_ID, vo.getProjectId());
        assertEquals("综布仓一期", vo.getProjectName());

        // —— 今日单元格：itemId → {quantity, reviewStatus} ——
        assertEquals(2, vo.getTodayRecords().size());
        assertEquals(new BigDecimal("5"), vo.getTodayRecords().get(11L).getQuantity());
        assertEquals("approved", vo.getTodayRecords().get(11L).getReviewStatus());
        assertEquals("pending", vo.getTodayRecords().get(12L).getReviewStatus());

        // —— 昨日数量（复制昨天数据源）——
        assertEquals(2, vo.getYesterdayRecords().size());
        assertEquals(new BigDecimal("5"), vo.getYesterdayRecords().get(11L));
        assertEquals(new BigDecimal("8"), vo.getYesterdayRecords().get(13L));

        // —— 常用项排序：fillCount 降序（项11=3 → 项13=2 → 项12=1）——
        List<WorkloadMyReportVO.FrequentItem> frequent = vo.getFrequentItems();
        assertEquals(3, frequent.size());
        assertEquals(List.of(11L, 13L, 12L),
                frequent.stream().map(WorkloadMyReportVO.FrequentItem::getItemId).toList(),
                "常用项应按填报频次降序排列");

        // —— 项11 聚合：fillCount=3，最新一天（今天）数量 5 ——
        WorkloadMyReportVO.FrequentItem fi11 = frequent.get(0);
        assertEquals(3, fi11.getFillCount(), "fillCount 应为近14天填报条数");
        assertEquals(new BigDecimal("5"), fi11.getLastQuantity(), "lastQuantity 应取最新一条数量");
        assertEquals(REPORT_DATE, fi11.getLastDate());
        assertEquals("桥架安装", fi11.getCategory());
        assertEquals("米", fi11.getUnit());
        assertEquals(new BigDecimal("100"), fi11.getRatedWorkload());

        // —— 项13：最新一天为昨天 8 ——
        WorkloadMyReportVO.FrequentItem fi13 = frequent.get(1);
        assertEquals(2, fi13.getFillCount());
        assertEquals(new BigDecimal("8"), fi13.getLastQuantity());
        assertEquals(yesterday, fi13.getLastDate());

        // —— 总项数 ——
        assertEquals(106, vo.getTotalItemCount(), "总项数来自清单计数（前端提示可搜索更多）");
    }

    @Test
    @DisplayName("常用项关联清单已删除：跳过该item，不产出孤儿卡片")
    void myReportContext_itemDeleted_skipsOrphanFrequentItem() {
        stubTeamAndProject();
        // 近14天只有项11，但清单表查不到（已删除）
        when(recordMapper.selectList(any())).thenReturn(
                Collections.<BizWorkloadRecord>emptyList(),
                Collections.<BizWorkloadRecord>emptyList(),
                Collections.singletonList(record(11L, REPORT_DATE, "5", "approved")));
        when(itemMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
        when(itemMapper.selectCount(any())).thenReturn(0L);

        WorkloadMyReportVO vo = workloadService.getMyReportContext(PROJECT_ID, REPORT_DATE);

        assertTrue(vo.getFrequentItems().isEmpty(), "清单项已删除时应跳过，不返回半空常用项");
    }

    @Test
    @DisplayName("date为null：默认今天作为填报日期")
    void myReportContext_nullDate_defaultsToToday() {
        stubTeamAndProject();
        LocalDate today = LocalDate.now();
        when(recordMapper.selectList(any())).thenReturn(
                Collections.<BizWorkloadRecord>emptyList(),
                Collections.<BizWorkloadRecord>emptyList(),
                Collections.<BizWorkloadRecord>emptyList());
        when(itemMapper.selectCount(any())).thenReturn(3L);

        WorkloadMyReportVO vo = workloadService.getMyReportContext(PROJECT_ID, null);

        assertEquals(today, vo.getReportDate(), "未传日期时应默认今天");
        assertEquals(3, vo.getTotalItemCount());
    }

    // ==================================================================
    // 身份
    // ==================================================================

    @Test
    @DisplayName("未登录：抛 UNAUTHORIZED，不触发任何查询")
    void myReportContext_notLoggedIn_throwsUnauthorized() {
        UserContext.clear();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workloadService.getMyReportContext(PROJECT_ID, REPORT_DATE));

        assertEquals("未登录或认证已过期", ex.getMessage());
        verify(projectTeamMapper, never()).selectList(any());
        verify(recordMapper, never()).selectList(any());
    }

    // ==================================================================
    // 公共桩
    // ==================================================================

    /** 用户是 PROJECT_ID 的活跃成员，项目存在且未归档（正路径最小前置） */
    private void stubTeamAndProject() {
        when(projectTeamMapper.selectList(any())).thenReturn(
                Collections.singletonList(team(PROJECT_ID, "member", 1)));
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(
                Collections.singletonList(project(PROJECT_ID, "综布仓一期", 0)));
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "综布仓一期", 0));
    }
}
