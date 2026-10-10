package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 登录响应视图对象（响应结果封装）。
 * 用于登录成功后返回访问令牌、刷新令牌及用户基本信息。
 *
 * @author hbsz
 */
@Data
public class LoginVO implements Serializable {

    /** 访问令牌 */
    private String token;

    /** 刷新令牌 */
    private String refreshToken;

    /** 令牌有效期（秒） */
    private Integer expiresIn;

    /** 登录用户基本信息 */
    private UserInfoVO userInfo;

    /**
     * 用户基本信息
     */
    @Data
    public static class UserInfoVO implements Serializable {
        private Long id;
        private String username;
        private String name;
        private String avatar;
        private List<String> roles;
        private List<String> permissions;
    }

}
