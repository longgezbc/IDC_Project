package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 日报查询参数
 * <p>
 * 封装日报预览与导出时的查询条件，包括统计区间与导出格式。
 * 日期格式为 yyyy-MM-dd 字符串，由后端解析为 LocalDate。
 *
 * @author hbsz
 */
@Data
public class DailyReportQueryDTO {

    /** 统计区间开始日期（yyyy-MM-dd），必填 */
    @NotBlank(message = "开始日期不能为空")
    private String startDate;

    /** 统计区间结束日期（yyyy-MM-dd），必填 */
    @NotBlank(message = "结束日期不能为空")
    private String endDate;
}
