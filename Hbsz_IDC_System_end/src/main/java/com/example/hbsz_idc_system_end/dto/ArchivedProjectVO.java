package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 归档项目视图对象（响应结果封装）。
 * 用于向前端展示已归档项目的列表/详情信息。
 *
 * @author hbsz
 */
@Data
public class ArchivedProjectVO implements Serializable {

    /** 项目ID */
    private String id;

    /** 项目名称 */
    private String name;

    /** 项目编号 */
    private String code;

    /** 项目状态 */
    private String status;

    /** 优先级 */
    private String priority;

    /** 项目类型 */
    private String type;

    /** 客户名称 */
    private String customerName;

    /** 项目经理 */
    private String manager;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 归档日期 */
    private LocalDate archiveDate;

    /** 合同金额 */
    private BigDecimal contractAmount;

    /** 进度百分比 */
    private Integer progress;

    /** 项目描述 */
    private String description;

}
