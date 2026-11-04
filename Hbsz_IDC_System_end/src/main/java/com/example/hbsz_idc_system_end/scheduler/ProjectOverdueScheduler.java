package com.example.hbsz_idc_system_end.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hbsz_idc_system_end.dto.NotificationSendDTO;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.SysNotification;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.SysNotificationMapper;
import com.example.hbsz_idc_system_end.service.INotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目工期延期预警调度器。
 * <p>
 * 每日 08:30 扫描未归档、未完成且 end_date 在未来 3 天内（含今日）或已逾期的项目，
 * 向项目经理推送 project_overdue 站内通知。
 * <p>
 * 预警条件：is_archived=0 AND status != '已完成' AND end_date <= today+3。
 * 即将到期（today <= end_date <= today+3）且进度 < 90% 视为有延期风险；
 * 已逾期（end_date < today）不论进度均预警。
 * <p>
 * 去重策略：同一项目同一天只通知一次（查 sys_notification 当天 project_overdue 记录）。
 *
 * @author hbsz
 */
@Slf4j
@Component
public class ProjectOverdueScheduler {

    private final BizProjectMapper projectMapper;
    private final SysNotificationMapper notificationMapper;
    private final INotificationService notificationService;

    public ProjectOverdueScheduler(BizProjectMapper projectMapper,
                                    SysNotificationMapper notificationMapper,
                                    INotificationService notificationService) {
        this.projectMapper = projectMapper;
        this.notificationMapper = notificationMapper;
        this.notificationService = notificationService;
    }

    /**
     * 每日 08:30 扫描延期/即将到期的项目并通知项目经理。
     * 错开 08:00 的任务到期通知，避免通知洪峰。
     */
    @Scheduled(cron = "0 30 8 * * ?")
    public void scanProjectOverdue() {
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(3);
        log.info("[项目延期预警] 开始扫描: today={}, deadline={}", today, deadline);

        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<BizProject>()
                .eq(BizProject::getIsArchived, 0)
                .ne(BizProject::getStatus, "已完成")
                .isNotNull(BizProject::getEndDate)
                .isNotNull(BizProject::getManagerId)
                .le(BizProject::getEndDate, deadline));

        if (CollectionUtils.isEmpty(projects)) {
            log.info("[项目延期预警] 扫描完成: 无待预警项目");
            return;
        }

        // 批量查询今日已通知的项目，构建去重集合
        Set<String> notifiedKeys = batchLoadNotifiedToday(projects, today);

        List<NotificationSendDTO> notifications = projects.stream()
                .filter(p -> {
                    String key = buildDedupKey(p.getId(), p.getManagerId());
                    if (notifiedKeys.contains(key)) {
                        return false;
                    }
                    // 即将到期但进度已 >= 90% 的不预警（基本能按时完成）
                    boolean overdue = p.getEndDate().isBefore(today);
                    boolean nearDue = !p.getEndDate().isBefore(today);
                    if (nearDue && p.getProgress() != null && p.getProgress() >= 90) {
                        return false;
                    }
                    return overdue || nearDue;
                })
                .map(p -> {
                    boolean overdue = p.getEndDate().isBefore(today);
                    String title = overdue ? "项目已逾期提醒" : "项目即将到期提醒";
                    String content = "项目「" + p.getName() + "」(" + p.getProjectCode() + ")"
                            + (overdue ? "已逾期，截止日期：" : "即将到期，截止日期：")
                            + p.getEndDate()
                            + "，当前进度：" + (p.getProgress() != null ? p.getProgress() : 0) + "%，请关注。";

                    NotificationSendDTO notif = new NotificationSendDTO();
                    notif.setUserId(p.getManagerId());
                    notif.setTitle(title);
                    notif.setContent(content);
                    notif.setNotifyType("project_overdue");
                    notif.setBizType("project");
                    notif.setBizId(p.getId());
                    notif.setPriority(overdue ? 3 : 2);
                    return notif;
                })
                .collect(Collectors.toList());

        if (!notifications.isEmpty()) {
            notificationService.sendNotifications(notifications);
        }
        log.info("[项目延期预警] 扫描完成: 候选={}, 已发送={}, 跳过(已通知)={}",
                projects.size(), notifications.size(), projects.size() - notifications.size());
    }

    /** 批量查询今日已发送的 project_overdue 通知，构建 projectId|managerId 去重集合 */
    private Set<String> batchLoadNotifiedToday(List<BizProject> projects, LocalDate today) {
        Set<Long> projectIds = projects.stream().map(BizProject::getId).collect(Collectors.toSet());
        Set<Long> managerIds = projects.stream().map(BizProject::getManagerId).collect(Collectors.toSet());
        LocalDateTime todayStart = today.atStartOfDay();

        List<SysNotification> notifiedList = notificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getBizType, "project")
                        .eq(SysNotification::getNotifyType, "project_overdue")
                        .ge(SysNotification::getCreatedAt, todayStart)
                        .in(SysNotification::getBizId, projectIds)
                        .in(SysNotification::getUserId, managerIds));

        Set<String> keys = new HashSet<>();
        if (notifiedList != null) {
            for (SysNotification n : notifiedList) {
                keys.add(buildDedupKey(n.getBizId(), n.getUserId()));
            }
        }
        return keys;
    }

    private String buildDedupKey(Long projectId, Long managerId) {
        return projectId + "|" + managerId;
    }
}
