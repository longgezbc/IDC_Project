package com.example.hbsz_idc_system_end.task;

import com.example.hbsz_idc_system_end.entity.BizProjectAssignment;
import com.example.hbsz_idc_system_end.mapper.BizProjectAssignmentMapper;
import com.example.hbsz_idc_system_end.service.impl.AssignmentRevokeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 授权过期自动回收定时任务
 * <p>
 * 每天凌晨 2:00 扫描 biz_project_assignment 表中已过期但未回收的有效授权记录
 * （is_active=1 AND expire_at IS NOT NULL AND expire_at < NOW()），
 * 逐条将 is_active 置为 0、写入 revoke_reason='授权过期自动回收' 与 revoked_at=NOW()，
 * 并同步写入 biz_project_transfer_log 审计日志。
 * <p>
 * 触发时间：cron = "0 0 2 * * ?"（每天凌晨 2 点）。
 *
 * @author hbsz
 */
@Slf4j
@Component
public class AssignmentExpireTask {

    private final BizProjectAssignmentMapper assignmentMapper;
    // 【修复H4】注入独立的 AssignmentRevokeService，通过 Spring 代理对象调用 revokeOne，
    // 确保 @Transactional 注解生效；原 this.revokeOne() 同类内部调用绕过了 AOP 代理，事务不生效。
    private final AssignmentRevokeService assignmentRevokeService;

    public AssignmentExpireTask(BizProjectAssignmentMapper assignmentMapper,
                                 AssignmentRevokeService assignmentRevokeService) {
        this.assignmentMapper = assignmentMapper;
        this.assignmentRevokeService = assignmentRevokeService;
    }

    /**
     * 每天凌晨 2:00 执行授权过期回收。
     * <p>
     * cron 表达式：{@code 0 0 2 * * ?}
     * <ul>
     *   <li>秒：0</li>
     *   <li>分：0</li>
     *   <li>时：2（凌晨 2 点）</li>
     *   <li>日：*</li>
     *   <li>月：*</li>
     *   <li>周：?（不限制）</li>
     * </ul>
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void revokeExpiredAssignments() {
        LocalDateTime now = LocalDateTime.now();
        log.info("[授权过期回收][开始] 触发时间={}", now);

        try {
            List<BizProjectAssignment> expiredList = assignmentMapper.selectExpiredActiveAssignments(now);
            if (expiredList == null || expiredList.isEmpty()) {
                log.info("[授权过期回收][完成] 无过期授权需要回收");
                return;
            }

            log.info("[授权过期回收] 待回收数量={}", expiredList.size());
            int successCount = 0;
            int failCount = 0;
            for (BizProjectAssignment assignment : expiredList) {
                try {
                    // 【修复H4】通过注入的 Service 调用，走 Spring AOP 代理，@Transactional 生效
                    assignmentRevokeService.revokeOne(assignment, now);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("[授权过期回收][失败] 授权ID={}, projectId={}, engineerId={}, error={}",
                            assignment.getId(), assignment.getProjectId(),
                            assignment.getEngineerId(), e.getMessage(), e);
                }
            }
            log.info("[授权过期回收][完成] 总数={}, 成功={}, 失败={}",
                    expiredList.size(), successCount, failCount);
        } catch (Exception e) {
            log.error("[授权过期回收][异常] 执行失败: {}", e.getMessage(), e);
        }
    }

}
