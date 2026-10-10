package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 权限实体
 * <p>
 * 对应数据库表 sys_permission，描述菜单、按钮、接口等各类权限资源。
 * 通过 parentId 自关联形成树形结构实现权限继承；通过 sys_role_permission 与角色（sys_role）多对多关联。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父权限ID（0表示顶级） */
    private Long parentId;

    /** 权限名称 */
    private String permissionName;

    /** 权限标识，如 system:user:add */
    private String permissionCode;

    /** 权限类型：1菜单 2按钮 3接口 */
    private Integer permissionType;

    /** 前端路由路径 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 菜单图标 */
    private String icon;

    /** 排序 */
    private Integer sort;

    /** 是否可见：1是 0否 */
    private Integer isVisible;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

}
