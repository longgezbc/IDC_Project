package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存变更记录（流水）。记录每次入库/出库/调整的前后库存与操作人。
 * @author hbsz
 */
@Data
@TableName("biz_material_transaction")
public class BizMaterialTransaction extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    /** in入库 / out出库 / adjust调整 */
    private String transactionType;
    private BigDecimal quantity;
    private BigDecimal beforeStock;
    private BigDecimal afterStock;
    private Long projectId;
    private String projectName;
    private Long operatorId;
    private String operatorName;
    private String remark;
}
