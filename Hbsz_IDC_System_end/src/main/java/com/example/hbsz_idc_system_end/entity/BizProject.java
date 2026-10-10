package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目实体
 * <p>
 * 对应数据库表 biz_project，是系统的核心业务实体，记录 IDC 工程项目的基本信息、进度与合同预算等。
 * customerId 关联客户（biz_customer），managerId/createdBy 关联用户（sys_user）；
 * 下辖任务（biz_task）、文档（biz_document）、设计图纸（biz_design_drawing）等。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("biz_project")
public class BizProject extends BaseEntity {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目编号（唯一） */
    private String projectCode;

    /** 项目名称 */
    private String name;

    /** 项目状态：如 pending待启动/in_progress进行中/completed已完成/paused暂停 */
    private String status;

    /** 优先级：如 high高/medium中/low低 */
    private String priority;

    /** 项目类型 */
    private String projectType;

    /** 客户ID，关联 biz_customer.id */
    private Long customerId;

    /** 项目经理ID，关联 sys_user.id */
    private Long managerId;

    /** 项目经理姓名（冗余字段） */
    private String managerName;

    /** 项目经理联系电话 */
    private String managerPhone;

    /** 项目地址 */
    private String address;

    /**
     * 项目地区（省/市/区，以 "/" 分隔）。
     * <p>
     * 前端用 el-cascader 级联选择器选择省/市/区，选中值为数组（如 ["广东省","深圳市","南山区"]），
     * 提交时 join("/") 存入本字段；回显时 split("/") 转回数组。
     * 与 address（详细地址）分离存储，便于按地区维度做项目统计。
     */
    private String region;

    /** 计划开始日期 */
    private LocalDate startDate;

    /** 计划结束日期 */
    private LocalDate endDate;

    /** 实际开始日期 */
    private LocalDate actualStartDate;

    /** 实际结束日期 */
    private LocalDate actualEndDate;

    /** 合同金额 */
    private BigDecimal contractAmount;

    /** 预算金额 */
    private BigDecimal budget;

    /** 项目进度百分比（0-100） */
    private Integer progress;

    /** 项目描述 */
    private String description;

    /** 是否已归档：1是 0否 */
    private Integer isArchived;

    /** 归档日期 */
    private LocalDate archiveDate;

    /** 创建人ID，关联 sys_user.id */
    private Long createdBy;

    /**
     * 乐观锁版本号（修复 #1：防止并发更新覆盖）
     * <p>
     * 配合 MyBatis-Plus {@link com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor} 使用。
     * 每次 updateById 时自动追加 {@code WHERE version = #{oldVersion}} 并 {@code SET version = #{oldVersion} + 1}，
     * 版本不匹配时更新影响行数为 0，业务层据此抛出友好提示。
     */
    @Version
    private Integer version;

}
