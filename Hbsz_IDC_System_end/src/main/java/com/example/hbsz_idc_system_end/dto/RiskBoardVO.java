package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 风险预警看板 VO（PM 视角）
 * <p>
 * 聚合项目经理所负责项目的风险信息：逾期任务、即将到期任务、待审核填报数、
 * 各项目风险等级。仅 PM 角色使用，admin 也可访问。
 *
 * @author hbsz
 */
@Data
public class RiskBoardVO implements Serializable {

    /** 统计范围项目数（PM 负责的项目数） */
    private Integer totalProjects;

    /** 逾期任务数 */
    private Integer overdueTaskCount;

    /** 即将到期任务数（3 天内到期） */
    private Integer upcomingTaskCount;

    /** 待审核填报数（PM 负责项目下的 pending 日志） */
    private Integer pendingReviewCount;

    /** 关键节点逾期数（is_key_task=1 且逾期） */
    private Integer overdueKeyTaskCount;

    /** 风险等级：low(无风险) / medium(中等) / high(高风险) */
    private String riskLevel;

    /** 逾期任务明细列表（取前 20 条） */
    private List<RiskTaskItem> overdueTasks;

    /** 即将到期任务明细列表（取前 20 条） */
    private List<RiskTaskItem> upcomingTasks;

    /** 各项目风险摘要 */
    private List<ProjectRiskItem> projectRisks;

    /**
     * 风险任务明细项
     */
    @Data
    public static class RiskTaskItem implements Serializable {
        /** 任务ID */
        private Long taskId;
        /** 任务名称 */
        private String taskName;
        /** 所属项目ID */
        private Long projectId;
        /** 所属项目名称 */
        private String projectName;
        /** 任务状态 */
        private String status;
        /** 进度百分比 */
        private Integer progress;
        /** 计划结束日期 */
        private LocalDate endDate;
        /** 是否关键任务：1是 0否 */
        private Integer isKeyTask;
        /** 是否里程碑：1是 0否 */
        private Integer isMilestone;
        /** 负责人ID */
        private Long assigneeId;
        /** 负责人姓名 */
        private String assigneeName;
        /** 风险类型：overdue(逾期) / upcoming(即将到期) */
        private String riskType;
        /** 逾期天数（仅 overdue 类型有值，正数表示逾期天数） */
        private Integer overdueDays;
    }

    /**
     * 项目风险摘要项
     */
    @Data
    public static class ProjectRiskItem implements Serializable {
        /** 项目ID */
        private Long projectId;
        /** 项目名称 */
        private String projectName;
        /** 项目下总任务数 */
        private Integer totalTasks;
        /** 逾期任务数 */
        private Integer overdueTasks;
        /** 即将到期任务数 */
        private Integer upcomingTasks;
        /** 关键节点逾期数 */
        private Integer overdueKeyTasks;
        /** 待审核填报数 */
        private Integer pendingReviews;
        /** 项目风险等级：low / medium / high */
        private String riskLevel;
    }
}