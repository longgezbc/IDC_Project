package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 文档审核 DTO（项目经理/管理员审核上传文档时使用）。
 *
 * @author hbsz
 */
@Data
public class DocumentReviewDTO implements Serializable {

    /** 是否通过：true=通过，false=驳回 */
    @NotNull(message = "审核结果不能为空")
    private Boolean approve;

    /** 审核意见 */
    private String remark;

}
