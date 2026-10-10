package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 客户实体
 * <p>
 * 对应数据库表 biz_customer，维护客户及其联系人信息，被项目（biz_project）通过 customerId 引用。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("biz_customer")
public class BizCustomer extends BaseEntity {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户编号（唯一） */
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

}
