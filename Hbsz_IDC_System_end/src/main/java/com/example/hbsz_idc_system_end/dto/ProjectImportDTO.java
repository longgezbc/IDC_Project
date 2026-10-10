package com.example.hbsz_idc_system_end.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 项目批量导入 DTO。
 * <p>
 * 字段与 {@link ProjectSaveDTO} 对应，供 EasyExcel 读取 Excel 使用。
 * 日期、金额等字段统一使用字符串/BigDecimal 接收，由 Service 层做类型转换。
 *
 * @author hbsz
 */
@Data
public class ProjectImportDTO implements Serializable {

    @ExcelProperty("项目编号")
    private String code;

    @ExcelProperty("项目名称")
    private String name;

    @ExcelProperty("项目类型")
    private String type;

    @ExcelProperty("优先级")
    private String priority;

    @ExcelProperty("客户名称")
    private String customerName;

    @ExcelProperty("客户联系人")
    private String customerContact;

    @ExcelProperty("客户联系电话")
    private String customerPhone;

    @ExcelProperty("客户邮箱")
    private String customerEmail;

    @ExcelProperty("项目经理姓名")
    private String manager;

    @ExcelProperty("开始日期(yyyy-MM-dd)")
    private String startDate;

    @ExcelProperty("结束日期(yyyy-MM-dd)")
    private String endDate;

    @ExcelProperty("合同金额")
    private BigDecimal contractAmount;

    @ExcelProperty("项目地址")
    private String address;

    @ExcelProperty("项目描述")
    private String description;
}
