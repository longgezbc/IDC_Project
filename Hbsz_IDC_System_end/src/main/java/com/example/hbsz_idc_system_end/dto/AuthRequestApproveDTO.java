package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 项目授权申请审批 DTO
 * <p>
 * 由审批人（admin/PM）调用 POST /api/project-auth/requests/{id}/approve 或 /reject 时传入。
 *
 * @author hbsz
 */
@Data
public class AuthRequestApproveDTO implements Serializable {

    /** 是否通过：true 通过 / false 驳回 */
    @NotNull(message = "审批结果不能为空")
    private Boolean approve;

    /** 审批意见 */
    private String comment;

}
