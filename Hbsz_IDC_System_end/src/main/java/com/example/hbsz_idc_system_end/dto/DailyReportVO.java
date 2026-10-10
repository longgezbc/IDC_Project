package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 甘特图进度日报数据视图对象
 * <p>
 * 以甘特图任务进度为核心，封装以下数据板块：
 * <ul>
 *   <li>summary — 全局概览指标（项目数/任务数/延期数等）</li>
 *   <li>projectSummary — 各项目甘特图进度汇总（任务统计 + 延期统计）</li>
 *   <li>taskProgress — 任务级甘特图进度明细（计划/实际日期、进度、延期天数）</li>
 *   <li>projectId — 筛选的项目ID（null 表示全部项目）</li>
 * </ul>
 * 前端可直接渲染预览，后端导出 Excel 时也以此为数据源。
 * 导出 Excel 时，每个项目独占一个 Sheet，含项目信息头 + 任务明细表。
 *
 * @author hbsz
 */
@Data
public class DailyReportVO {

    /** 全局概览指标（totalProjects/totalTasks/completedTasks/inProgressTasks/delayedTasks/milestoneCount 等） */
    private Map<String, Object> summary;

    /** 项目级甘特图进度汇总列表 */
    private List<Map<String, Object>> projectSummary;

    /** 任务级甘特图进度明细列表 */
    private List<Map<String, Object>> taskProgress;

    /** 报告标题（含日期，用于导出文件内容头部） */
    private String reportTitle;

    /** 筛选的项目ID（null 表示全部项目；非空表示仅查询该项目） */
    private Long projectId;

    /** 筛选的项目名称（用于报告标题展示，projectId 非空时有值） */
    private String projectName;
}
