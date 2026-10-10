package com.example.hbsz_idc_system_end.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 项目授权关系查询 DTO
 * <p>
 * 用于分页查询项目-工程师授权关系列表（biz_project_assignment），
 * 支持按项目、工程师、有效状态、即将过期筛选。
 *
 * @author hbsz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AssignmentQueryDTO extends PageQuery implements Serializable {

    /** 项目ID（精确匹配） */
    private Long projectId;

    /** 工程师用户ID（精确匹配） */
    private Long engineerId;

    /** 是否有效：1有效 / 0已撤销，为 null 时不限制 */
    private Integer isActive;

    /** 即将过期筛选：true 仅查询 7 天内即将过期或已过期的有效授权 */
    private Boolean expireSoon;

}
