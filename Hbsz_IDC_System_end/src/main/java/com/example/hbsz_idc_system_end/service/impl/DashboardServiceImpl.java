package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.*;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizTask;
import com.example.hbsz_idc_system_end.entity.BizTaskProgressLog;
import com.example.hbsz_idc_system_end.entity.SysNotification;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizTaskMapper;
import com.example.hbsz_idc_system_end.mapper.BizTaskProgressLogMapper;
import com.example.hbsz_idc_system_end.mapper.SysNotificationMapper;
import com.example.hbsz_idc_system_end.service.IDashboardService;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作台服务实现
 * <p>
 * 按当前登录用户聚合工作台数据，核心原则：
 * <ul>
 *   <li>数据隔离：所有查询均按 {@link UserContext#getUserId()} 限定，
 *       我的任务按 assigneeId，我的填报按 reporterId，待我审核按项目 managerId；</li>
 *   <li>无角色判断：后端计算所有统计字段并返回，由前端按角色决定展示哪些卡片，
 *       简化后端逻辑；</li>
 *   <li>性能：批量查询关联项目名称，规避 N+1。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class DashboardServiceImpl implements IDashboardService {

    private final BizTaskMapper taskMapper;
    private final BizTaskProgressLogMapper progressLogMapper;
    private final BizProjectMapper projectMapper;
    private final SysNotificationMapper notificationMapper;
    private final IPermissionService permissionService;

    public DashboardServiceImpl(BizTaskMapper taskMapper,
                                BizTaskProgressLogMapper progressLogMapper,
                                BizProjectMapper projectMapper,
                                SysNotificationMapper notificationMapper,
                                IPermissionService permissionService) {
        this.taskMapper = taskMapper;
        this.progressLogMapper = progressLogMapper;
        this.projectMapper = projectMapper;
        this.notificationMapper = notificationMapper;
        this.permissionService = permissionService;
    }

    /**
     * 性能优化 #29：原 getSummary 方法串行执行 11 次 COUNT 查询，工作台首屏耗时较高。
     * 使用 @Cacheable 缓存 60 秒（RedisCacheManager 中为 dashboardSummary 单独配置 60s TTL），
     * 同一用户在 60 秒内重复刷新工作台直接命中缓存，大幅减少 DB 压力。
     * 缓存 key 使用当前登录用户 ID（通过 SpEL 调用 UserContext.getUserId()），
     * 实现按用户隔离缓存。
     */
    @Override
    @Cacheable(value = "dashboardSummary", key = "T(com.example.hbsz_idc_system_end.utils.UserContext).getUserId()")
    public DashboardSummaryVO getSummary() {
        Long userId = currentUserId();
        DashboardSummaryVO vo = new DashboardSummaryVO();
        vo.setRole("");

        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(3);

        // 修复 #4：聚合查询替代 8 次串行 selectCount（任务 4 次 + 填报 4 次）
        // 原 11 次 DB 查询降至 5 次（任务聚合 1 次 + 填报聚合 1 次 + 项目数 1 次 + 待审核 1 次 + 通知 1 次）
        Map<String, Object> taskCounts = taskMapper.selectTaskCountsByUser(userId, today, deadline);
        vo.setMyTaskCount(toInt(taskCounts.get("total")));
        vo.setMyPendingTaskCount(toInt(taskCounts.get("pending")));
        vo.setMyOverdueTaskCount(toInt(taskCounts.get("overdue")));
        vo.setMyUpcomingTaskCount(toInt(taskCounts.get("upcoming")));

        Map<String, Object> subCounts = progressLogMapper.selectSubmissionCountsByUser(userId);
        vo.setMySubmissionCount(toInt(subCounts.get("total")));
        vo.setMyPendingSubmissionCount(toInt(subCounts.get("pending")));
        vo.setMyApprovedSubmissionCount(toInt(subCounts.get("approved")));
        vo.setMyRejectedSubmissionCount(toInt(subCounts.get("rejected")));

        // 我负责的项目数
        Long managedCount = projectMapper.selectCount(new LambdaQueryWrapper<BizProject>()
                .eq(BizProject::getManagerId, userId));
        vo.setManagedProjectCount(managedCount != null ? managedCount.intValue() : 0);

        // 待我审核的进度数（我负责的项目下的 pending 日志）
        List<Long> managedProjectIds = getManagedProjectIds(userId);
        int pendingReview = 0;
        if (!managedProjectIds.isEmpty()) {
            Long prCount = progressLogMapper.selectCount(new LambdaQueryWrapper<BizTaskProgressLog>()
                    .in(BizTaskProgressLog::getProjectId, managedProjectIds)
                    .eq(BizTaskProgressLog::getReviewStatus, "pending"));
            pendingReview = prCount != null ? prCount.intValue() : 0;
        }
        vo.setPendingReviewCount(pendingReview);

        // 未读通知数
        Long unread = notificationMapper.selectCount(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, 0));
        vo.setUnreadNotificationCount(unread != null ? unread.intValue() : 0);

                return vo;
    }

        @Override
    public PageResult<MyTaskItemVO> myTasks(PageQuery query, String status) {
        Long userId = currentUserId();
        LambdaQueryWrapper<BizTask> wrapper = new LambdaQueryWrapper<BizTask>()
                .eq(BizTask::getAssigneeId, userId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(BizTask::getStatus, status);
        }
        wrapper.orderByDesc(BizTask::getEndDate);

        Page<BizTask> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<BizTask> result = taskMapper.selectPage(page, wrapper);

        // 批量查询项目名称
        Map<Long, String> projectNameMap = batchProjectNames(
                result.getRecords().stream().map(BizTask::getProjectId).distinct().collect(Collectors.toList()));

        List<MyTaskItemVO> voList = result.getRecords().stream()
                .map(t -> convertToMyTaskVO(t, projectNameMap))
                .collect(Collectors.toList());
        return new PageResult<>(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public PageResult<ProgressLogVO> mySubmissions(PageQuery query, String reviewStatus) {
        Long userId = currentUserId();
        LambdaQueryWrapper<BizTaskProgressLog> wrapper = new LambdaQueryWrapper<BizTaskProgressLog>()
                .eq(BizTaskProgressLog::getReporterId, userId);
        if (StringUtils.hasText(reviewStatus)) {
            wrapper.eq(BizTaskProgressLog::getReviewStatus, reviewStatus);
        }
        wrapper.orderByDesc(BizTaskProgressLog::getReportTime);

        Page<BizTaskProgressLog> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<BizTaskProgressLog> result = progressLogMapper.selectPage(page, wrapper);

        // 批量查询任务名称和项目名称
        List<Long> taskIds = result.getRecords().stream()
                .map(BizTaskProgressLog::getTaskId).distinct().collect(Collectors.toList());
        List<Long> projectIds = result.getRecords().stream()
                .map(BizTaskProgressLog::getProjectId).distinct().collect(Collectors.toList());

        Map<Long, String> taskNameMap = batchTaskNames(taskIds);
        Map<Long, String> projectNameMap = batchProjectNames(projectIds);

        List<ProgressLogVO> voList = result.getRecords().stream()
                .map(log -> convertToProgressLogVO(log, taskNameMap, projectNameMap))
                .collect(Collectors.toList());
        return new PageResult<>(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public RiskBoardVO getRiskBoard() {
        Long userId = currentUserId();

        // 校验角色：仅 PM / admin 可访问
        Set<String> roles = permissionService.getCurrentUserRoles();
        boolean isPmOrAdmin = roles != null && (roles.contains(CommonConstant.ROLE_CODE_ADMIN)
                || roles.contains(CommonConstant.ROLE_CODE_PROJECT_MANAGER));
        if (!isPmOrAdmin) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅项目经理或管理员可访问风险预警看板");
        }

        // admin 视角：所有项目；PM 视角：自己负责的项目
        List<Long> projectIds;
        if (roles.contains(CommonConstant.ROLE_CODE_ADMIN)) {
            List<BizProject> allProjects = projectMapper.selectList(new LambdaQueryWrapper<BizProject>()
                    .select(BizProject::getId).eq(BizProject::getIsArchived, 0));
            projectIds = allProjects.stream().map(BizProject::getId).collect(Collectors.toList());
        } else {
            projectIds = getManagedProjectIds(userId);
        }

        RiskBoardVO vo = new RiskBoardVO();
        vo.setTotalProjects(projectIds.size());

        if (projectIds.isEmpty()) {
            vo.setOverdueTaskCount(0);
            vo.setUpcomingTaskCount(0);
            vo.setPendingReviewCount(0);
            vo.setOverdueKeyTaskCount(0);
            vo.setRiskLevel("low");
            vo.setOverdueTasks(Collections.emptyList());
            vo.setUpcomingTasks(Collections.emptyList());
            vo.setProjectRisks(Collections.emptyList());
            return vo;
        }

        Map<Long, String> projectNameMap = batchProjectNames(projectIds);
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(3);

        // 查询这些项目下所有未完成任务（仅未归档项目）
        List<BizTask> allTasks = taskMapper.selectList(new LambdaQueryWrapper<BizTask>()
                .in(BizTask::getProjectId, projectIds)
                .ne(BizTask::getStatus, "已完成"));

        // 分类：逾期 + 即将到期
        List<RiskBoardVO.RiskTaskItem> overdueItems = new ArrayList<>();
        List<RiskBoardVO.RiskTaskItem> upcomingItems = new ArrayList<>();
        int overdueKeyCount = 0;

        for (BizTask task : allTasks) {
            if (task.getEndDate() == null) continue;

            if (task.getEndDate().isBefore(today)) {
                // 逾期
                RiskBoardVO.RiskTaskItem item = buildRiskTaskItem(task, projectNameMap, "overdue",
                        (int) java.time.temporal.ChronoUnit.DAYS.between(task.getEndDate(), today));
                overdueItems.add(item);
                if (task.getIsKeyTask() != null && task.getIsKeyTask() == 1) {
                    overdueKeyCount++;
                }
            } else if (!task.getEndDate().isAfter(deadline)) {
                // 即将到期（3 天内）
                RiskBoardVO.RiskTaskItem item = buildRiskTaskItem(task, projectNameMap, "upcoming", null);
                upcomingItems.add(item);
            }
        }

        // 排序：关键任务优先 → 逾期天数多优先
        overdueItems.sort((a, b) -> {
            int ka = a.getIsKeyTask() != null ? a.getIsKeyTask() : 0;
            int kb = b.getIsKeyTask() != null ? b.getIsKeyTask() : 0;
            if (ka != kb) return kb - ka;
            return b.getOverdueDays() - a.getOverdueDays();
        });
        upcomingItems.sort((a, b) -> {
            int ka = a.getIsKeyTask() != null ? a.getIsKeyTask() : 0;
            int kb = b.getIsKeyTask() != null ? b.getIsKeyTask() : 0;
            if (ka != kb) return kb - ka;
            return a.getEndDate().compareTo(b.getEndDate());
        });
        // 取前 20 条
        if (overdueItems.size() > 20) overdueItems = overdueItems.subList(0, 20);
        if (upcomingItems.size() > 20) upcomingItems = upcomingItems.subList(0, 20);

        vo.setOverdueTaskCount(overdueItems.size());
        vo.setUpcomingTaskCount(upcomingItems.size());
        vo.setOverdueKeyTaskCount(overdueKeyCount);
        vo.setOverdueTasks(overdueItems);
        vo.setUpcomingTasks(upcomingItems);

        // 待审核填报数
        Long pendingCount = progressLogMapper.selectCount(new LambdaQueryWrapper<BizTaskProgressLog>()
                .in(BizTaskProgressLog::getProjectId, projectIds)
                .eq(BizTaskProgressLog::getReviewStatus, "pending"));
        vo.setPendingReviewCount(pendingCount != null ? pendingCount.intValue() : 0);

        // 各项目风险摘要
        // 【修复M11】将已查询的 allTasks 传入 buildProjectRisks 复用，避免重复查询任务列表。
        List<RiskBoardVO.ProjectRiskItem> projectRisks = buildProjectRisks(projectIds, projectNameMap, today, deadline, allTasks);
        vo.setProjectRisks(projectRisks);

        // 全局风险等级
        vo.setRiskLevel(calcOverallRiskLevel(overdueItems.size(), overdueKeyCount,
                upcomingItems.size(), vo.getPendingReviewCount()));

        return vo;
    }

    /**
     * 构造风险任务明细项
     */
    private RiskBoardVO.RiskTaskItem buildRiskTaskItem(BizTask task, Map<Long, String> projectNameMap,
                                                         String riskType, Integer overdueDays) {
        RiskBoardVO.RiskTaskItem item = new RiskBoardVO.RiskTaskItem();
        item.setTaskId(task.getId());
        item.setTaskName(task.getName());
        item.setProjectId(task.getProjectId());
        item.setProjectName(projectNameMap.getOrDefault(task.getProjectId(), ""));
        item.setStatus(task.getStatus());
        item.setProgress(task.getProgress());
        item.setEndDate(task.getEndDate());
        item.setIsKeyTask(task.getIsKeyTask());
        item.setIsMilestone(task.getIsMilestone());
        item.setAssigneeId(task.getAssigneeId());
        item.setAssigneeName(task.getAssigneeName());
        item.setRiskType(riskType);
        item.setOverdueDays(overdueDays);
        return item;
    }

    /**
     * 构造各项目风险摘要
     */
    private List<RiskBoardVO.ProjectRiskItem> buildProjectRisks(List<Long> projectIds,
                                                                  Map<Long, String> projectNameMap,
                                                                  LocalDate today, LocalDate deadline,
                                                                  List<BizTask> allTasks) {
        List<RiskBoardVO.ProjectRiskItem> result = new ArrayList<>();

        // 【修复M11】复用 getRiskBoard 已查询的 allTasks，按项目分组，避免重复查询任务列表造成额外 DB 开销。
        Map<Long, List<BizTask>> tasksByProject = allTasks.stream()
                .collect(Collectors.groupingBy(BizTask::getProjectId));

        // 各项目待审核数
        // P1-3.3：改为 GROUP BY 聚合查询，避免加载全字段日志到内存再循环计数
        // 命中 P0-3.1 新增的复合索引 idx_project_review_time
        Map<Long, Long> pendingByProject = new HashMap<>();
        if (!projectIds.isEmpty()) {
            List<Map<String, Object>> pendingCounts = progressLogMapper.selectPendingCountByProjectIds(projectIds);
            for (Map<String, Object> row : pendingCounts) {
                Object pidObj = row.get("projectId");
                Object cntObj = row.get("pendingCount");
                if (pidObj != null && cntObj != null) {
                    Long pid = ((Number) pidObj).longValue();
                    Long cnt = ((Number) cntObj).longValue();
                    pendingByProject.put(pid, cnt);
                }
            }
        }

        for (Long pid : projectIds) {
            List<BizTask> tasks = tasksByProject.getOrDefault(pid, Collections.emptyList());
            int overdue = 0, upcoming = 0, keyOverdue = 0;

            for (BizTask task : tasks) {
                if (!"已完成".equals(task.getStatus()) && task.getEndDate() != null) {
                    if (task.getEndDate().isBefore(today)) {
                        overdue++;
                        if (task.getIsKeyTask() != null && task.getIsKeyTask() == 1) keyOverdue++;
                    } else if (!task.getEndDate().isAfter(deadline)) {
                        upcoming++;
                    }
                }
            }

            RiskBoardVO.ProjectRiskItem item = new RiskBoardVO.ProjectRiskItem();
            item.setProjectId(pid);
            item.setProjectName(projectNameMap.getOrDefault(pid, ""));
            item.setTotalTasks(tasks.size());
            item.setOverdueTasks(overdue);
            item.setUpcomingTasks(upcoming);
            item.setOverdueKeyTasks(keyOverdue);
            item.setPendingReviews(pendingByProject.getOrDefault(pid, 0L).intValue());
            item.setRiskLevel(calcProjectRiskLevel(overdue, keyOverdue, upcoming));
            result.add(item);
        }

        // 按风险等级降序（high → medium → low），同等级按逾期数降序
        result.sort((a, b) -> {
            int ra = riskOrder(a.getRiskLevel());
            int rb = riskOrder(b.getRiskLevel());
            if (ra != rb) return rb - ra;
            return b.getOverdueTasks() - a.getOverdueTasks();
        });

        return result;
    }

    /**
     * 计算项目风险等级：有逾期关键节点 → high；逾期>0 或 关键节点临期 → medium；否则 low
     */
    private String calcProjectRiskLevel(int overdue, int keyOverdue, int upcoming) {
        if (keyOverdue > 0) return "high";
        if (overdue > 0 || upcoming >= 3) return "medium";
        return "low";
    }

    /**
     * 计算全局风险等级：逾期关键节点>0 或 逾期>5 → high；逾期>0 或 临期>5 → medium；否则 low
     */
    private String calcOverallRiskLevel(int overdueCount, int overdueKeyCount,
                                          int upcomingCount, int pendingReview) {
        if (overdueKeyCount > 0 || overdueCount >= 5) return "high";
        if (overdueCount > 0 || upcomingCount >= 5 || pendingReview >= 10) return "medium";
        return "low";
    }

    /**
     * 风险等级排序：high=3, medium=2, low=1
     */
    private int riskOrder(String level) {
        if ("high".equals(level)) return 3;
        if ("medium".equals(level)) return 2;
        return 1;
    }

    // ===== 私有辅助方法 =====

    /**
     * 获取当前用户负责的项目ID列表
     */
    private List<Long> getManagedProjectIds(Long userId) {
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<BizProject>()
                .select(BizProject::getId)
                .eq(BizProject::getManagerId, userId));
        return projects.stream().map(BizProject::getId).collect(Collectors.toList());
    }

    /**
     * 批量查询项目名称，返回 projectId -> name 映射
     */
    private Map<Long, String> batchProjectNames(List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<BizProject>()
                .select(BizProject::getId, BizProject::getName)
                .in(BizProject::getId, projectIds));
        return projects.stream().collect(Collectors.toMap(BizProject::getId, BizProject::getName, (a, b) -> a));
    }

    /**
     * 批量查询任务名称，返回 taskId -> name 映射
     */
    private Map<Long, String> batchTaskNames(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<BizTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<BizTask>()
                .select(BizTask::getId, BizTask::getName)
                .in(BizTask::getId, taskIds));
        return tasks.stream().collect(Collectors.toMap(BizTask::getId, BizTask::getName, (a, b) -> a));
    }

    /**
     * BizTask 转 MyTaskItemVO
     */
    private MyTaskItemVO convertToMyTaskVO(BizTask task, Map<Long, String> projectNameMap) {
        MyTaskItemVO vo = new MyTaskItemVO();
        vo.setId(task.getId());
        vo.setName(task.getName());
        vo.setProjectId(task.getProjectId());
        vo.setProjectName(projectNameMap.getOrDefault(task.getProjectId(), ""));
        vo.setStatus(task.getStatus());
        vo.setProgress(task.getProgress());
        vo.setStartDate(task.getStartDate());
        vo.setEndDate(task.getEndDate());
        vo.setIsMilestone(task.getIsMilestone());
        vo.setIsKeyTask(task.getIsKeyTask());
        return vo;
    }

    /**
     * BizTaskProgressLog 转 ProgressLogVO（附带任务名和项目名）
     */
    private ProgressLogVO convertToProgressLogVO(BizTaskProgressLog log,
                                                  Map<Long, String> taskNameMap,
                                                  Map<Long, String> projectNameMap) {
        ProgressLogVO vo = new ProgressLogVO();
        vo.setId(String.valueOf(log.getId()));
        vo.setTaskId(String.valueOf(log.getTaskId()));
        vo.setTaskName(taskNameMap.getOrDefault(log.getTaskId(), ""));
        vo.setProjectId(String.valueOf(log.getProjectId()));
        vo.setProjectName(projectNameMap.getOrDefault(log.getProjectId(), ""));
        vo.setProgress(log.getProgress());
        vo.setPreviousProgress(log.getPreviousProgress());
        vo.setRemark(log.getRemark());
        vo.setReviewStatus(log.getReviewStatus());
        vo.setReviewerId(log.getReviewerId() != null ? String.valueOf(log.getReviewerId()) : null);
        vo.setReviewerName(log.getReviewerName());
        vo.setReviewTime(log.getReviewTime());
        vo.setReviewRemark(log.getReviewRemark());
        vo.setReporterId(log.getReporterId() != null ? String.valueOf(log.getReporterId()) : null);
        vo.setReporterName(log.getReporterName());
        vo.setReportTime(log.getReportTime());
        return vo;
    }

    /**
     * 修复 #4：将聚合查询返回的 Object 值安全转为 int
     * <p>
     * MySQL SUM(CASE WHEN...) 返回 Long/BigDecimal，COUNT(*) 返回 Long。
     * 统一处理 null 和各种 Number 子类。
     */
    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    /**
     * 获取当前登录用户ID
     */
    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

}
