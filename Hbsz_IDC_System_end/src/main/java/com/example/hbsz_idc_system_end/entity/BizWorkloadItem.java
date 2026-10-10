package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工作量清单实体
 * <p>
 * 对应数据库表 biz_workload_item。按项目维护的工作量作业目录，
 * 源自《工作量统计模板.xlsx》的行结构：作业类型（大类，如 24/48/96芯光缆）
 * × 作业范围（具体工序，如 光缆上架（30-60米）），并携带额定工作量（每人每日
 * 配额，作为绩效折算基数）、计量单位、绩效分权重与计划工作总量。
 * <p>
 * 已完成 / 剩余 / 今日完成量等统计指标由 biz_workload_record 实时聚合，不在本表冗余存储。
 *
 * @author hbsz
 */
@Data
@TableName("biz_workload_item")
public class BizWorkloadItem implements Serializable {

    /** 主键ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID（FK→biz_project.id） */
    private Long projectId;

    /** 作业类型（大类，如：24/48/96芯光缆） */
    private String category;

    /** 作业范围（具体工序，如：光缆上架（30-60米）） */
    private String workScope;

    /** 额定工作量（每人每日配额，绩效折算基数），null 视为 1 */
    private BigDecimal ratedWorkload;

    /** 计量单位（米/条/箱/芯/台/张/柜/组/个） */
    private String unit;

    /** 绩效分权重 */
    private BigDecimal performanceScore;

    /** 工作总量（计划工程量） */
    private BigDecimal totalQuantity;

    /** 备注 */
    private String remark;

    /** 排序号（模板行序） */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
