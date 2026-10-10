package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目授权申请创建 DTO
 * <p>
 * 由申请人（PM/admin）调用 POST /api/project-auth/requests 创建授权申请时传入。
 *
 * @author hbsz
 */
@Data
public class AuthRequestCreateDTO implements Serializable {

    /** 项目ID（必填） */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 目标工程师用户ID（必填） */
    @NotNull(message = "目标工程师不能为空")
    private Long targetEngineerId;

    /** 申请范围：read_only 只读 / read_write 读写 / review 可审核 */
    private String scope;

    /** 申请有效期（NULL=永久） */
    private LocalDateTime expireAt;

    /** 申请理由 */
    private String reason;

}
