package com.example.hbsz_idc_system_end.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 工作量统计矩阵 VO。
 * <p>
 * 数字化还原《工作量统计模板.xlsx》：行 = 清单项（含模板全部统计列），
 * 列 = 施工人员（当日有填报记录的人员自动汇聚成列），另附每人当日绩效合计
 * （对应模板 K2"今日绩效"行）。
 * <p>
 * 审核口径（2026-08-18）：
 * <ul>
 *   <li>items 聚合列（已完成/今日量）与 workerPerformance 仅统计 approved 记录；</li>
 *   <li>matrix 返回全部状态单元格（含 pending/rejected），供前端编辑回显与状态着色；</li>
 *   <li>workers 为当日有填报记录的人员（含历史自由文本记录，userId 可为 null）。</li>
 * </ul>
 *
 * @author hbsz
 */
@Data
public class WorkloadMatrixVO {

    /** 项目ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** 统计日期 */
    private LocalDate statisticsDate;

    /** 清单行（含已完成/剩余/今日量聚合，按 sortOrder 排序，仅统计已审核记录） */
    private List<WorkloadItemVO> items;

    /** 当日人员列（组长置前，其余按姓名排序），用于矩阵与 Excel 动态表头 */
    private List<WorkloadWorkerVO> workers;

    /** 矩阵明细：itemId → (workerName → 单元格)，仅含有值单元格（含各审核状态） */
    private Map<Long, Map<String, WorkloadCellVO>> matrix;

    /** 每人当日绩效合计（仅统计已审核记录）：workerName → Σ(数量÷额定×绩效权重) */
    private Map<String, BigDecimal> workerPerformance;

    /** 当日待审核单元格数（供汇总卡片展示） */
    private Long pendingCount;

}
