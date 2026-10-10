package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 任务视图对象（响应结果封装）。
 * 用于向前端返回项目任务/甘特图节点的详细信息。
 *
 * @author hbsz
 */
@Data
public class TaskVO implements Serializable {

    /** 任务ID */
    private String id;

    /** 所属项目ID */
    private String projectId;

    /** 任务名称 */
    private String name;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 实际开始日期 */
    private LocalDate actualStartDate;

    /** 实际结束日期 */
    private LocalDate actualEndDate;

    /** 进度百分比 */
    private Integer progress;

    /** 任务状态 */
    private String status;

    /** 负责人（显示名） */
    private String assignee;

    /** 负责人用户ID（关联 sys_user.id，用于编辑时回填下拉框） */
    private Long assigneeId;

    /** 是否里程碑 */
    private Boolean isMilestone;

    /** 是否关键任务 */
    private Boolean isKey;

    /** 父任务ID */
    private String parentId;

    /** 任务描述 */
    private String description;

    /** 备注 */
    private String remark;

}