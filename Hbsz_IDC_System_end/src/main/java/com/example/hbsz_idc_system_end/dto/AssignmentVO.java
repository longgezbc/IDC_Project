package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目授权关系视图对象
 * <p>
 * 用于返回项目-工程师授权关系详情，包含工程师姓名、项目名、是否已过期、剩余天数等冗余与计算字段。
 *
 * @author hbsz
 */
@Data
public class AssignmentVO implements Serializable {

    /** 授权记录ID */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 项目名称（冗余） */
    private String projectName;

    /** 工程师用户ID */
    private Long engineerId;

    /** 工程师姓名（冗余） */
    private String engineerName;

    /** 工程师用户名 */
    private String engineerUsername;

    /** 工程师手机号 */
    private String engineerPhone;

    /** 授权人ID */
    private Long assignedBy;

    /** 授权人姓名（冗余） */
    private String assignedByName;

    /** 授权时间 */
    private LocalDateTime assignedAt;

    /** 是否有效：1有效 / 0已撤销 */
    private Integer isActive;

    /** 授权备注 */
    private String remark;

    /** 授权范围：read_only / read_write / review */
    private String permissionScope;

    /** 授权过期时间（NULL=永久） */
    private LocalDateTime expireAt;

    /** 撤销原因 */
    private String revokeReason;

    /** 实际撤销时间 */
    private LocalDateTime revokedAt;

    /** 是否已过期（基于 expireAt 与当前时间比较） */
    private Boolean isExpired;

    /** 剩余天数（expireAt 为空时返回 null；已过期返回负数） */
    private Long daysToExpire;

}
