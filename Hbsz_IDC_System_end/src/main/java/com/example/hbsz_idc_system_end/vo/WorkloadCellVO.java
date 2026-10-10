package com.example.hbsz_idc_system_end.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工作量统计矩阵单元格视图对象。
 * <p>
 * 对应"人员×清单项"矩阵的一个单元格：完成数量 + 审核状态。
 * 前端依据 reviewStatus 渲染：approved 单元格锁定不可编辑、
 * pending 橙色待审、rejected 红色可重新填报（附驳回原因 tooltip）。
 *
 * @author hbsz
 */
@Data
public class WorkloadCellVO implements Serializable {

    /** 当日完成数量 */
    private BigDecimal quantity;

    /** 审核状态：pending/approved/rejected */
    private String reviewStatus;

    /** 审核意见（驳回原因，供 tooltip 展示） */
    private String reviewRemark;

}
