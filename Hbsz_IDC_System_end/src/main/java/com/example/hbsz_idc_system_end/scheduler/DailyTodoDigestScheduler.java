package com.example.hbsz_idc_system_end.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hbsz_idc_system_end.dto.NotificationSendDTO;
import com.example.hbsz_idc_system_end.entity.SysNotification;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.ApprovalTodoMapper;
import com.example.hbsz_idc_system_end.mapper.SysNotificationMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.INotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 每日待办摘要推送调度器
 * <p>
 * 每日 09:00 统计四类审批待办（进度/工作量/文档/授权），向对应审批人推送
 * 「今日待办摘要」站内通知，将 TodoCenter 从被动查看升级为主动触达。
 * <p>
 * 归属规则（与待办中心数据隔离口径一致）：
 * <ul>
 *   <li>进度/工作量/文档待审 → 项目负责人（biz_project.manager_id）；</li>
 *   <li>授权申请待审 → 全部启用状态的 admin 用户。</li>
 * </ul>
 * 去重策略：同一用户当天只推送一次摘要——查询 sys_notification 表是否已存在
 * 当天（createdAt >= 今日 00:00:00）的 notify_type='todo_digest' + userId 记录，
 * 防止应用重启或集群多实例导致的重复推送。
 *
 * @author hbsz
 */
@Slf4j
@Component
public class DailyTodoDigestScheduler {

    private static final String NOTIFY_TYPE_DIGEST = "todo_digest";

    private final ApprovalTodoMapper approvalTodoMapper;
    private final SysUserMapper userMapper;
    private final SysNotificationMapper notificationMapper;
    private final INotificationService notificationService;

    public DailyTodoDigestScheduler(ApprovalTodoMapper approvalTodoMapper,
                                    SysUserMapper userMapper,
                                    SysNotificationMapper notificationMapper,
                                    INotificationService notificationService) {
        this.approvalTodoMapper = approvalTodoMapper;
        this.userMapper = userMapper;
        this.notificationMapper = notificationMapper;
        this.notificationService = notificationService;
    }

    /**
     * 每日 09:00 推送待办摘要。
     * <p>
     * cron 表达式：秒 分 时 日 月 周。
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void pushDailyTodoDigest() {
        LocalDate today = LocalDate.now();
        log.info("[待办摘要] 开始统计: date={}", today);

        // 1) 三类业务待办按项目负责人分组统计
        Map<Long, Map<String, Long>> todoByUser = loadManagerTodos();

        // 2) 授权申请待审归入 admin（与 admin 全量可见口径一致）
        mergeAuthTodos(todoByUser);

        if (todoByUser.isEmpty()) {
            log.info("[待办摘要] 统计完成: 全员无待办，跳过推送");
            return;
        }

        // 3) 当天已推送过的用户去重
        Set<Long> alreadyPushed = loadDigestPushedToday(todoByUser.keySet(), today);

        // 4) 构建批量通知（仅保留总数 > 0 且未推送的用户）
        List<NotificationSendDTO> notifications = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Long>> entry : todoByUser.entrySet()) {
            Long userId = entry.getKey();
            if (alreadyPushed.contains(userId)) {
                continue;
            }
            long total = entry.getValue().values().stream().mapToLong(Long::longValue).sum();
            if (total <= 0) {
                continue;
            }
            notifications.add(buildDigestNotification(userId, total, entry.getValue()));
        }

        if (!notifications.isEmpty()) {
            notificationService.sendNotifications(notifications);
        }
        log.info("[待办摘要] 推送完成: 有待办用户={}, 已发送={}, 跳过(已推送)={}",
                todoByUser.size(), notifications.size(), todoByUser.size() - notifications.size());
    }

    /**
     * 查询三类业务待办（进度/工作量/文档），按项目负责人分组。
     *
     * @return userId → { source → cnt }
     */
    private Map<Long, Map<String, Long>> loadManagerTodos() {
        Map<Long, Map<String, Long>> result = new HashMap<>();
        List<Map<String, Object>> rows = approvalTodoMapper.countPendingByManager();
        if (CollectionUtils.isEmpty(rows)) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            Long userId = ((Number) row.get("userId")).longValue();
            String source = String.valueOf(row.get("source"));
            long cnt = ((Number) row.get("cnt")).longValue();
            result.computeIfAbsent(userId, k -> new HashMap<>()).put(source, cnt);
        }
        return result;
    }

    /**
     * 授权申请待审通知全部启用状态的 admin（存在待审时）。
     */
    private void mergeAuthTodos(Map<Long, Map<String, Long>> todoByUser) {
        long authPending = approvalTodoMapper.countPendingAuth();
        if (authPending <= 0) {
            return;
        }
        List<SysUser> admins = userMapper.selectActiveAdmins();
        if (CollectionUtils.isEmpty(admins)) {
            log.warn("[待办摘要] 存在 {} 条授权待审但无启用状态的 admin 用户", authPending);
            return;
        }
        for (SysUser admin : admins) {
            todoByUser.computeIfAbsent(admin.getId(), k -> new HashMap<>()).put("auth", authPending);
        }
    }

    /**
     * 查询当天已推送过摘要的用户集合（幂等去重）。
     */
    private Set<Long> loadDigestPushedToday(Set<Long> userIds, LocalDate today) {
        List<SysNotification> pushed = notificationMapper.selectList(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getNotifyType, NOTIFY_TYPE_DIGEST)
                .ge(SysNotification::getCreatedAt, today.atStartOfDay())
                .in(SysNotification::getUserId, userIds));
        Set<Long> result = new HashSet<>();
        if (pushed != null) {
            for (SysNotification n : pushed) {
                result.add(n.getUserId());
            }
        }
        return result;
    }

    /**
     * 构建单条摘要通知（正文按固定顺序拼各类数量）。
     */
    private NotificationSendDTO buildDigestNotification(Long userId, long total, Map<String, Long> counts) {
        NotificationSendDTO notif = new NotificationSendDTO();
        notif.setUserId(userId);
        notif.setTitle("今日待办摘要");
        notif.setContent("您有 " + total + " 条待办待处理（" + buildDetail(counts) + "），请进入审批中心及时处理");
        notif.setNotifyType(NOTIFY_TYPE_DIGEST);
        notif.setBizType("system");
        notif.setPriority(2);
        return notif;
    }

    /**
     * 拼接摘要明细：进度 X 条、工作量 Y 条、文档 Z 条、授权 W 条（仅含 > 0 项）。
     */
    private String buildDetail(Map<String, Long> counts) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, "进度", counts.get("progress"));
        appendPart(sb, "工作量", counts.get("workload"));
        appendPart(sb, "文档", counts.get("document"));
        appendPart(sb, "授权", counts.get("auth"));
        return sb.length() > 0 ? sb.toString() : "无";
    }

    private void appendPart(StringBuilder sb, String label, Long cnt) {
        if (cnt == null || cnt <= 0) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("、");
        }
        sb.append(label).append(" ").append(cnt).append(" 条");
    }

}
