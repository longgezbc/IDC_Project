package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 材料实体。管理材料基础信息与当前库存数量。
 * @author hbsz
 */
@Data
@TableName("biz_material")
public class BizMaterial extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String category;
    /** 规格型号 */
    private String spec;
    private String unit;
    private BigDecimal currentStock;
    private BigDecimal safetyStock;
    private BigDecimal unitPrice;
    private String warehouse;
    private Integer status;
    private String remark;
}
