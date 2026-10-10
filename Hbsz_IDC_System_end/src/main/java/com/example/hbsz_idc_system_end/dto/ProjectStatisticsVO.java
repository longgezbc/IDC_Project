package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 项目统计概览视图对象（响应结果封装）。
 * 用于向前端返回项目数量、金额、文档与关键任务等概览数据。
 *
 * @author hbsz
 */
@Data
public class ProjectStatisticsVO implements Serializable {

    /** 进行中项目数 */
    private Integer activeCount;

    /** 已完成项目数 */
    private Integer completedCount;

    /** 已归档项目数 */
    private Integer archivedCount;

    /** 项目总数 */
    private Integer totalCount;

    /** 合同总金额 */
    private BigDecimal totalContractAmount;

    /** 平均进度 */
    private Integer avgProgress;

    /** 文档总数 */
    private Integer documentCount;

    /** 即将到期的关键任务数 */
    private Integer upcomingKeyTaskCount;

}