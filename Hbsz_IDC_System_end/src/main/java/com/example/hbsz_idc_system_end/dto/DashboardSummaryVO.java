package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作台汇总数据 VO
 * <p>
 * 按当前登录用户的角色聚合统计：我的任务数、我的填报数、待我审核数等。
 * 前端根据 role 字段决定展示哪些卡片。
 *
 * @author hbsz
 */
@Data
public class DashboardSummaryVO implements Serializable {

    /** 当前用户主角色（admin/project_manager/engineer/viewer 等） */
    private String role;

    /** 我的任务总数（分配给当前用户的任务） */
    private Integer myTaskCount;

    /** 我的未完成任务数（状态非"已完成"） */
    private Integer myPendingTaskCount;

    /** 我的已逾期任务数（endDate < 今天 且未完成） */
    private Integer myOverdueTaskCount;

    /** 我的即将到期任务数（endDate 在未来3天内且未完成） */
    private Integer myUpcomingTaskCount;

    /** 我的填报总数（提交的进度日志总数） */
    private Integer mySubmissionCount;

    /** 我的待审核填报数 */
    private Integer myPendingSubmissionCount;

    /** 我的已通过填报数 */
    private Integer myApprovedSubmissionCount;

    /** 我的已驳回填报数 */
    private Integer myRejectedSubmissionCount;

    /** 待我审核的进度数（项目经理专属：我负责的项目下的 pending 日志数） */
    private Integer pendingReviewCount;

    /** 我负责的项目数（项目经理专属） */
    private Integer managedProjectCount;

    /** 未读通知数 */
    private Integer unreadNotificationCount;

}