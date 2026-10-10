package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 进度审核 DTO（项目经理审核工程师提交的进度填报时使用）。
 *
 * @author hbsz
 */
@Data
public class ProgressReviewDTO implements Serializable {

    /** 是否通过：true=通过，false=驳回 */
    @NotNull(message = "审核结果不能为空")
    private Boolean approve;

    /** 审核意见 */
    private String remark;

}
