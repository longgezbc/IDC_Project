package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.dto.WorkloadReviewDTO;
import com.example.hbsz_idc_system_end.entity.BizProject;
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
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkloadServiceImpl#reviewRecords(WorkloadReviewDTO)} 特征测试（Characterization Tests）。
 * <p>
 * 目的：锁定当前 CAS 批量审核行为，防止后续重构（如权限收口、拆分 Service）无意改变契约：
 * <ul>
 *   <li>审核语义为"尽力而为"：CAS 更新（WHERE review_status='pending'）返回实际成功行数，
 *       并发/重复审核返回 0 行而不抛异常——与单条进度审核（抛异常）语义不同，此差异必须保留；</li>
 *   <li>SET 子句必须写入 review_status/reviewer_id/reviewer_name/review_time，
 *       review_remark 仅在意见非空时写入；</li>
 *   <li>前置校验顺序：记录存在 → 同属一项目 → 项目访问权限（IDOR） → 审核归属（admin 或项目负责人）。</li>
 * </ul>
 * 技术说明：LambdaUpdateWrapper 构建依赖 MyBatis-Plus TableInfo 缓存，
 * 纯 Mockito 环境需在 @BeforeAll 手动初始化 BizWorkloadRecord 表元数据。
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("工作量批量审核 CAS 特征测试")
class WorkloadServiceImplReviewRecordsTest {

    private static final Long PROJECT_ID = 300L;
    private static final Long REVIEWER_ID = 500L;
    private static final String REVIEWER_NAME = "审核人张三";

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
        // 纯单测环境下 MyBatis-Plus 不会通过 Mapper 注册初始化表元数据，
        // LambdaUpdateWrapper 解析 BizWorkloadRecord::getXxx 列名时依赖该缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizWorkloadRecord.class);
    }

    @BeforeEach
    void setUp() {
        // 门面 + 真实协作类组装（mock mapper）：继续以黑盒方式覆盖 IWorkloadService 门面级契约
        WorkloadQuerySupport support = new WorkloadQuerySupport(itemMapper, recordMapper, projectMapper,
                projectTeamMapper, userMapper, projectAuthorizationService);
        WorkloadItemManager itemManager = new WorkloadItemManager(itemMapper, recordMapper, new ObjectMapper(), support);
        WorkloadReportManager reportManager = new WorkloadReportManager(itemMapper, recordMapper, projectMapper,
                projectTeamMapper, support, notificationService);
        // 归属校验委托共享 ProjectAccessGuard 单点——真实 guard + mock 依赖穿透越权校验逻辑
        WorkloadReviewManager reviewManager = new WorkloadReviewManager(recordMapper, support,
                new ProjectAccessGuard(projectMapper, assignmentMapper, permissionService), notificationService);
        WorkloadStatisticsManager statisticsManager = new WorkloadStatisticsManager(recordMapper, support);
        WorkloadExcelExporter excelExporter = new WorkloadExcelExporter(exportRecordMapper, statisticsManager, support);
        workloadService = new WorkloadServiceImpl(itemManager, reportManager, reviewManager,
                statisticsManager, excelExporter);

        UserContext.setUserId(REVIEWER_ID);
        UserContext.setUsername(REVIEWER_NAME);

        // 默认桩：admin 角色放行（各测试按需覆盖为 PM/无权限场景）
        Set<String> adminRoles = new HashSet<>(Collections.singletonList(CommonConstant.ROLE_CODE_ADMIN));
        when(permissionService.getCurrentUserRoles()).thenReturn(adminRoles);
        when(projectAuthorizationService.canAccessProject(PROJECT_ID)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private WorkloadReviewDTO reviewDto(List<Long> ids, Boolean approve, String remark) {
        WorkloadReviewDTO dto = new WorkloadReviewDTO();
        dto.setIds(ids);
        dto.setApprove(approve);
        dto.setRemark(remark);
        return dto;
    }

    private BizWorkloadRecord record(Long id) {
        BizWorkloadRecord r = new BizWorkloadRecord();
        r.setId(id);
        r.setProjectId(PROJECT_ID);
        return r;
    }

    /** 捕获 CAS 更新 wrapper，便于断言 SET 子句与参数 */
    private LambdaUpdateWrapper<BizWorkloadRecord> captureUpdateWrapper() {
        ArgumentCaptor<LambdaUpdateWrapper<BizWorkloadRecord>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }

    // ==================================================================
    // 正常路径：CAS 更新语义
    // ==================================================================

    @Test
    @DisplayName("管理员批量通过：SET 子句写入 approved + 审核人四要素，返回实际成功行数")
    void reviewRecords_approveByAdmin_writesApprovedStatusAndReviewerInfo() {
        List<Long> ids = Arrays.asList(1L, 2L, 3L);
        when(recordMapper.selectBatchIds(ids)).thenReturn(Arrays.asList(record(1L), record(2L), record(3L)));
        when(recordMapper.update(isNull(), any())).thenReturn(3);

        int rows = workloadService.reviewRecords(reviewDto(ids, true, null));

        assertEquals(3, rows, "应返回 CAS 实际更新行数");
        LambdaUpdateWrapper<BizWorkloadRecord> wrapper = captureUpdateWrapper();
        // 先渲染 WHERE 段：eq() 条件参数为惰性注册，getSqlSegment() 才会写入 paramNameValuePairs
        String whereSegment = wrapper.getSqlSegment();
        assertTrue(whereSegment.contains("review_status"), "WHERE 段必须以 review_status 作为 CAS 条件列");
        String sqlSet = wrapper.getSqlSet();
        assertAll("SET 子句必须包含审核结果与审核人信息",
                () -> assertTrue(sqlSet.contains("review_status"), "缺少 review_status"),
                () -> assertTrue(sqlSet.contains("reviewer_id"), "缺少 reviewer_id"),
                () -> assertTrue(sqlSet.contains("reviewer_name"), "缺少 reviewer_name"),
                () -> assertTrue(sqlSet.contains("review_time"), "缺少 review_time"),
                () -> assertFalse(sqlSet.contains("review_remark"), "意见为空时不应写 review_remark"));
        assertAll("CAS 参数必须包含目标状态与 WHERE pending 条件值",
                () -> assertTrue(wrapper.getParamNameValuePairs().containsValue("approved"),
                        "参数应包含目标状态 approved"),
                () -> assertTrue(wrapper.getParamNameValuePairs().containsValue("pending"),
                        "WHERE 条件必须限定 pending（CAS 语义核心）"),
                () -> assertTrue(wrapper.getParamNameValuePairs().containsValue(REVIEWER_ID),
                        "参数应包含审核人 ID"),
                () -> assertTrue(wrapper.getParamNameValuePairs().containsValue(REVIEWER_NAME),
                        "参数应包含审核人姓名"));
    }

    @Test
    @DisplayName("项目负责人驳回：写 rejected + 驳回意见，非 admin 走 managerId 归属校验")
    void reviewRecords_rejectByProjectManager_writesRejectedWithRemark() {
        List<Long> ids = Collections.singletonList(1L);
        when(recordMapper.selectBatchIds(ids)).thenReturn(Collections.singletonList(record(1L)));
        // 非 admin：走项目负责人归属校验路径
        when(permissionService.getCurrentUserRoles())
                .thenReturn(new HashSet<>(Collections.singletonList("project_manager")));
        BizProject project = new BizProject();
        project.setId(PROJECT_ID);
        project.setManagerId(REVIEWER_ID);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(recordMapper.update(isNull(), any())).thenReturn(1);

        int rows = workloadService.reviewRecords(reviewDto(ids, false, "数量与现场不符"));

        assertEquals(1, rows);
        LambdaUpdateWrapper<BizWorkloadRecord> wrapper = captureUpdateWrapper();
        assertTrue(wrapper.getSqlSet().contains("review_remark"), "驳回意见非空时应写入 review_remark");
        assertTrue(wrapper.getParamNameValuePairs().containsValue("rejected"), "目标状态应为 rejected");
        assertTrue(wrapper.getParamNameValuePairs().containsValue("数量与现场不符"),
                "参数应包含驳回意见");
    }

    // ==================================================================
    // CAS 并发特征（与单条进度审核的关键语义差异）
    // ==================================================================

    @Test
    @DisplayName("CAS命中0行（并发/重复审核）：不抛异常，返回0（尽力而为语义）")
    void reviewRecords_casZeroRows_returnsZeroWithoutException() {
        List<Long> ids = Collections.singletonList(1L);
        when(recordMapper.selectBatchIds(ids)).thenReturn(Collections.singletonList(record(1L)));
        when(recordMapper.update(isNull(), any())).thenReturn(0);

        int rows = assertDoesNotThrow(
                () -> workloadService.reviewRecords(reviewDto(ids, true, null)),
                "批量审核 CAS 0 行应静默返回 0，不抛异常（与单条审核抛异常的语义不同）");

        assertEquals(0, rows, "并发冲突时应返回实际成功行数 0");
    }

    // ==================================================================
    // 前置校验：存在性 / 同项目 / 访问权限 / 审核归属
    // ==================================================================

    @Test
    @DisplayName("部分记录不存在：抛 DATA_NOT_FOUND，不触发更新")
    void reviewRecords_missingRecords_throwsWithoutUpdate() {
        List<Long> ids = Arrays.asList(1L, 2L);
        when(recordMapper.selectBatchIds(ids)).thenReturn(Collections.singletonList(record(1L)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workloadService.reviewRecords(reviewDto(ids, true, null)));

        assertEquals("部分填报记录不存在", ex.getMessage());
        verify(recordMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("跨项目混入：抛参数校验异常，不触发更新")
    void reviewRecords_crossProject_throwsWithoutUpdate() {
        List<Long> ids = Arrays.asList(1L, 2L);
        BizWorkloadRecord other = record(2L);
        other.setProjectId(999L);
        when(recordMapper.selectBatchIds(ids)).thenReturn(Arrays.asList(record(1L), other));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workloadService.reviewRecords(reviewDto(ids, true, null)));

        assertEquals("不能跨项目批量审核", ex.getMessage());
        verify(recordMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("无项目访问权限（IDOR防护）：抛无权异常，不触发更新")
    void reviewRecords_noProjectAccess_throwsWithoutUpdate() {
        List<Long> ids = Collections.singletonList(1L);
        when(recordMapper.selectBatchIds(ids)).thenReturn(Collections.singletonList(record(1L)));
        when(projectAuthorizationService.canAccessProject(PROJECT_ID)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workloadService.reviewRecords(reviewDto(ids, true, null)));

        assertEquals("无权访问该项目的工作量数据", ex.getMessage());
        verify(recordMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("非admin且非项目负责人：抛 FORBIDDEN，不触发更新")
    void reviewRecords_notManagerNotAdmin_throwsForbidden() {
        List<Long> ids = Collections.singletonList(1L);
        when(recordMapper.selectBatchIds(ids)).thenReturn(Collections.singletonList(record(1L)));
        when(permissionService.getCurrentUserRoles())
                .thenReturn(new HashSet<>(Collections.singletonList("engineer")));
        BizProject project = new BizProject();
        project.setId(PROJECT_ID);
        project.setManagerId(888L); // 项目负责人是别人
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workloadService.reviewRecords(reviewDto(ids, true, null)));

        // 归属校验已收敛至共享 ProjectAccessGuard 单点，异常消息以守卫实际消息为准
        assertEquals("您不是该项目的负责人，无权执行此操作", ex.getMessage());
        verify(recordMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("非admin且项目不存在：抛项目不存在异常")
    void reviewRecords_projectNotFound_throws() {
        List<Long> ids = Collections.singletonList(1L);
        when(recordMapper.selectBatchIds(ids)).thenReturn(Collections.singletonList(record(1L)));
        when(permissionService.getCurrentUserRoles())
                .thenReturn(new HashSet<>(Collections.singletonList("project_manager")));
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workloadService.reviewRecords(reviewDto(ids, true, null)));

        assertEquals("项目不存在", ex.getMessage());
    }
}
