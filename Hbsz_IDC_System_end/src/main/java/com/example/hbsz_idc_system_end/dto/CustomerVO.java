package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户视图对象（VO）。
 * 在客户实体基础上扩展关联项目数 projectCount，便于列表/详情展示客户使用情况。
 *
 * @author hbsz
 */
@Data
public class CustomerVO implements Serializable {

    /** 客户ID */
    private Long id;

    /** 客户编号 */
    private String customerCode;

    /** 客户名称 */
    private String name;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 客户地址 */
    private String address;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 关联项目数（用于展示该客户名下有多少项目） */
    private Integer projectCount;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
