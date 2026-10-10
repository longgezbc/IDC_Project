package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 角色权限分配 DTO（请求参数封装）。
 * 用于为指定角色全量重新分配权限。
 *
 * @author hbsz
 */
@Data
public class AssignPermissionDTO implements Serializable {

    /** 权限ID列表（全量覆盖） */
    private List<Long> permissionIds;

}
