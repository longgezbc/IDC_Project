package com.example.hbsz_idc_system_end.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 项目授权申请查询 DTO
 * <p>
 * 用于分页查询项目授权申请列表，支持按状态、项目、申请人筛选。
 *
 * @author hbsz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthRequestQueryDTO extends PageQuery implements Serializable {

    /** 状态：pending 待审批 / approved 已通过 / rejected 已驳回 */
    private String status;

    /** 项目ID（精确匹配） */
    private Long projectId;

    /** 申请人ID（精确匹配） */
    private Long applicantId;

}
