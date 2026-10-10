package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 密码重置 DTO（请求参数封装）。
 * 用于管理员为指定用户重置登录密码的场景。
 *
 * @author hbsz
 */
@Data
public class ResetPasswordDTO implements Serializable {

    /** 新密码：@NotBlank 非空，@Pattern 要求 8-32 位且包含大小写字母、数字和特殊字符 */
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[~!@#$%^&*()_+\\-={}|\\[\\]:\";'<>?,./]).{8,32}$",
            message = "密码长度8-32位，需包含大小写字母、数字和特殊字符")
    private String newPassword;

}
