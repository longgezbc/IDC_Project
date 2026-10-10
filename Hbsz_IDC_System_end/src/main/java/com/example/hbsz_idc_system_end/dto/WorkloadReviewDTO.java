package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 工作量填报批量审核 DTO（项目经理审核填报明细时使用）。
 * <p>
 * 审核采用 CAS 语义：仅 review_status='pending' 的记录可被审核，
 * 并发/重复审核时受影响行数为 0，由调用方提示。
 *
 * @author hbsz
 */
@Data
public class WorkloadReviewDTO implements Serializable {

    /** 待审核记录ID列表 */
    @NotEmpty(message = "请选择要审核的填报记录")
    @Size(max = 2000, message = "单次审核不能超过2000条")
    private List<Long> ids;

    /** 是否通过：true=通过，false=驳回 */
    @NotNull(message = "审核结果不能为空")
    private Boolean approve;

    /** 审核意见（驳回时建议填写原因） */
    @Size(max = 255, message = "审核意见不能超过255字符")
    private String remark;

}
