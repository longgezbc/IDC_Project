package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 角色新增/修改 DTO（请求参数封装）。
 * 用于后台角色管理的创建与更新，可同时为角色分配权限。
 *
 * @author hbsz
 */
@Data
public class RoleSaveDTO implements Serializable {

    /** 角色名称 */
    private String roleName;

    /** 角色编码 */
    private String roleCode;

    /** 描述 */
    private String description;

    /** 排序 */
    private Integer sort;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /** 权限ID列表（修改时传入表示重新分配权限） */
    private List<Long> permissionIds;

}
