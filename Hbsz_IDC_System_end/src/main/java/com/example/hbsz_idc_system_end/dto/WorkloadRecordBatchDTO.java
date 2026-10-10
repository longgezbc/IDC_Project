package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 工作量每日填报批量保存 DTO。
 * <p>
 * 对应模板"人员×作业范围"矩阵的一次性提交：同一项目、同一日期下的
 * 多条（清单项 × 人员 × 数量）明细。
 * <p>
 * 审核语义（2026-08-18）：保存后记录进入待审核（pending）状态；
 * 重复提交同一（项+日期+人员）：
 * <ul>
 *   <li>原记录为 pending / rejected：按更新覆盖并重置回 pending（重新审核）；</li>
 *   <li>原记录为 approved：拒绝修改（需项目经理驳回后重新填报）。</li>
 * </ul>
 *
 * @author hbsz
 */
@Data
public class WorkloadRecordBatchDTO {

    /** 项目ID */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 填报日期（工作量归属日） */
    @NotNull(message = "填报日期不能为空")
    private LocalDate recordDate;

    /** 填报明细列表 */
    @NotNull(message = "填报明细不能为空")
    @Size(max = 2000, message = "单次填报明细不能超过2000条")
    private List<@Valid RecordEntry> records;

    /**
     * 单条填报明细：清单项 × 人员 × 数量
     */
    @Data
    public static class RecordEntry {

        /** 工作量清单项ID */
        @NotNull(message = "清单项ID不能为空")
        private Long itemId;

        /** 施工人员用户ID（必须是项目组在组成员，姓名由后端解析为快照） */
        @NotNull(message = "施工人员不能为空")
        private Long workerUserId;

        /** 当日完成数量（0 表示清空该单元格） */
        @NotNull(message = "完成数量不能为空")
        @DecimalMin(value = "0", message = "完成数量不能为负数")
        private BigDecimal quantity;

    }

}
