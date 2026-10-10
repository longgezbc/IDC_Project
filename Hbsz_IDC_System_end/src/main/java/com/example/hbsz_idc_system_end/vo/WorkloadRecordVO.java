package com.example.hbsz_idc_system_end.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工作量填报明细视图对象（审核列表）。
 * <p>
 * 供项目经理审核填报：联表携带清单项作业类型/作业范围/单位等上下文，
 * 以及审核状态、驳回原因、填报人与时间。
 *
 * @author hbsz
 */
@Data
public class WorkloadRecordVO implements Serializable {

    /** 记录ID */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 清单项ID */
    private Long itemId;

    /** 作业类型 */
    private String category;

    /** 作业范围 */
    private String workScope;

    /** 计量单位 */
    private String unit;

    /** 填报日期 */
    private LocalDate recordDate;

    /** 施工人员姓名 */
    private String workerName;

    /** 施工人员用户ID（历史自由文本记录为 null） */
    private Long workerUserId;

    /** 当日完成数量 */
    private BigDecimal quantity;

    /** 审核状态：pending/approved/rejected */
    private String reviewStatus;

    /** 审核意见（驳回原因） */
    private String reviewRemark;

    /** 审核人姓名 */
    private String reviewerName;

    /** 审核时间 */
    private LocalDateTime reviewTime;

    /** 填报时间 */
    private LocalDateTime createdAt;

}
