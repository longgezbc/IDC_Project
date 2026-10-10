package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户信息视图对象（响应结果封装）。
 * 用于向前端返回登录用户的基本资料、角色与权限。
 *
 * @author hbsz
 */
@Data
public class UserInfoVO implements Serializable {

    /** 用户ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 姓名 */
    private String name;

    /** 头像 */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 所属部门 */
    private String department;

    /** 职位 */
    private String position;

    /** 个人简介 */
    private String bio;

    /** 角色编码列表 */
    private java.util.List<String> roles;

    /** 权限标识列表 */
    private java.util.List<String> permissions;

}
