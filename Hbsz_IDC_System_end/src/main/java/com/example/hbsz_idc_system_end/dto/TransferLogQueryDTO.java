package com.example.hbsz_idc_system_end.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 项目转移日志查询条件 DTO
 * <p>
 * 支持按项目、操作人、操作类型、时间范围筛选审计日志。
 *
 * @author hbsz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TransferLogQueryDTO extends PageQuery implements Serializable {

    /** 项目ID（精确匹配） */
    private Long projectId;

    /** 操作人ID（精确匹配） */
    private Long operatedBy;

    /** 操作类型：assign / revoke / transfer */
    private String actionType;

    /** 起始时间（yyyy-MM-dd） */
    private String dateBegin;

    /** 结束时间（yyyy-MM-dd） */
    private String dateEnd;

}
