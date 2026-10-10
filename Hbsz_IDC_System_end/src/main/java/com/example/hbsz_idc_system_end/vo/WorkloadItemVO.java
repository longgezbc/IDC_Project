package com.example.hbsz_idc_system_end.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工作量清单项 VO（含实时聚合统计）。
 * <p>
 * 对应模板行：序号/作业类型/作业范围/额定工作量/单位/备注/工作总量/已完成/剩余/绩效分，
 * 其中 已完成/剩余/今日完成量 由填报记录实时聚合，不在清单表冗余存储。
 *
 * @author hbsz
 */
@Data
public class WorkloadItemVO {

    /** 清单项ID */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 作业类型（大类） */
    private String category;

    /** 作业范围（具体工序） */
    private String workScope;

    /** 额定工作量（每人每日配额） */
    private BigDecimal ratedWorkload;

    /** 计量单位 */
    private String unit;

    /** 绩效分权重 */
    private BigDecimal performanceScore;

    /** 工作总量（计划工程量） */
    private BigDecimal totalQuantity;

    /** 备注 */
    private String remark;

    /** 排序号 */
    private Integer sortOrder;

    /** 已完成量（全部日期累计，实时聚合） */
    private BigDecimal completedQuantity;

    /** 剩余工作量 = 工作总量 - 已完成（未设总量时为 null） */
    private BigDecimal remainingQuantity;

    /** 当日完成量（统计日期，实时聚合） */
    private BigDecimal todayQuantity;

}
