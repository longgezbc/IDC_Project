package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 个人资料更新 DTO（请求参数封装）。
 * 用于登录用户在个人中心修改自身资料。
 *
 * @author hbsz
 */
@Data
public class UserProfileDTO implements Serializable {

    /** 姓名 */
    private String name;

    /** 昵称 */
    private String nickname;

    /** 性别：0女 1男 */
    private Integer gender;

    /** 头像 */
    private String avatar;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 职位 */
    private String position;

    /** 个人简介 */
    private String bio;

}
