package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改密码 DTO（请求参数封装）。
 * 用于登录用户校验原密码后设置新密码的场景。
 *
 * @author hbsz
 */
@Data
public class ChangePasswordDTO implements Serializable {

    /** 原密码：@NotBlank 校验不能为空 */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 新密码：@NotBlank 非空，@Pattern 要求 8-32 位且包含大小写字母、数字和特殊字符 */
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[~!@#$%^&*()_+\\-={}|\\[\\]:\";'<>?,./]).{8,32}$",
            message = "密码长度8-32位，需包含大小写字母、数字和特殊字符")
    private String newPassword;

}
