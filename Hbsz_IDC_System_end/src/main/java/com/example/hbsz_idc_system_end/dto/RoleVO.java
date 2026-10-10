package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色视图对象（响应结果封装）。
 * 用于向前端返回角色基本信息及其权限、用户数量统计。
 *
 * @author hbsz
 */
@Data
public class RoleVO implements Serializable {

    /** 角色ID */
    private Long id;

    /** 角色名称 */
    private String roleName;

    /** 角色编码 */
    private String roleCode;

    /** 角色描述 */
    private String description;

    /** 排序 */
    private Integer sort;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 权限ID列表 */
    private List<Long> permissionIds;

    /** 用户数量 */
    private Integer userCount;

}
