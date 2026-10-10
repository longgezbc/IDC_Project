package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 进度填报日志视图对象。
 * <p>
 * 用于审核列表与填报历史展示。包含任务名、项目名等冗余字段，便于前端直接渲染。
 *
 * @author hbsz
 */
@Data
public class ProgressLogVO implements Serializable {

    /** 日志ID */
    private String id;

    /** 任务ID */
    private String taskId;

    /** 任务名称（冗余） */
    private String taskName;

    /** 项目ID */
    private String projectId;

    /** 项目名称（冗余） */
    private String projectName;

    /** 本次填报后的进度百分比 */
    private Integer progress;

    /** 填报前的进度百分比 */
    private Integer previousProgress;

    /** 填报备注 */
    private String remark;

    /** 施工图片URL列表 */
    private List<String> images;

    /** 实际开工时间 */
    private LocalDate actualStartDate;

    /** 实际结束时间 */
    private LocalDate actualEndDate;

    /** 审核状态：pending / approved / rejected */
    private String reviewStatus;

    /** 审核人ID */
    private String reviewerId;

    /** 审核人姓名 */
    private String reviewerName;

    /** 审核时间 */
    private LocalDateTime reviewTime;

    /** 审核意见 */
    private String reviewRemark;

    /** 填报人ID */
    private String reporterId;

    /** 填报人姓名 */
    private String reporterName;

    /** 填报时间 */
    private LocalDateTime reportTime;

}