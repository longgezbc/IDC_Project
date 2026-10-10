package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 项目进度视图对象（响应结果封装）。
 * 用于向前端返回项目整体进度及其任务列表。
 *
 * @author hbsz
 */
@Data
public class ProjectProgressVO implements Serializable {

    /** 项目ID */
    private String projectId;

    /** 项目名称 */
    private String projectName;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 整体进度百分比 */
    private Integer overallProgress;

    /** 任务列表 */
    private List<TaskVO> tasks;

}
