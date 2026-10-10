package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizProjectTransferLog;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.BizProjectTransferLogMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 项目转移/授权审计日志共享组件。
 * <p>
 * 此前 writeTransferLog 与 getUserName 在 ProjectAuthServiceImpl /
 * ProjectAuthorizationServiceImpl / ProjectTeamServiceImpl 三处逐行重复
 * （DRY 违规，字段赋值易漂移），现收敛为单点共享组件。
 * <p>
 * 职责：
 * <ul>
 *   <li>{@link #getUserName(Long)}：查询用户姓名（null→"系统"，不存在→"未知用户"，优先 name 其次 username）；</li>
 *   <li>{@link #writeTransferLog}：写审计日志到 biz_project_transfer_log，
 *       覆盖 auth_request/approve/reject/revoke/transfer/team_add/team_remove/transfer_leader 等操作类型。</li>
 * </ul>
 * 注意：本组件不开启事务、不捕获异常——审计写入的事务边界与静默策略由调用方决定。
 *
 * @author hbsz
 */
@Component
public class ProjectTransferLogHelper {

    private final SysUserMapper userMapper;
    private final BizProjectTransferLogMapper transferLogMapper;

    public ProjectTransferLogHelper(SysUserMapper userMapper,
                                    BizProjectTransferLogMapper transferLogMapper) {
        this.userMapper = userMapper;
        this.transferLogMapper = transferLogMapper;
    }

    /**
     * 获取用户姓名（优先 name，其次 username）。
     *
     * @param userId 用户ID；null 返回 "系统"（系统自动操作），不存在返回 "未知用户"
     */
    public String getUserName(Long userId) {
        if (userId == null) {
            return "系统";
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return "未知用户";
        }
        return StringUtils.hasText(user.getName()) ? user.getName() : user.getUsername();
    }

    /**
     * 写审计日志到 biz_project_transfer_log。
     *
     * @param project      项目实体（取 id/name 冗余存储，历史项目删除后日志仍可读）
     * @param actionType   操作类型：auth_request/approve/reject/extend/revoke/transfer/
     *                     team_add/team_remove/transfer_leader 等
     * @param fromEngineer 原工程师（可为 null）
     * @param toEngineer   目标工程师（可为 null）
     * @param operatorId   操作人ID
     * @param operatorName 操作人姓名（调用方经 {@link #getUserName(Long)} 获取）
     * @param remark       操作备注（空白归一为 null）
     */
    public void writeTransferLog(BizProject project, String actionType,
                                 SysUser fromEngineer, SysUser toEngineer,
                                 Long operatorId, String operatorName, String remark) {
        BizProjectTransferLog logEntry = new BizProjectTransferLog();
        logEntry.setProjectId(project.getId());
        logEntry.setProjectName(project.getName());
        logEntry.setActionType(actionType);
        logEntry.setFromEngineerId(fromEngineer != null ? fromEngineer.getId() : null);
        logEntry.setFromEngineerName(displayName(fromEngineer));
        logEntry.setToEngineerId(toEngineer != null ? toEngineer.getId() : null);
        logEntry.setToEngineerName(displayName(toEngineer));
        logEntry.setOperatedBy(operatorId);
        logEntry.setOperatorName(operatorName);
        logEntry.setOperatedAt(LocalDateTime.now());
        logEntry.setRemark(StringUtils.hasText(remark) ? remark : null);
        transferLogMapper.insert(logEntry);
    }

    /**
     * 工程师显示姓名（review F4）：与 {@link #getUserName(Long)} 同规则——
     * 优先 name，为空时回退 username，避免 name 为空的用户在审计日志中留下 NULL 姓名。
     *
     * @param user 用户实体；null 返回 null（未指定工程师的场景）
     */
    private String displayName(SysUser user) {
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getName()) ? user.getName() : user.getUsername();
    }
}
