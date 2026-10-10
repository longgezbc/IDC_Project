package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求 DTO（请求参数封装）。
 * 用于接收用户名、密码、图形验证码及记住登录状态的登录参数。
 * <p>
 * 安全加固（P0-4.1）：验证码强制启用。原 LoginDTO 未要求验证码字段，
 * CaptchaController 虽能生成验证码但未接入登录流程，导致登录接口可被
 * 自动化脚本无验证码直接尝试，配合限流仍有暴力破解窗口。
 * 现将 captchaId、captcha 标记为 @NotBlank，由后端强制校验。
 *
 * @author hbsz
 */
@Data
public class LoginDTO implements Serializable {

    /** 用户名：@NotBlank 非空，@Size 长度 3-50 位 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在3-50个字符之间")
    private String username;

    /** 密码：@NotBlank 非空，@Size 长度 6-50 位 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度需在6-50个字符之间")
    private String password;

    /**
     * 验证码标识（由 GET /captcha 接口返回）。
     * 后端据此从 Redis 读取对应答案进行校验。
     */
    @NotBlank(message = "验证码标识不能为空")
    private String captchaId;

    /**
     * 验证码答案（用户输入）。
     * 与 Redis 中存储的答案进行字符串比对（忽略前后空白）。
     */
    @NotBlank(message = "验证码不能为空")
    private String captcha;

    /** 是否记住登录状态，默认 false */
    private Boolean remember = false;

}
