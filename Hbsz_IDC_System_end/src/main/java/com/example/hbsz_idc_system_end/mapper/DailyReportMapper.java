package com.example.hbsz_idc_system_end.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 日报数据采集 Mapper（甘特图进度专用）。
 * <p>
 * 以 biz_task 为核心，JOIN biz_project 获取项目名称，
 * 查询任务的计划/实际日期、进度百分比、延期情况等甘特图关键指标。
 * 所有查询支持按 projectId 可选过滤，实现"每个项目每个任务的施工日报"。
 * 注：biz_task.status 实际存储中文枚举值（已完成/进行中/未开始），SQL 必须使用中文匹配。
 *
 * @author hbsz
 */
@Mapper
public interface DailyReportMapper {

    /**
     * 查询甘特图任务进度明细（支持按项目筛选）。
     * <p>
     * JOIN biz_project 获取项目名称与经理，返回每个任务的计划/实际日期、进度、状态、延期信息。
     * 延期判定逻辑：status != '已完成' 且 end_date < today，delay_days = today - end_date。
     * 当 projectId 为 null 时查询全部未归档项目，否则仅查询指定项目。
     *
     * @param today     统计基准日（当天），用于计算延期天数
     * @param projectId 项目ID（可选，为 null 时查询全部）
     * @return 任务进度列表，每条包含 projectId/projectCode/projectName/managerName/taskName/
     *         assigneeName/startDate/endDate/actualStartDate/actualEndDate/progress/status/
     *         isMilestone/isKeyTask/isDelayed/delayDays/sort/remark
     */
    @Select("<script>" +
            "SELECT p.id AS projectId, p.project_code AS projectCode, p.name AS projectName, p.manager_name AS managerName," +
            "       t.id AS taskId, t.name AS taskName, t.assignee_name AS assigneeName," +
            "       t.start_date AS startDate, t.end_date AS endDate," +
            "       t.actual_start_date AS actualStartDate, t.actual_end_date AS actualEndDate," +
            "       t.progress, t.status, t.is_milestone AS isMilestone, t.is_key_task AS isKeyTask," +
            "       t.sort, t.remark," +
            "       CASE WHEN t.status != '已完成' AND t.end_date &lt; #{today}" +
            "            THEN 1 ELSE 0 END AS isDelayed," +
            "       CASE WHEN t.status != '已完成' AND t.end_date &lt; #{today}" +
            "            THEN DATEDIFF(#{today}, t.end_date) ELSE 0 END AS delayDays" +
            " FROM biz_task t" +
            " INNER JOIN biz_project p ON p.id = t.project_id AND p.is_archived = 0" +
            " <where>" +
            "   <if test='projectId != null'> AND p.id = #{projectId} </if>" +
            " </where>" +
            " ORDER BY p.id ASC, t.sort ASC, t.id ASC" +
            "</script>")
    List<Map<String, Object>> selectGanttTaskProgress(@Param("today") LocalDate today,
                                                       @Param("projectId") Long projectId);

    /**
     * 查询项目级甘特图进度汇总（支持按项目筛选）。
     * <p>
     * 对每个未归档项目，统计任务总数、已完成数、进行中数、未开始数、延期数，
     * 以及项目整体进度（取自 biz_project.progress 字段）。
     * 当 projectId 为 null 时汇总全部未归档项目，否则仅汇总指定项目。
     *
     * @param today     统计基准日，用于判定延期
     * @param projectId 项目ID（可选，为 null 时查询全部）
     * @return 项目汇总列表，每条包含 projectId/projectCode/projectName/managerName/progress/
     *         status/startDate/endDate/taskTotal/taskCompleted/taskInProgress/taskPending/taskDelayed
     */
    @Select("<script>" +
            "SELECT p.id AS projectId, p.project_code AS projectCode, p.name AS projectName," +
            "       p.manager_name AS managerName, p.progress, p.status," +
            "       p.start_date AS startDate, p.end_date AS endDate," +
            "       COUNT(t.id) AS taskTotal," +
            "       SUM(CASE WHEN t.status = '已完成' THEN 1 ELSE 0 END) AS taskCompleted," +
            "       SUM(CASE WHEN t.status = '进行中' THEN 1 ELSE 0 END) AS taskInProgress," +
            "       SUM(CASE WHEN t.status = '未开始' THEN 1 ELSE 0 END) AS taskPending," +
            "       SUM(CASE WHEN t.status != '已完成' AND t.end_date &lt; #{today} THEN 1 ELSE 0 END) AS taskDelayed" +
            " FROM biz_project p" +
            " LEFT JOIN biz_task t ON t.project_id = p.id" +
            " <where>" +
            "   p.is_archived = 0" +
            "   <if test='projectId != null'> AND p.id = #{projectId} </if>" +
            " </where>" +
            " GROUP BY p.id, p.project_code, p.name, p.manager_name, p.progress, p.status, p.start_date, p.end_date" +
            " ORDER BY p.progress DESC, p.name ASC" +
            "</script>")
    List<Map<String, Object>> selectProjectGanttSummary(@Param("today") LocalDate today,
                                                         @Param("projectId") Long projectId);
}
