package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.BizTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 任务数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link BizTask}，映射数据表 {@code biz_task}，负责项目任务的增删改查。
 * 除 MyBatis-Plus 通用方法外，额外提供关键任务预警查询（基于 UNION 优化）以及
 * 按任务平均进度原子回写项目进度等方法，用于进度看板与任务提醒场景。
 *
 * @author hbsz
 */
@Mapper
public interface BizTaskMapper extends BaseMapper<BizTask> {

    /**
     * 查询指定项目即将到来的关键任务（未来7天内开始或结束，或已延期）。
     * <p>
     * SQL 优化说明：
     * 原版使用 OR 连接 start_date / end_date 范围条件，MySQL 难以同时使用多个索引，
     * 容易退化为全表扫描。改为 UNION 拆分后，每个子查询都能命中 idx_start_date 或 idx_end_date 索引，
     * 且每个子查询自带 LIMIT 100，避免一次性加载过多数据到内存。
     *
     * @param projectId 项目ID
     * @param today     统计基准日（当天），用于判定延期与未来窗口起点
     * @param nextWeek  未来窗口终点（通常为 today + 7 天）
     * @return 该项目下按结束日期升序排列的关键任务列表（最多 100 条）
     */
    @Select({
            "<script>",
            "(SELECT * FROM biz_task WHERE project_id = #{projectId} AND is_key_task = 1 AND status != '已完成'",
            "  AND start_date BETWEEN #{today} AND #{nextWeek} ORDER BY end_date ASC LIMIT 100)",
            "UNION",
            "(SELECT * FROM biz_task WHERE project_id = #{projectId} AND is_key_task = 1 AND status != '已完成'",
            "  AND end_date BETWEEN #{today} AND #{nextWeek} ORDER BY end_date ASC LIMIT 100)",
            "UNION",
            "(SELECT * FROM biz_task WHERE project_id = #{projectId} AND is_key_task = 1 AND status != '已完成'",
            "  AND end_date &lt; #{today} ORDER BY end_date ASC LIMIT 100)",
            "ORDER BY end_date ASC LIMIT 100",
            "</script>"
    })
    List<BizTask> selectUpcomingKeyTasks(@Param("projectId") Long projectId,
                                          @Param("today") LocalDate today,
                                          @Param("nextWeek") LocalDate nextWeek);

    /**
     * 查询所有项目即将到来的关键任务。
     * <p>
     * SQL 优化说明：
     * 1. 用 UNION 拆分 OR 条件，让每个子查询都能命中 idx_start_date / idx_end_date 索引
     * 2. 每个子查询加 LIMIT 100，最外层再 LIMIT 100，避免内存中 subList 截断
     * 3. 消除 TaskService 中 List.subList 内存截断的额外开销
     *
     * @param today    统计基准日（当天），用于判定延期与未来窗口起点
     * @param nextWeek 未来窗口终点（通常为 today + 7 天）
     * @return 全局按结束日期升序排列的关键任务列表（最多 100 条）
     */
    @Select({
            "<script>",
            "(SELECT * FROM biz_task WHERE is_key_task = 1 AND status != '已完成'",
            "  AND start_date BETWEEN #{today} AND #{nextWeek} ORDER BY end_date ASC LIMIT 100)",
            "UNION",
            "(SELECT * FROM biz_task WHERE is_key_task = 1 AND status != '已完成'",
            "  AND end_date BETWEEN #{today} AND #{nextWeek} ORDER BY end_date ASC LIMIT 100)",
            "UNION",
            "(SELECT * FROM biz_task WHERE is_key_task = 1 AND status != '已完成'",
            "  AND end_date &lt; #{today} ORDER BY end_date ASC LIMIT 100)",
            "ORDER BY end_date ASC LIMIT 100",
            "</script>"
    })
    List<BizTask> selectAllUpcomingKeyTasks(@Param("today") LocalDate today,
                                             @Param("nextWeek") LocalDate nextWeek);

    /**
     * 通过单条 UPDATE 子查询同步项目整体进度（任务平均进度）
     * <p>
     * 优化说明：
     * 原实现为先 selectList 再 updateById，两个用户并发填报同一项目不同任务时，
     * 后提交者会用基于旧数据的平均值覆盖前者的更新（典型 lost update）。
     * 改为单条 UPDATE 子查询后，整个操作在 MySQL 行锁下原子完成，消除并发覆盖风险。
     *
     * @param projectId 项目ID
     * @return 受影响行数（通常为 1）
     */
    @Update("UPDATE biz_project SET progress = " +
            "COALESCE((SELECT AVG(progress) FROM biz_task WHERE project_id = #{projectId}), 0) " +
            "WHERE id = #{projectId}")
    int updateProjectProgressByTaskAvg(@Param("projectId") Long projectId);

    /**
     * 修复 #4：聚合查询指定用户的任务统计（一次查询替代 4 次 selectCount）
     * <p>
     * 使用 SUM(CASE WHEN...) 将 total/pending/overdue/upcoming 四个计数合并为单条 SQL，
     * 将工作台 getSummary 的 DB 查询从 11 次降至 5 次。
     *
     * @param userId   用户ID
     * @param today    今天日期（用于判定逾期）
     * @param deadline 截止日期（today+3，用于判定即将到期）
     * @return Map 包含 total/pending/overdue/upcoming 四个键，值为 Long 类型
     */
    @Select("SELECT " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN status != '已完成' THEN 1 ELSE 0 END) AS pending, " +
            "SUM(CASE WHEN status != '已完成' AND end_date IS NOT NULL AND end_date < #{today} THEN 1 ELSE 0 END) AS overdue, " +
            "SUM(CASE WHEN status != '已完成' AND end_date IS NOT NULL AND end_date >= #{today} AND end_date <= #{deadline} THEN 1 ELSE 0 END) AS upcoming " +
            "FROM biz_task WHERE assignee_id = #{userId}")
    Map<String, Object> selectTaskCountsByUser(@Param("userId") Long userId,
                                                @Param("today") LocalDate today,
                                                @Param("deadline") LocalDate deadline);

}
