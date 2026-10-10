package com.example.hbsz_idc_system_end.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hbsz_idc_system_end.dto.NotificationSendDTO;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizTask;
import com.example.hbsz_idc_system_end.entity.SysNotification;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizTaskMapper;
import com.example.hbsz_idc_system_end.mapper.SysNotificationMapper;
import com.example.hbsz_idc_system_end.service.INotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务到期通知定时调度器
 * <p>
 * 每日 08:00 扫描未来 3 天内即将到期（含今日到期）及已逾期未完成的任务，
 * 向任务负责人发送 task_due 类型站内通知。
 * <p>
 * 去重策略：同一任务同一天只通知一次——查询 sys_notification 表是否已存在
 * 当天（createdAt >= 今日 00:00:00）的相同 bizId + userId + notifyType 记录。
 *
 * @author hbsz
 */
@Slf4j
@Component
public class TaskDueNotificationScheduler {

    private final BizTaskMapper taskMapper;
    private final BizProjectMapper projectMapper;
    private final SysNotificationMapper notificationMapper;
    private final INotificationService notificationService;

    public TaskDueNotificationScheduler(BizTaskMapper taskMapper,
                                        BizProjectMapper projectMapper,
                                        SysNotificationMapper notificationMapper,
                                        INotificationService notificationService) {
        this.taskMapper = taskMapper;
        this.projectMapper = projectMapper;
        this.notificationMapper = notificationMapper;
        this.notificationService = notificationService;
    }

    /**
     * 每日 08:00 扫描即将到期/已逾期的任务并通知负责人。
     * <p>
     * cron 表达式：秒 分 时 日 月 周。
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void scanTaskDueNotifications() {
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(3);
        int sent = 0;
        int skipped = 0;
        log.info("[任务到期通知] 开始扫描: today={}, deadline={}", today, deadline);

        // 查询未完成且 endDate <= today+3 的任务
        List<BizTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<BizTask>()
                .ne(BizTask::getStatus, "已完成")
                .isNotNull(BizTask::getAssigneeId)
                .isNotNull(BizTask::getEndDate)
                .le(BizTask::getEndDate, deadline));

        if (CollectionUtils.isEmpty(tasks)) {
            log.info("[任务到期通知] 扫描完成: 无待通知任务");
            return;
        }

        // 【修复H12】原实现循环内每次调用 isAlreadyNotifiedToday 和 projectMapper.selectById，
        // 造成 N+1 查询。改为循环外批量查询：
        // 1) 一次性查出今日所有已发送的 task_due 通知，构建 (bizId, userId) 去重集合；
        // 2) 一次性查出所有相关项目，构建 projectId -> projectName 映射。
        Set<String> notifiedKeys = batchLoadNotifiedToday(tasks, today);
        Map<Long, String> projectNameMap = batchLoadProjectNames(tasks);

        // 使用可变容器包装 skipped 计数，使其可以在 lambda 中修改
        int[] skippedHolder = {0};

        // 构建批量通知列表（过滤已通知的任务）
        List<NotificationSendDTO> notifications = tasks.stream()
                .filter(task -> {
                    String dedupKey = buildDedupKey(task.getId(), task.getAssigneeId());
                    if (notifiedKeys.contains(dedupKey)) {
                        skippedHolder[0]++;
                        return false;
                    }
                    return true;
                })
                .map(task -> {
                    String projectName = projectNameMap.getOrDefault(task.getProjectId(), "未知项目");
                    boolean overdue = task.getEndDate().isBefore(today);
                    String title = overdue ? "任务已逾期提醒" : "任务即将到期提醒";
                    String content = (overdue ? "任务「" : "任务「") + task.getName()
                            + "」(" + projectName + ")"
                            + (overdue ? "已逾期，截止日期：" : "即将到期，截止日期：")
                            + task.getEndDate()
                            + "，请尽快处理。";

                    NotificationSendDTO notif = new NotificationSendDTO();
                    notif.setUserId(task.getAssigneeId());
                    notif.setTitle(title);
                    notif.setContent(content);
                    notif.setNotifyType("task_due");
                    notif.setBizType("task");
                    notif.setBizId(task.getId());
                    notif.setPriority(overdue ? 3 : 2);
                    return notif;
                })
                .collect(Collectors.toList());

        // 使用批量通知方法，减少数据库往返次数
        if (!notifications.isEmpty()) {
            notificationService.sendNotifications(notifications);
            sent = notifications.size();
        }
        skipped = skippedHolder[0];

        log.info("[任务到期通知] 扫描完成: 总计={}, 已发送={}, 跳过(已通知)={}", tasks.size(), sent, skipped);
    }

    /**
     * 【修复H12】一次性查出今日所有已发送的 task_due 通知，构建 (bizId|userId) 去重集合。
     * 替代原循环内逐条 selectCount 查询，将 N 次查询降为 1 次。
     *
     * @param tasks 待通知任务列表
     * @return 今日已通知的 (bizId|userId) 集合
     */
    private Set<String> batchLoadNotifiedToday(List<BizTask> tasks, LocalDate today) {
        // 收集本次需要去重检查的 (bizId, userId) 对，缩小查询范围
        Set<Long> taskIds = tasks.stream()
                .map(BizTask::getId)
                .collect(Collectors.toSet());
        Set<Long> userIds = tasks.stream()
                .map(BizTask::getAssigneeId)
                .collect(Collectors.toSet());

        LocalDateTime todayStart = today.atStartOfDay();
        List<SysNotification> notifiedList = notificationMapper.selectList(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getBizType, "task")
                .eq(SysNotification::getNotifyType, "task_due")
                .ge(SysNotification::getCreatedAt, todayStart)
                .in(SysNotification::getBizId, taskIds)
                .in(SysNotification::getUserId, userIds));

        Set<String> keys = new HashSet<>();
        if (notifiedList != null) {
            for (SysNotification n : notifiedList) {
                keys.add(buildDedupKey(n.getBizId(), n.getUserId()));
            }
        }
        return keys;
    }

    /**
     * 【修复H12】一次性查出所有相关项目，构建 projectId -> projectName 映射。
     * 替代原循环内逐条 projectMapper.selectById，将 N 次查询降为 1 次。
     *
     * @param tasks 待通知任务列表
     * @return projectId -> projectName 映射
     */
    private Map<Long, String> batchLoadProjectNames(List<BizTask> tasks) {
        Set<Long> projectIds = tasks.stream()
                .map(BizTask::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (projectIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<BizProject> projects = projectMapper.selectBatchIds(projectIds);
        Map<Long, String> map = new HashMap<>();
        if (projects != null) {
            for (BizProject p : projects) {
                map.put(p.getId(), p.getName());
            }
        }
        return map;
    }

    /**
     * 构建去重键：bizId|userId
     */
    private String buildDedupKey(Long bizId, Long userId) {
        return bizId + "|" + userId;
    }

}
