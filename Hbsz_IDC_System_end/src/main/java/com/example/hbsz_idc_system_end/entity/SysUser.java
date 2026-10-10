package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户实体
 * <p>
 * 对应数据库表 sys_user，记录系统登录账号及其个人资料、状态等信息。
 * 通过 sys_user_role 关联表与角色（sys_role）建立多对多关系实现权限分配；
 * deptId 关联所属部门，positionId 关联岗位。
 * </p>
 *
 * @author hbsz
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名（唯一） */
    private String username;

    /** 登录密码（加密存储） */
    private String password;

    /** 真实姓名 */
    private String name;

    /** 昵称 */
    private String nickname;

    /** 性别：1男 2女 0未知 */
    private Integer gender;

    /** 头像地址 */
    private String avatar;

    /** 电子邮箱 */
    private String email;

    /** 手机号码 */
    private String phone;

    /** 岗位ID（关联 sys_position.id） */
    private Long positionId;

    /** 部门ID（关联 sys_department.id） */
    private Long deptId;

    /** 个人简介 */
    private String bio;

    /** 是否开启双因子认证：1开启 0关闭 */
    private Integer twoFactorEnabled;

    /** 账号状态：1启用 0禁用 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 最后登录IP */
    private String lastLoginIp;

}
