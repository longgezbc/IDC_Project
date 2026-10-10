package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目授权申请视图对象
 * <p>
 * 用于返回授权申请详情，包含申请人姓名、目标工程师姓名、项目名、审批人姓名等冗余字段。
 *
 * @author hbsz
 */
@Data
public class AuthRequestVO implements Serializable {

    /** 申请ID */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 项目名称（冗余） */
    private String projectName;

    /** 申请人ID */
    private Long applicantId;

    /** 申请人姓名（冗余） */
    private String applicantName;

    /** 目标工程师ID */
    private Long targetEngineerId;

    /** 目标工程师姓名（冗余） */
    private String targetEngineerName;

    /** 申请范围：read_only / read_write / review */
    private String scope;

    /** 申请有效期（NULL=永久） */
    private LocalDateTime expireAt;

    /** 申请理由 */
    private String reason;

    /** 状态：pending / approved / rejected */
    private String status;

    /** 审批人ID */
    private Long approverId;

    /** 审批人姓名（冗余） */
    private String approverName;

    /** 审批时间 */
    private LocalDateTime approvedAt;

    /** 审批意见 */
    private String comment;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
