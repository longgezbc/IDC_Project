package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 任务保存 DTO（请求参数封装，新建/更新共用）。
 * 用于提交项目任务的名称、时间、进度及负责人等信息。
 *
 * @author hbsz
 */
@Data
public class TaskSaveDTO implements Serializable {

    /** 任务名称：@NotBlank 校验不能为空 */
    @NotBlank(message = "任务名称不能为空")
    private String name;

    /** 父任务ID */
    private Long parentId;

    /** 开始日期：@NotNull 校验不能为空 */
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    /** 结束日期：@NotNull 校验不能为空 */
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    /** 进度百分比 */
    private Integer progress;

    /** 任务状态 */
    private String status;

    /** 负责人（显示名，兼容旧前端） */
    private String assignee;

    /**
     * 负责人用户ID：关联 sys_user.id，用于「我的任务」列表、到期通知、归档通知等场景。
     * 前端应从用户下拉选择负责人后传入对应用户的 id，否则 Dashboard/通知/定时任务将无法匹配用户。
     */
    private Long assigneeId;

    /** 是否里程碑 */
    private Boolean isMilestone;

    /** 是否关键任务 */
    private Boolean isKey;

    /** 排序 */
    private Integer sort;

    /** 任务描述 */
    private String description;

    /** 备注 */
    private String remark;

}