package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限视图对象（响应结果封装，含树形结构）。
 * 用于向前端返回权限菜单/按钮/接口的层级数据。
 *
 * @author hbsz
 */
@Data
public class PermissionVO implements Serializable {

    /** 权限ID */
    private Long id;

    /** 父权限ID（0表示顶级） */
    private Long parentId;

    /** 权限名称 */
    private String permissionName;

    /** 权限标识 */
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

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 子权限列表（树形结构） */
    private List<PermissionVO> children;

}
