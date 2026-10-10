package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 用户新增/修改 DTO（请求参数封装）。
 * 用于后台用户管理的创建与更新，通过 Create/Update 校验分组区分场景。
 *
 * @author hbsz
 */
@Data
public class UserSaveDTO implements Serializable {

    /** 用户名（新增时必填，3-20位字母数字下划线） */
    @NotBlank(message = "用户名不能为空", groups = {Create.class})
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "用户名只能包含字母、数字、下划线，长度3-20位", groups = {Create.class, Update.class})
    private String username;

    /** 密码（新增时必填，修改时为空表示不修改；最小8位，需包含大小写字母、数字和特殊字符） */
    @NotBlank(message = "密码不能为空", groups = {Create.class})
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[~!@#$%^&*()_+\\-={}|\\[\\]:\";'<>?,./]).{8,32}$",
            message = "密码长度8-32位，需包含大小写字母、数字和特殊字符", groups = {Create.class, Update.class})
    private String password;

    /** 姓名 */
    @NotBlank(message = "姓名不能为空", groups = {Create.class, Update.class})
    @Size(max = 50, message = "姓名长度不能超过50个字符", groups = {Create.class, Update.class})
    private String name;

    /** 昵称 */
    @Size(max = 50, message = "昵称长度不能超过50个字符", groups = {Create.class, Update.class})
    private String nickname;

    /** 性别：0女 1男 */
    private Integer gender;

    /** 头像 */
    private String avatar;

    /** 邮箱 */
    @Email(message = "邮箱格式不正确", groups = {Create.class, Update.class})
    @Size(max = 100, message = "邮箱长度不能超过100个字符", groups = {Create.class, Update.class})
    private String email;

    /** 手机号 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确", groups = {Create.class, Update.class})
    private String phone;

    /**
     * 部门ID（旧字段）
     * @deprecated 已由 {@link #deptId} 替代，待前端代码清理后删除。新代码请使用 {@link #deptId}。
     */
    @Deprecated
    private Long departmentId;

    /** 部门ID（关联 sys_department.id，新部门体系外键） */
    private Long deptId;

    /** 职位（文本描述，旧字段） */
    @Size(max = 50, message = "职位长度不能超过50个字符", groups = {Create.class, Update.class})
    private String position;

    /** 岗位ID（关联 sys_position.id，新岗位职级体系） */
    private Long positionId;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /** 角色ID列表 */
    private List<Long> roleIds;

    /**
     * 新增校验分组
     */
    public interface Create {}

    /**
     * 修改校验分组
     */
    public interface Update {}

}
