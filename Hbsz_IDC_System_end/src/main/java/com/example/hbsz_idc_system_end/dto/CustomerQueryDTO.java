package com.example.hbsz_idc_system_end.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 客户查询 DTO（查询对象）。
 * 继承 PageQuery，用于客户列表的关键字、状态等条件分页查询。
 *
 * @author hbsz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerQueryDTO extends PageQuery implements Serializable {

    /** 关键字（按客户名称/编号/联系人/电话模糊查询） */
    private String keyword;

    /** 状态：1启用 0禁用 */
    private Integer status;

}
