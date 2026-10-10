package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目授权申请实体
 * <p>
 * 对应数据库表 biz_project_auth_request。工程师/PM 向他人申请项目授权的审批流转表，
 * 记录申请、审批全流程。审批通过后会自动创建一条 {@link BizProjectAssignment} 授权记录。
 * <p>
 * 业务流程：
 * <ol>
 *   <li>申请人填写申请（projectId/targetEngineerId/scope/expireAt/reason）→ 状态 pending；</li>
 *   <li>审批人审核通过：状态 approved，自动创建 assignment 记录；</li>
 *   <li>审批人审核驳回：状态 rejected，仅记录意见。</li>
 * </ol>
 *
 * @author hbsz
 */
@Data
@TableName("biz_project_auth_request")
public class BizProjectAuthRequest extends BaseEntity {

    /** 主键ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID（FK→biz_project.id） */
    private Long projectId;

    /** 申请人用户ID（FK→sys_user.id） */
    private Long applicantId;

    /** 目标工程师用户ID（FK→sys_user.id） */
    private Long targetEngineerId;

    /** 申请范围：read_only 只读 / read_write 读写 / review 可审核 */
    private String scope;

    /** 申请有效期（NULL=永久） */
    private LocalDateTime expireAt;

    /** 申请理由 */
    private String reason;

    /** 状态：pending 待审批 / approved 已通过 / rejected 已驳回 */
    private String status;

    /** 审批人用户ID（FK→sys_user.id） */
    private Long approverId;

    /** 审批时间 */
    private LocalDateTime approvedAt;

    /** 审批意见 */
    private String comment;

}
