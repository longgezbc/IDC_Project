package com.example.hbsz_idc_system_end.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 用户分页查询参数（查询对象）。
 * 继承 PageQuery，用于后台用户列表的条件分页查询。
 *
 * @author hbsz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageQuery implements Serializable {

    /** 用户名（模糊查询） */
    private String username;

    /** 姓名（模糊查询） */
    private String name;

    /** 手机号（模糊查询） */
    private String phone;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /**
     * 部门ID（旧字段）
     * @deprecated 已由 {@link #deptId} 替代，待前端代码清理后删除。新代码请使用 {@link #deptId}。
     */
    @Deprecated
    private Long departmentId;

    /** 部门ID（关联 sys_department.id，新部门体系外键） */
    private Long deptId;

    /** 角色ID */
    private Long roleId;

}
