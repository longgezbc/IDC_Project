package com.example.hbsz_idc_system_end.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户批量导入 DTO。
 * <p>
 * 字段与 {@link UserSaveDTO} 对应，供 EasyExcel 读取 Excel 使用。
 * positionCode/roleCode 由 Service 层查询数据库转换为 positionId/roleId。
 *
 * @author hbsz
 */
@Data
public class UserImportDTO implements Serializable {

    @ExcelProperty("用户名")
    private String username;

    @ExcelProperty("姓名")
    private String name;

    @ExcelProperty("密码")
    private String password;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("邮箱")
    private String email;

    @ExcelProperty("岗位编码")
    private String positionCode;

    @ExcelProperty("角色编码")
    private String roleCode;
}
