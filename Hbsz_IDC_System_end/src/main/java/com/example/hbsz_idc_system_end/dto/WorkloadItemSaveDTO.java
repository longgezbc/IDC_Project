package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工作量清单项保存 DTO（新增/编辑共用）。
 * <p>
 * 对应模板行结构：作业类型 + 作业范围 + 额定工作量 + 单位 + 绩效分 + 工作总量。
 *
 * @author hbsz
 */
@Data
public class WorkloadItemSaveDTO {

    /** 项目ID */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 作业类型（大类，如：24/48/96芯光缆） */
    @NotBlank(message = "作业类型不能为空")
    @Size(max = 100, message = "作业类型长度不能超过100字符")
    private String category;

    /** 作业范围（具体工序） */
    @NotBlank(message = "作业范围不能为空")
    @Size(max = 200, message = "作业范围长度不能超过200字符")
    private String workScope;

    /** 额定工作量（每人每日配额），缺省 1 */
    @NotNull(message = "额定工作量不能为空")
    @DecimalMin(value = "0.01", message = "额定工作量必须大于0")
    private BigDecimal ratedWorkload;

    /** 计量单位 */
    @NotBlank(message = "单位不能为空")
    @Size(max = 20, message = "单位长度不能超过20字符")
    private String unit;

    /** 绩效分权重，缺省 1 */
    @DecimalMin(value = "0", message = "绩效分权重不能为负数")
    private BigDecimal performanceScore;

    /** 工作总量（计划工程量），缺省 0 */
    @DecimalMin(value = "0", message = "工作总量不能为负数")
    private BigDecimal totalQuantity;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过255字符")
    private String remark;

    /** 排序号 */
    private Integer sortOrder;

}
