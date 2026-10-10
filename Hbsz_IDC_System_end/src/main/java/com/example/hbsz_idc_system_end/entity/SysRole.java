package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色实体
 * <p>
 * 对应数据库表 sys_role，定义系统中的角色。角色是用户与权限之间的桥梁：
 * 通过 sys_user_role 与用户（sys_user）多对多关联，通过 sys_role_permission 与权限（sys_permission）多对多关联。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色名称 */
    private String roleName;

    /** 角色编码（唯一标识，如 admin） */
    private String roleCode;

    /** 角色描述 */
    private String description;

    /** 排序序号，值越小越靠前 */
    private Integer sort;

    /** 状态：1启用 0禁用 */
    private Integer status;

}
