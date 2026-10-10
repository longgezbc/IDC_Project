package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理视图对象（响应结果封装）。
 * 用于后台用户列表/详情展示，包含用户基本信息与所属角色。
 *
 * @author hbsz
 */
@Data
public class UserManageVO implements Serializable {

    /** 用户ID */
    private Long id;

    /** 用户名 */
    private String username;

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

    /**
     * 部门ID（旧字段，保留兼容）
     * @deprecated 已由 {@link #deptId} 替代，待前端代码清理后删除。新代码请使用 {@link #deptId}。
     */
    @Deprecated
    private Long departmentId;

    /**
     * 职位（文本描述，旧字段，保留兼容）
     * @deprecated 已由 {@link #positionId} 替代，应通过 {@link #positionId} 关联 sys_position.name
     *             获取规范化的岗位名称。待前端代码清理后删除。
     */
    @Deprecated
    private String position;

    /** 岗位ID（关联 sys_position.id） */
    private Long positionId;

    /** 岗位编码（用于前端展示岗位标识） */
    private String positionCode;

    /** 岗位名称（用于前端展示） */
    private String positionName;

    /** 部门ID（预留字段，当前不启用部门隔离） */
    private Long deptId;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 最后登录IP */
    private String lastLoginIp;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 角色ID列表 */
    private List<Long> roleIds;

    /** 角色编码列表 */
    private List<String> roleCodes;

    /** 角色名称列表 */
    private List<String> roleNames;

}
