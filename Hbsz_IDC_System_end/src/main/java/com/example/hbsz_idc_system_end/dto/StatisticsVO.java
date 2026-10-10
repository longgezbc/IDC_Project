package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 统计分析视图对象（响应结果封装）。
 * 用于向前端返回项目总量、金额及各维度分布与月度趋势等统计数据。
 *
 * @author hbsz
 */
@Data
public class StatisticsVO implements Serializable {

    /** 项目总数 */
    private Integer totalProjects;

    /** 进行中项目数 */
    private Integer activeProjects;

    /** 已完成项目数 */
    private Integer completedProjects;

    /** 已归档项目数 */
    private Integer archivedProjects;

    /** 合同总金额 */
    private BigDecimal totalContractAmount;

    /** 平均进度 */
    private Integer avgProgress;

    /** 按状态分布 */
    private List<Map<String, Object>> statusDistribution;

    /** 按类型分布 */
    private List<Map<String, Object>> typeDistribution;

    /** 按优先级分布 */
    private List<Map<String, Object>> priorityDistribution;

    /** 月度趋势 */
    private List<Map<String, Object>> monthlyTrend;

}
