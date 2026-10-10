package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目-工程师授权关系实体
 * <p>
 * 对应数据库表 biz_project_assignment。维护项目与工程师之间的显式多对多授权关系。
 * 工程师只能访问其被显式授权的项目（is_active=1 的记录）。
 * 由项目经理（PM）通过转移/授权操作维护，每次变更同步写入 {@link BizProjectTransferLog} 审计日志。
 * <p>
 * 阶段3：授权规范化扩展字段：
 * <ul>
 *   <li>{@link #permissionScope}：授权范围（read_only 只读 / read_write 读写 / review 可审核），
 *       用于精细化控制工程师在项目下的操作权限；</li>
 *   <li>{@link #expireAt}：授权过期时间（NULL=永久），过期后定时任务自动回收；</li>
 *   <li>{@link #revokeReason}：撤销原因，便于审计与统计；</li>
 *   <li>{@link #revokedAt}：实际撤销时间。</li>
 * </ul>
 *
 * @author hbsz
 */
@Data
@TableName("biz_project_assignment")
public class BizProjectAssignment implements Serializable {

    /** 主键ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID（FK→biz_project.id） */
    private Long projectId;

    /** 工程师用户ID（FK→sys_user.id） */
    private Long engineerId;

    /** 授权人（PM）用户ID（FK→sys_user.id） */
    private Long assignedBy;

    /** 授权时间 */
    private LocalDateTime assignedAt;

    /** 是否有效：1有效 / 0已撤销 */
    private Integer isActive;

    /** 授权备注 */
    private String remark;

    /**
     * 授权范围：read_only 只读 / read_write 读写 / review 可审核。
     * <p>
     * 用于精细化控制工程师在项目下的操作权限：
     * <ul>
     *   <li>read_only：仅可查看，不可写；</li>
     *   <li>read_write：可读写，包括填报进度、上传文档等；</li>
     *   <li>review：可审核他人进度填报、删除文档等高级操作。</li>
     * </ul>
     */
    private String permissionScope;

    /** 授权过期时间（NULL=永久） */
    private LocalDateTime expireAt;

    /** 撤销原因（仅 is_active=0 时有值） */
    private String revokeReason;

    /** 实际撤销时间（仅 is_active=0 时有值） */
    private LocalDateTime revokedAt;

}
