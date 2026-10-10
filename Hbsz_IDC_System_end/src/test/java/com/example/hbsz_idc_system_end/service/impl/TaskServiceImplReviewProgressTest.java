package com.example.hbsz_idc_system_end.service.impl;

import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.dto.NotificationSendDTO;
import com.example.hbsz_idc_system_end.dto.ProgressLogVO;
import com.example.hbsz_idc_system_end.dto.ProgressReviewDTO;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizTask;
import com.example.hbsz_idc_system_end.entity.BizTaskProgressLog;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizTaskMapper;
import com.example.hbsz_idc_system_end.mapper.BizTaskProgressLogMapper;
import com.example.hbsz_idc_system_end.service.INotificationService;
import com.example.hbsz_idc_system_end.service.IProjectAuthorizationService;
import com.example.hbsz_idc_system_end.utils.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link TaskServiceImpl#reviewProgress(Long, ProgressReviewDTO)} 特征测试（Characterization Tests）。
 * <p>
 * 目的：锁定当前 CAS 并发审核行为，防止后续重构（如拆分 Service、调整通知逻辑）
 * 无意中改变以下关键契约：
 * <ul>
 *   <li>CAS 更新失败（affectedRows=0）必须抛"已审核"异常且不执行任何后续副作用；</li>
 *   <li>仅审核通过才回写任务进度、联动任务状态、同步项目整体进度；</li>
 *   <li>驳回路径不触发任务/项目任何写操作；</li>
 *   <li>通知为静默投递，失败不影响主流程。</li>
 * </ul>
 * 全部使用 Mockito 纯单元测试，不依赖数据库/Redis，可独立运行。
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("进度审核 CAS 特征测试")
class TaskServiceImplReviewProgressTest {

    private static final Long LOG_ID = 100L;
    private static final Long TASK_ID = 200L;
    private static final Long PROJECT_ID = 300L;
    private static final Long REPORTER_ID = 400L;
    private static final Long REVIEWER_ID = 500L;
    private static final String REVIEWER_NAME = "审核人张三";

    @Mock
    private BizTaskMapper taskMapper;
    @Mock
    private BizTaskProgressLogMapper progressLogMapper;
    @Mock
    private BizProjectMapper projectMapper;
    @Mock
    private INotificationService notificationService;
    @Mock
    private IProjectAuthorizationService projectAuthorizationService;

    private TaskServiceImpl taskService;

    private BizTaskProgressLog progressLog;
    private BizTask task;
    private BizProject project;

    @BeforeEach
    void setUp() {
        // objectMapper 用真实实例：VO 转换中 images 为 null 时直接返回空列表，不触发 JSON 解析
        taskService = new TaskServiceImpl(taskMapper, progressLogMapper, projectMapper,
                new ObjectMapper(), notificationService, projectAuthorizationService);

        UserContext.setUserId(REVIEWER_ID);
        UserContext.setUsername(REVIEWER_NAME);

        progressLog = new BizTaskProgressLog();
        progressLog.setId(LOG_ID);
        progressLog.setTaskId(TASK_ID);
        progressLog.setProjectId(PROJECT_ID);
        progressLog.setReporterId(REPORTER_ID);
        progressLog.setReporterName("填报人李四");
        progressLog.setReviewStatus("pending");
        progressLog.setPreviousProgress(30);
        progressLog.setProgress(50);
        progressLog.setReportTime(LocalDateTime.now().minusHours(2));

        task = new BizTask();
        task.setId(TASK_ID);
        task.setProjectId(PROJECT_ID);
        task.setProgress(30);
        task.setStatus("进行中");

        project = new BizProject();
        project.setId(PROJECT_ID);
        project.setProgress(30);
        project.setManagerId(REVIEWER_ID);

        // 默认桩：审核链路全部放行，各测试按需覆盖
        when(progressLogMapper.selectById(LOG_ID)).thenReturn(progressLog);
        when(projectAuthorizationService.canAccessProject(PROJECT_ID)).thenReturn(true);
        when(progressLogMapper.updateReviewStatusCAS(eq(LOG_ID), anyString(), eq(REVIEWER_ID),
                eq(REVIEWER_NAME), any(LocalDateTime.class), any())).thenReturn(1);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskMapper.updateById(any(BizTask.class))).thenReturn(1);
        // 项目进度同步前后各查一次，返回同一实例即可
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(taskMapper.updateProjectProgressByTaskAvg(PROJECT_ID)).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private ProgressReviewDTO reviewDto(Boolean approve, String remark) {
        ProgressReviewDTO dto = new ProgressReviewDTO();
        dto.setApprove(approve);
        dto.setRemark(remark);
        return dto;
    }

    // ==================================================================
    // 审核通过：任务进度回写 + 状态联动
    // ==================================================================

    @Test
    @DisplayName("通过且进度100%：任务状态联动为已完成，回写任务进度并同步项目进度")
    void reviewProgress_approve_100Percent_setsTaskCompleted() {
        progressLog.setProgress(100);

        ProgressLogVO vo = taskService.reviewProgress(LOG_ID, reviewDto(true, null));

        assertEquals("approved", vo.getReviewStatus(), "审核后 VO 状态应为 approved");
        assertEquals(100, task.getProgress(), "任务进度应回写为 100");
        assertEquals("已完成", task.getStatus(), "进度 100% 时任务状态应联动为已完成");
        verify(taskMapper).updateById(task);
        verify(taskMapper).updateProjectProgressByTaskAvg(PROJECT_ID);
    }

    @Test
    @DisplayName("通过且进度50%：任务状态联动为进行中")
    void reviewProgress_approve_50Percent_setsTaskInProgress() {
        task.setStatus("待开始");

        ProgressLogVO vo = taskService.reviewProgress(LOG_ID, reviewDto(true, null));

        assertEquals("approved", vo.getReviewStatus());
        assertEquals(50, task.getProgress(), "任务进度应回写为日志进度 50");
        assertEquals("进行中", task.getStatus(), "进度 >0 且 <100 时任务状态应联动为进行中");
    }

    @Test
    @DisplayName("通过：填报备注与实际起止日期同步到任务，项目实际日期仅在为空时填充")
    void reviewProgress_approve_syncsRemarkAndActualDates() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 15);
        progressLog.setRemark("本期完成光缆上架");
        progressLog.setActualStartDate(start);
        progressLog.setActualEndDate(end);

        taskService.reviewProgress(LOG_ID, reviewDto(true, null));

        assertEquals("本期完成光缆上架", task.getRemark(), "填报备注非空时应同步到任务备注");
        assertEquals(start, task.getActualStartDate(), "实际开工日期应同步到任务");
        assertEquals(end, task.getActualEndDate(), "实际结束日期应同步到任务");
        // 项目侧：开工日期为空被填充，结束日期总是覆盖 → 触发项目更新
        assertEquals(start, project.getActualStartDate(), "项目实际开工日期为空时应被填充");
        assertEquals(end, project.getActualEndDate(), "项目实际结束日期应被同步");
        verify(projectMapper).updateById(project);
    }

    @Test
    @DisplayName("通过：向填报人投递审核通过通知（notifyType=progress，优先级=1）")
    void reviewProgress_approve_notifiesReporter() {
        taskService.reviewProgress(LOG_ID, reviewDto(true, "干得不错"));

        ArgumentCaptor<NotificationSendDTO> captor = ArgumentCaptor.forClass(NotificationSendDTO.class);
        verify(notificationService).sendNotification(captor.capture());
        NotificationSendDTO sent = captor.getValue();
        assertEquals(REPORTER_ID, sent.getUserId(), "通知接收人应为填报人");
        assertEquals("进度审核通过", sent.getTitle(), "通过通知标题应为『进度审核通过』");
        assertEquals("progress", sent.getNotifyType());
        assertEquals(1, sent.getPriority(), "通过通知优先级应为 1");
        assertTrue(sent.getContent().contains(REVIEWER_NAME), "通知内容应包含审核人姓名");
        assertTrue(sent.getContent().contains("干得不错"), "通知内容应包含审核意见");
    }

    // ==================================================================
    // 驳回：不产生任务/项目写操作
    // ==================================================================

    @Test
    @DisplayName("驳回：不回写任务进度、不更新任务、不同步项目进度")
    void reviewProgress_reject_noTaskOrProjectMutation() {
        ProgressLogVO vo = taskService.reviewProgress(LOG_ID, reviewDto(false, "数量不符"));

        assertEquals("rejected", vo.getReviewStatus(), "驳回后 VO 状态应为 rejected");
        assertEquals(30, task.getProgress(), "驳回不应改变任务进度");
        verify(taskMapper, never()).selectById(anyLong());
        verify(taskMapper, never()).updateById(any(BizTask.class));
        verify(taskMapper, never()).updateProjectProgressByTaskAvg(anyLong());
        verify(projectMapper, never()).updateById(any(BizProject.class));
    }

    @Test
    @DisplayName("驳回：向填报人投递驳回通知（优先级=2，内容含驳回原因）")
    void reviewProgress_reject_notifiesReporterWithRemark() {
        taskService.reviewProgress(LOG_ID, reviewDto(false, "数量与现场不符"));

        ArgumentCaptor<NotificationSendDTO> captor = ArgumentCaptor.forClass(NotificationSendDTO.class);
        verify(notificationService).sendNotification(captor.capture());
        NotificationSendDTO sent = captor.getValue();
        assertEquals("进度审核驳回", sent.getTitle());
        assertEquals(2, sent.getPriority(), "驳回通知优先级应为 2");
        assertTrue(sent.getContent().contains("数量与现场不符"), "驳回通知应包含驳回原因");
    }

    // ==================================================================
    // CAS 并发防护（核心特征）
    // ==================================================================

    @Test
    @DisplayName("CAS失败(affectedRows=0)：抛已审核异常，且不执行任何任务/项目/通知副作用")
    void reviewProgress_casConflict_throwsAndSkipsAllFollowUps() {
        when(progressLogMapper.updateReviewStatusCAS(eq(LOG_ID), anyString(), eq(REVIEWER_ID),
                eq(REVIEWER_NAME), any(LocalDateTime.class), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.reviewProgress(LOG_ID, reviewDto(true, null)),
                "CAS 更新 0 行时应抛出重复审核异常");

        assertEquals("该进度填报已审核，不可重复审核", ex.getMessage());
        // 关键特征：并发冲突时不得回写任务进度或发通知（防止进度回写两次、通知发送两次）
        verify(taskMapper, never()).updateById(any(BizTask.class));
        verify(taskMapper, never()).updateProjectProgressByTaskAvg(anyLong());
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    @DisplayName("CAS写入成功后VO携带审核人信息（不再二次updateById写日志表）")
    void reviewProgress_casSuccess_voCarriesReviewerInfoWithoutSecondUpdate() {
        ProgressLogVO vo = taskService.reviewProgress(LOG_ID, reviewDto(true, null));

        assertEquals(String.valueOf(REVIEWER_ID), vo.getReviewerId(), "VO 应携带审核人 ID");
        assertEquals(REVIEWER_NAME, vo.getReviewerName(), "VO 应携带审核人姓名");
        assertNotNull(vo.getReviewTime(), "VO 应携带审核时间");
        // 特征：审核状态已由 CAS SQL 写库，实体不允许再走 updateById 二次提交
        verify(progressLogMapper, never()).updateById(any(BizTaskProgressLog.class));
    }

    // ==================================================================
    // 前置校验（在 CAS 之前拦截，避免误写）
    // ==================================================================

    @Test
    @DisplayName("日志不存在：抛 DATA_NOT_FOUND，不触发 CAS")
    void reviewProgress_logNotFound_throwsBeforeCas() {
        when(progressLogMapper.selectById(LOG_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.reviewProgress(LOG_ID, reviewDto(true, null)));

        assertEquals("进度日志不存在", ex.getMessage());
        verify(progressLogMapper, never()).updateReviewStatusCAS(anyLong(), anyString(), anyLong(),
                anyString(), any(LocalDateTime.class), any());
    }

    @Test
    @DisplayName("日志已非pending（应用层检查）：抛已审核异常，CAS不执行")
    void reviewProgress_alreadyReviewed_throwsBeforeCas() {
        progressLog.setReviewStatus("approved");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.reviewProgress(LOG_ID, reviewDto(true, null)));

        assertEquals("该进度填报已审核，不可重复审核", ex.getMessage());
        verify(progressLogMapper, never()).updateReviewStatusCAS(anyLong(), anyString(), anyLong(),
                anyString(), any(LocalDateTime.class), any());
    }

    @Test
    @DisplayName("无项目访问权限（IDOR防护）：抛无权异常，CAS不执行")
    void reviewProgress_noProjectAccess_throwsBeforeCas() {
        when(projectAuthorizationService.canAccessProject(PROJECT_ID)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.reviewProgress(LOG_ID, reviewDto(true, null)));

        assertEquals("无权审核该进度日志", ex.getMessage());
        verify(progressLogMapper, never()).updateReviewStatusCAS(anyLong(), anyString(), anyLong(),
                anyString(), any(LocalDateTime.class), any());
    }

    @Test
    @DisplayName("通过但任务不存在：抛任务不存在异常（事务回滚）")
    void reviewProgress_taskNotFoundOnApprove_throws() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.reviewProgress(LOG_ID, reviewDto(true, null)));

        assertEquals("任务不存在", ex.getMessage());
        verify(taskMapper, never()).updateById(any(BizTask.class));
    }

    // ==================================================================
    // 通知容错
    // ==================================================================

    @Test
    @DisplayName("通知投递失败：静默吞掉异常，审核主流程正常返回")
    void reviewProgress_notificationFailure_swallowed() {
        doThrow(new RuntimeException("WebSocket down"))
                .when(notificationService).sendNotification(any());

        ProgressLogVO vo = assertDoesNotThrow(
                () -> taskService.reviewProgress(LOG_ID, reviewDto(true, null)),
                "通知失败不应影响审核主流程");

        assertEquals("approved", vo.getReviewStatus(), "通知失败时审核结果仍应正常返回");
    }
}
