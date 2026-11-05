package com.example.hbsz_idc_system_end.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 材料批量导入 DTO（出库物料导入模板）。
 * <p>
 * 对应模板表头：标准型号 / 物料类型 / 厂商 / 当前状态 / 数量。
 * 模板中同型号多行（分批领用台账），导入时按型号合并累加数量。
 *
 * @author hbsz
 */
@Data
public class MaterialImportDTO implements Serializable {

    @ExcelProperty("标准型号")
    private String standardModel;

    @ExcelProperty("物料类型")
    private String materialType;

    @ExcelProperty("厂商")
    private String manufacturer;

    @ExcelProperty("当前状态")
    private String statusText;

    @ExcelProperty("数量")
    private BigDecimal quantity;
}
