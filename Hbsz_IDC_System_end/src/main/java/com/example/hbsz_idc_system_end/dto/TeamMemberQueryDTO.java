package com.example.hbsz_idc_system_end.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 项目组成员查询 DTO
 * <p>
 * 用于分页查询项目组成员列表，支持按项目、用户、组内角色、是否在组筛选。
 *
 * @author hbsz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TeamMemberQueryDTO extends PageQuery implements Serializable {

    /** 项目ID（精确匹配） */
    private Long projectId;

    /** 成员用户ID（精确匹配） */
    private Long userId;

    /** 组内角色：leader / member / observer */
    private String roleInTeam;

    /** 是否在组：1是 0否，为 null 时不限制 */
    private Integer isActive;

}
