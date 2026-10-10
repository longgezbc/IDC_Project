package com.example.hbsz_idc_system_end.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志分页查询参数。
 * <p>
 * 支持按操作人（模糊）、模块、业务类型、执行状态与操作时间范围组合筛选，
 * 时间参数为 {@code yyyy-MM-dd} 字符串（ endDate 自动放大到当天 23:59:59 ），
 * 与 DailyReportQueryDTO 的字符串日期惯例保持一致。
 *
 * @author hbsz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogQueryDTO extends PageQuery {

    /** 操作人用户名（模糊匹配） */
    private String username;

    /** 操作模块（精确匹配，如：用户管理、项目管理） */
    private String module;

    /** 业务类型（精确匹配，如：新增、修改、删除、导出） */
    private String businessType;

    /** 执行状态：1成功 0失败；null 查全部 */
    private Integer status;

    /** 操作时间范围-开始（yyyy-MM-dd，含当天 00:00:00） */
    private String startDate;

    /** 操作时间范围-结束（yyyy-MM-dd，含当天 23:59:59） */
    private String endDate;
}
