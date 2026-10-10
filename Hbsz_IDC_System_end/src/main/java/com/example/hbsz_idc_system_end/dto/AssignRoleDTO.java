package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 用户角色分配 DTO（请求参数封装）。
 * 用于为指定用户全量重新分配角色。
 *
 * @author hbsz
 */
@Data
public class AssignRoleDTO implements Serializable {

    /** 角色ID列表（全量覆盖） */
    private List<Long> roleIds;

}
