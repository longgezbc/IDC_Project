package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目维度材料出库流水 VO。
 * <p>
 * 用途：项目详情页"项目材料" tab，展示该项目领用的材料出库记录（去向追溯）。
 * 在 {@code BizMaterialTransaction} 基础上关联材料名称/规格/单位，前端无需二次查询。
 *
 * @author hbsz
 */
@Data
public class MaterialFlowVO implements Serializable {

    /** 流水ID */
    private Long id;

    /** 材料ID */
    private Long materialId;

    /** 材料名称 */
    private String materialName;

    /** 规格型号 */
    private String spec;

    /** 计量单位 */
    private String unit;

    /** 出库数量 */
    private BigDecimal quantity;

    /** 变更前库存 */
    private BigDecimal beforeStock;

    /** 变更后库存 */
    private BigDecimal afterStock;

    /** 去向项目ID */
    private Long projectId;

    /** 去向项目名称 */
    private String projectName;

    /** 操作人 */
    private String operatorName;

    /** 备注 */
    private String remark;

    /** 出库时间 */
    private LocalDateTime createdAt;
}
