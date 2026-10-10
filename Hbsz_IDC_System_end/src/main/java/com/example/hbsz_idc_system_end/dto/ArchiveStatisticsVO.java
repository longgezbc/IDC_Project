package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 归档统计视图对象（响应结果封装）。
 * 用于向前端返回已归档项目的数量与金额汇总。
 *
 * @author hbsz
 */
@Data
public class ArchiveStatisticsVO implements Serializable {

    /** 归档项目总数 */
    private Integer totalCount;

    /** 归档项目合同总金额 */
    private BigDecimal totalContractAmount;

    /** 本年度归档数 */
    private Integer currentYearCount;

    /** 本月归档数 */
    private Integer currentMonthCount;

}
