package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 刷新 Token 请求 DTO。
 * <p>
 * M2修复：原 refresh 接口使用 {@code Map<String,String>} 接收参数，无法通过 @Valid 校验，
 * 存在参数注入风险且缺少结构化校验。改为使用本 DTO 承载 refreshToken 字段，
 * 配合 {@code @Valid} + {@code @NotBlank} 保证参数非空，便于统一异常处理。
 *
 * @author hbsz
 */
@Data
public class RefreshTokenDTO implements Serializable {

    /** 刷新令牌：@NotBlank 非空校验，防止传入空 token 触发后续异常 */
    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;

}
