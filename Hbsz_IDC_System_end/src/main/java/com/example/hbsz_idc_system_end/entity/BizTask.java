package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 任务实体
 * <p>
 * 对应数据库表 biz_task，记录项目下的任务（WBS 工作分解），支持里程碑与关键任务标记。
 * projectId 关联项目（biz_project）；parentId 自关联形成任务树；assigneeId/createdBy 关联用户（sys_user）；
 * 进度变更明细记录于 biz_task_progress_log。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("biz_task")
public class BizTask extends BaseEntity {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目ID，关联 biz_project.id */
    private Long projectId;

    /** 任务名称 */
    private String name;

    /** 父任务ID，自关联 biz_task.id（顶级任务为空或0） */
    private Long parentId;

    /** 计划开始日期 */
    private LocalDate startDate;

    /** 计划结束日期 */
    private LocalDate endDate;

    /** 实际开始日期 */
    private LocalDate actualStartDate;

    /** 实际结束日期 */
    private LocalDate actualEndDate;

    /** 任务进度百分比（0-100） */
    private Integer progress;

    /** 任务状态：如 pending待处理/in_progress进行中/completed已完成 */
    private String status;

    /** 负责人ID，关联 sys_user.id */
    private Long assigneeId;

    /** 负责人姓名（冗余字段） */
    private String assigneeName;

    /** 是否里程碑：1是 0否 */
    private Integer isMilestone;

    /** 是否关键任务：1是 0否 */
    private Integer isKeyTask;

    /** 排序序号，值越小越靠前 */
    private Integer sort;

    /** 任务描述 */
    private String description;

    /** 备注 */
    private String remark;

    /** 创建人ID，关联 sys_user.id */
    private Long createdBy;

}
