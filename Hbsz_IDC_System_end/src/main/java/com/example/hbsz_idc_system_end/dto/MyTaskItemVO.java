package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 我的任务列表项 VO
 * <p>
 * 在任务基础字段上扩展 projectName，用于工作台「我的任务」列表展示。
 *
 * @author hbsz
 */
@Data
public class MyTaskItemVO implements Serializable {

    /** 任务ID */
    private Long id;

    /** 任务名称 */
    private String name;

    /** 所属项目ID */
    private Long projectId;

    /** 所属项目名称 */
    private String projectName;

    /** 任务状态 */
    private String status;

    /** 进度百分比 */
    private Integer progress;

    /** 计划开始日期 */
    private LocalDate startDate;

    /** 计划结束日期 */
    private LocalDate endDate;

    /** 是否里程碑：1是 0否 */
    private Integer isMilestone;

    /** 是否关键任务：1是 0否 */
    private Integer isKeyTask;

}
