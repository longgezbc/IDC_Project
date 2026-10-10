package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 项目保存 DTO（请求参数封装，新建/更新共用）。
 * 用于提交项目基础信息、客户信息、合同与设计图纸等数据。
 *
 * @author hbsz
 */
@Data
public class ProjectSaveDTO implements Serializable {

    /** 项目名称：@NotBlank 校验不能为空 */
    @NotBlank(message = "项目名称不能为空")
    private String name;

    /** 项目编号：@NotBlank 校验不能为空 */
    @NotBlank(message = "项目编号不能为空")
    private String code;

    /** 项目类型：@NotBlank 校验不能为空 */
    @NotBlank(message = "项目类型不能为空")
    private String type;

    /** 优先级：@NotBlank 校验不能为空 */
    @NotBlank(message = "优先级不能为空")
    private String priority;

    /** 项目状态 */
    private String status;

    /** 开始日期：@NotNull 校验不能为空 */
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    /** 结束日期：@NotNull 校验不能为空 */
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    /** 合同金额：@NotNull 校验不能为空 */
    @NotNull(message = "合同金额不能为空")
    private BigDecimal contractAmount;

    /** 预算金额 */
    private BigDecimal budget;

    /** 项目地址 */
    private String address;

    /** 项目地区（省/市/区，以 "/" 分隔，由前端 el-cascader 选中值 join("/") 生成） */
    private String region;

    /** 项目描述 */
    private String description;

    /** 客户ID */
    private String customerId;

    /** 客户名称：@NotBlank 校验不能为空 */
    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    /** 客户联系人：@NotBlank 校验不能为空 */
    @NotBlank(message = "客户联系人不能为空")
    private String customerContact;

    /** 客户联系电话：@NotBlank 校验不能为空 */
    @NotBlank(message = "客户联系电话不能为空")
    private String customerPhone;

    /** 客户邮箱 */
    private String customerEmail;

    /** 项目经理：@NotBlank 校验不能为空 */
    @NotBlank(message = "项目经理不能为空")
    private String manager;

    /**
     * 项目经理用户ID：关联 sys_user.id，用于 PM 工作台统计、风险看板、进度审核通知等场景。
     * 前端应从用户下拉选择 PM 后传入对应用户的 id（而非仅传姓名），否则 Dashboard 统计/通知推送将失效。
     */
    private Long managerId;

    /** 项目经理电话 */
    private String managerPhone;

    /** 进度百分比 */
    private Integer progress;

    /** 设计图纸列表 */
    private List<DesignDrawingDTO> designDrawings;

    /**
     * 设计图纸子DTO
     */
    @Data
    public static class DesignDrawingDTO implements Serializable {
        private Long id;
        private String name;
        private String type;
        private String uploadDate;
        private String size;
    }

}
