package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizProjectAssignment;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.BizProjectAssignmentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.ProjectTransferLogHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 授权回收独立 Service
 * <p>
 * 【修复H4】原 AssignmentExpireTask 中通过 this.revokeOne() 进行同类内部调用，
 * Spring AOP 代理无法拦截，导致 @Transactional 注解完全失效，事务不会生效。
 * 现将 revokeOne 抽取到独立 Service 类中，通过 Spring 注入的方式调用，
 * 确保经过代理对象，@Transactional 才能正常生效，保证软删除授权与写入审计日志在同一事务内。
 * <p>
 * 【修复P0（2026-08-20）】原手写审计日志构造置 operatedBy=null，与列 NOT NULL 约束冲突
 * （INSERT 报 1364，回收整体回滚、定时任务每晚失败）。现收敛复用
 * {@link ProjectTransferLogHelper} 审计写入单点，operated_by 已通过迁移
 * V20260820.1 放宽为可空，系统自动操作以 operated_by=NULL + operator_name 表达。
 *
 * @author hbsz
 */
@Slf4j
@Service
public class AssignmentRevokeService {

    /** 自动回收的撤销原因 */
    private static final String REVOKE_REASON = "授权过期自动回收";

    /** 系统自动操作的审计操作人姓名（对应 operated_by=NULL，见迁移 V20260820.1） */
    private static final String SYSTEM_OPERATOR_NAME = "系统定时任务";

    private final BizProjectAssignmentMapper assignmentMapper;
    private final BizProjectMapper projectMapper;
    private final SysUserMapper userMapper;
    private final ProjectTransferLogHelper transferLogHelper;

    public AssignmentRevokeService(BizProjectAssignmentMapper assignmentMapper,
                                   BizProjectMapper projectMapper,
                                   SysUserMapper userMapper,
                                   ProjectTransferLogHelper transferLogHelper) {
        this.assignmentMapper = assignmentMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.transferLogHelper = transferLogHelper;
    }

    /**
     * 回收单条过期授权（独立事务，避免单条失败影响整体回收）。
     * <p>
     * 注意：必须通过 Spring 注入的代理对象调用本方法，@Transactional 才能生效。
     *
     * @param assignment 过期的授权记录
     * @param now       当前时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void revokeOne(BizProjectAssignment assignment, LocalDateTime now) {
        // 1. 软删除授权记录
        LambdaUpdateWrapper<BizProjectAssignment> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BizProjectAssignment::getId, assignment.getId())
                .eq(BizProjectAssignment::getIsActive, 1)
                .set(BizProjectAssignment::getIsActive, 0)
                .set(BizProjectAssignment::getRevokeReason, REVOKE_REASON)
                .set(BizProjectAssignment::getRevokedAt, now);
        int rows = assignmentMapper.update(null, updateWrapper);
        if (rows != 1) {
            log.warn("[授权过期回收] 授权ID={} 更新影响行数={}, 跳过审计日志写入", assignment.getId(), rows);
            return;
        }

        // 2. 写审计日志——复用共享单点 ProjectTransferLogHelper（消除手写构造漂移）
        BizProject project = projectMapper.selectById(assignment.getProjectId());
        SysUser engineer = userMapper.selectById(assignment.getEngineerId());
        transferLogHelper.writeTransferLog(project, "revoke", engineer, null,
                null, SYSTEM_OPERATOR_NAME, REVOKE_REASON);

        log.info("[授权过期回收][成功] 授权ID={}, 项目={}, 工程师={}, 过期时间={}",
                assignment.getId(),
                project != null ? project.getName() : assignment.getProjectId(),
                engineer != null ? engineer.getName() : assignment.getEngineerId(),
                assignment.getExpireAt());
    }
}
