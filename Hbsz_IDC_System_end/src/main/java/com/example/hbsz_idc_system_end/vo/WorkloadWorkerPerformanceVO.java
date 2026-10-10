package com.example.hbsz_idc_system_end.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 施工人员工作量绩效 VO（日期区间汇总）。
 * <p>
 * 对应模板"按人汇总"视角：区间完成总量、绩效分合计（Σ 数量÷额定×权重，
 * 即标准工日折算）、有效填报天数。
 *
 * @author hbsz
 */
@Data
public class WorkloadWorkerPerformanceVO {

    /** 施工人员姓名 */
    private String workerName;

    /** 区间完成总量（全部清单项合计） */
    private BigDecimal totalQuantity;

    /** 区间绩效分合计（标准工日折算） */
    private BigDecimal performanceScore;

    /** 有效填报天数（有填报记录的自然日数） */
    private Long recordDays;

    /** 日均绩效 = 绩效分合计 ÷ 有效填报天数（无记录时为 0） */
    private BigDecimal avgDailyPerformance;

}
