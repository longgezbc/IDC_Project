package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 客户保存 DTO（请求参数封装，新建/更新共用）。
 * 用于提交客户基础信息，必填项通过 jakarta.validation 注解校验。
 *
 * @author hbsz
 */
@Data
public class CustomerSaveDTO implements Serializable {

    /** 客户编号（唯一） */
    @NotBlank(message = "客户编号不能为空")
    @Size(max = 50, message = "客户编号长度不能超过50")
    private String code;

    /** 客户名称 */
    @NotBlank(message = "客户名称不能为空")
    @Size(max = 200, message = "客户名称长度不能超过200")
    private String name;

    /** 联系人 */
    @NotBlank(message = "联系人不能为空")
    @Size(max = 50, message = "联系人长度不能超过50")
    private String contactPerson;

    /** 联系电话（可选） */
    @Size(max = 20, message = "联系电话长度不能超过20")
    private String contactPhone;

    /** 联系邮箱（可选，校验格式） */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100")
    private String contactEmail;

    /** 客户地址 */
    @Size(max = 300, message = "客户地址长度不能超过300")
    private String address;

    /** 状态：1启用 0禁用，默认1 */
    private Integer status;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;

}
