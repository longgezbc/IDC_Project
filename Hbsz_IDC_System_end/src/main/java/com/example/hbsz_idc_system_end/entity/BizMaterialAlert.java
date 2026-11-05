package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存预警记录。库存低于安全线时生成，处理后标记 resolved。
 * @author hbsz
 */
@Data
@TableName("biz_material_alert")
public class BizMaterialAlert extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    private String materialName;
    private BigDecimal currentStock;
    private BigDecimal safetyStock;
    /** pending待处理 / resolved已处理 */
    private String status;
    private LocalDateTime resolvedAt;
}
