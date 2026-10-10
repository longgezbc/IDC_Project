package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务进度填报记录实体
 * <p>
 * 对应数据库表 biz_task_progress_log，记录任务每次进度上报的流水，用于进度追溯与统计。
 * taskId 关联任务（biz_task），projectId 关联项目（biz_project），reporterId 关联填报人（sys_user）。
 * </p>
 * <p>
 * 审核流程字段说明（2026-07-04 新增）：
 * <ul>
 *   <li>images：施工图片URL列表（JSON数组），由文件存储服务返回的相对路径组成；</li>
 *   <li>reviewStatus：审核状态，pending（待审核）/ approved（已通过）/ rejected（已驳回）；
 *       工程师填报后默认 pending，项目经理审核通过后才会回写到 biz_task.progress；</li>
 *   <li>reviewerId / reviewerName / reviewTime / reviewRemark：审核人信息与意见。</li>
 * </ul>
 *
 * @author hbsz
 */
@Data
@TableName("biz_task_progress_log")
public class BizTaskProgressLog implements Serializable {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务ID，关联 biz_task.id */
    private Long taskId;

    /** 所属项目ID，关联 biz_project.id */
    private Long projectId;

    /** 本次填报后的进度百分比（0-100） */
    private Integer progress;

    /** 填报前的进度百分比（0-100） */
    private Integer previousProgress;

    /** 填报备注 */
    private String remark;

    /** 施工图片URL列表（JSON数组字符串，如 ["/uploads/2026/07/xxx.jpg"]） */
    private String images;

    /** 实际开工时间（工程师填报） */
    private LocalDate actualStartDate;

    /** 实际结束时间（工程师填报） */
    private LocalDate actualEndDate;

    /** 审核状态：pending（待审核）/ approved（已通过）/ rejected（已驳回） */
    private String reviewStatus;

    /** 审核人ID，关联 sys_user.id */
    private Long reviewerId;

    /** 审核人姓名（冗余字段） */
    private String reviewerName;

    /** 审核时间 */
    private LocalDateTime reviewTime;

    /** 审核意见 */
    private String reviewRemark;

    /** 填报人ID，关联 sys_user.id */
    private Long reporterId;

    /** 填报人姓名（冗余字段） */
    private String reporterName;

    /** 填报时间 */
    private LocalDateTime reportTime;

}
