package com.example.hbsz_idc_system_end.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 客户批量导入 DTO。
 * <p>
 * 字段与 {@link CustomerSaveDTO} 对应，供 EasyExcel 读取 Excel 使用。
 *
 * @author hbsz
 */
@Data
public class CustomerImportDTO implements Serializable {

    @ExcelProperty("客户编号")
    private String code;

    @ExcelProperty("客户名称")
    private String name;

    @ExcelProperty("联系人")
    private String contactPerson;

    @ExcelProperty("联系电话")
    private String contactPhone;

    @ExcelProperty("联系邮箱")
    private String contactEmail;

    @ExcelProperty("客户地址")
    private String address;

    @ExcelProperty("备注")
    private String remark;
}
