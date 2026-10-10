package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 项目详情视图对象（响应结果封装）。
 * 用于向前端返回项目完整信息，对应前端项目对象结构。
 *
 * @author hbsz
 */
@Data
public class ProjectVO implements Serializable {

    /** 项目ID */
    private String id;

    /** 项目名称 */
    private String name;

    /** 项目编号 */
    private String code;

    /** 项目状态 */
    private String status;

    /** 优先级 */
    private String priority;

    /** 项目类型 */
    private String type;

    /** 客户ID */
    private String customerId;

    /** 客户名称 */
    private String customerName;

    /** 客户联系人 */
    private String customerContact;

    /** 客户联系电话 */
    private String customerPhone;

    /** 客户邮箱 */
    private String customerEmail;

    /** 项目经理 */
    private String manager;

    /** 项目经理关联用户ID（biz_project.manager_id，审核通知/审批归属的实际指向） */
    private Long managerId;

    /** manager_id 对应用户姓名（未关联时 null）；供前端比对 manager 文本是否分叉 */
    private String managerLinkedName;

    /** 负责人关联状态：ok=一致 / mismatch=姓名与关联用户不一致（审批归属分叉） /
     *  unlinked=有姓名未关联用户（通知断链） / disabled=关联用户已停用 / missing=关联用户不存在 */
    private String managerLinkStatus;

    /** 项目经理电话 */
    private String managerPhone;

    /** 项目地址 */
    private String address;

    /** 项目地区（省/市/区，以 "/" 分隔） */
    private String region;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 合同金额 */
    private BigDecimal contractAmount;

    /** 预算金额 */
    private BigDecimal budget;

    /** 进度百分比 */
    private Integer progress;

    /** 项目描述 */
    private String description;

    /** 设计图纸列表 */
    private List<DesignDrawingVO> designDrawings;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

    /** 是否已归档 */
    private Boolean archived;

    /**
     * 设计图纸VO
     */
    @Data
    public static class DesignDrawingVO implements Serializable {
        private String id;
        private String name;
        private String type;
        private String uploadDate;
        private String size;
    }

}
