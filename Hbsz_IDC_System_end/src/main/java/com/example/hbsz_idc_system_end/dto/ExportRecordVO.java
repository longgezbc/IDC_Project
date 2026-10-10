package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 导出历史记录视图对象
 * <p>
 * 对外暴露的导出记录信息，隐藏内部文件路径等敏感字段，
 * 供前端展示历史列表与重新下载使用。
 *
 * @author hbsz
 */
@Data
public class ExportRecordVO {

    /** 记录ID */
    private Long id;

    /** 导出人姓名 */
    private String userName;

    /** 报告类型：daily-日报 / weekly-周报 */
    private String reportType;

    /** 统计区间开始日期 */
    private LocalDate startDate;

    /** 统计区间结束日期 */
    private LocalDate endDate;

    /** 导出格式：xlsx / pdf */
    private String format;

    /** 文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件大小（人类可读，如 1.2 MB） */
    private String fileSizeText;

    /** 导出状态：success / failed */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
