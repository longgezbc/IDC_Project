package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.BizWorkloadRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 工作量每日填报数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link BizWorkloadRecord}，映射数据表 biz_workload_record。
 * 除 BaseMapper 通用操作外，提供面向统计矩阵的分组聚合查询：
 * <ul>
 *   <li>按清单项累计/当日完成量聚合（统计总览，仅统计已审核记录）；</li>
 *   <li>按清单项+人员的当日明细（填报矩阵，含审核状态供前端着色/锁定）；</li>
 *   <li>按人员在日期区间内的绩效折算聚合（人员绩效，仅统计已审核记录）；</li>
 *   <li>按项目/日期/状态筛选的填报明细（审核列表，联表取作业上下文）。</li>
 * </ul>
 *
 * @author hbsz
 */
@Mapper
public interface BizWorkloadRecordMapper extends BaseMapper<BizWorkloadRecord> {

    /**
     * 按清单项聚合完成量：每项的累计完成量（已完成）与指定日期当日完成量（今日工作量）。
     * <p>
     * 一条 SQL 同时产出两列聚合值，避免分别查询两次全表；
     * LEFT JOIN 保证从未填报的清单项也返回 0 值行（COALESCE 兜底）。
     * <p>
     * 审核口径（2026-08-18）：仅统计 review_status='approved' 的记录，
     * 待审核/已驳回填报不计入已完成量与今日量。
     *
     * @param projectId      项目ID
     * @param statisticsDate 当日统计日期（今日工作量归属日）
     * @return 每个元素为 Map：itemId（Long）、completed（BigDecimal 累计）、todayQuantity（BigDecimal 当日）
     */
    @Select("SELECT r.item_id AS `itemId`, " +
            "COALESCE(SUM(r.quantity), 0) AS `completed`, " +
            "COALESCE(SUM(CASE WHEN r.record_date = #{statisticsDate} THEN r.quantity ELSE 0 END), 0) AS `todayQuantity` " +
            "FROM biz_workload_record r " +
            "WHERE r.project_id = #{projectId} AND r.review_status = 'approved' " +
            "GROUP BY r.item_id")
    List<Map<String, Object>> selectItemAggregates(@Param("projectId") Long projectId,
                                                   @Param("statisticsDate") LocalDate statisticsDate);

    /**
     * 查询指定日期的填报矩阵明细：清单项 × 人员 的当日完成量（含审核状态）。
     * <p>
     * 返回全部状态单元格（pending/approved/rejected），供前端编辑回显与状态着色；
     * approved 单元格前端锁定不可编辑，绩效折算仅取 approved。
     *
     * @param projectId  项目ID
     * @param recordDate 填报日期
     * @return 每个元素为 Map：itemId（Long）、workerName（String）、workerUserId（Long，可空）、
     *         quantity（BigDecimal）、reviewStatus（String）、reviewRemark（String，可空）
     */
    @Select("SELECT r.item_id AS `itemId`, r.worker_name AS `workerName`, r.worker_user_id AS `workerUserId`, " +
            "r.quantity AS `quantity`, r.review_status AS `reviewStatus`, r.review_remark AS `reviewRemark` " +
            "FROM biz_workload_record r " +
            "WHERE r.project_id = #{projectId} AND r.record_date = #{recordDate}")
    List<Map<String, Object>> selectDailyMatrix(@Param("projectId") Long projectId,
                                                @Param("recordDate") LocalDate recordDate);

    /**
     * 按人员在日期区间内聚合绩效：绩效 = Σ(数量 ÷ 额定工作量 × 绩效分权重)。
     * <p>
     * 联表 biz_workload_item 取额定工作量与绩效权重，在 SQL 侧完成折算求和，
     * 避免将全量明细拉回内存计算；额定工作量按 NULLIF 防零保护。
     * <p>
     * 审核口径（2026-08-18）：仅统计 review_status='approved' 的记录。
     *
     * @param projectId 项目ID
     * @param startDate 区间开始日期（含）
     * @param endDate   区间结束日期（含）
     * @return 每个元素为 Map：workerName（String）、totalQuantity（BigDecimal 完成总量）、
     *         performanceScore（BigDecimal 绩效分合计）、recordDays（Long 有效填报天数）
     */
    @Select("SELECT r.worker_name AS `workerName`, " +
            "COALESCE(SUM(r.quantity), 0) AS `totalQuantity`, " +
            "COALESCE(SUM(r.quantity / NULLIF(i.rated_workload, 0) * i.performance_score), 0) AS `performanceScore`, " +
            "COUNT(DISTINCT r.record_date) AS `recordDays` " +
            "FROM biz_workload_record r " +
            "JOIN biz_workload_item i ON r.item_id = i.id " +
            "WHERE r.project_id = #{projectId} AND r.record_date BETWEEN #{startDate} AND #{endDate} " +
            "AND r.review_status = 'approved' " +
            "GROUP BY r.worker_name")
    List<Map<String, Object>> selectWorkerPerformance(@Param("projectId") Long projectId,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);

    /**
     * 查询填报明细审核列表：按项目（必填）+ 日期（可选）+ 审核状态（可选）筛选，
     * 联表 biz_workload_item 取作业类型/作业范围/单位上下文。
     * <p>
     * 排序：填报日期倒序 → 记录ID 升序（同日按清单行序稳定展示）。
     *
     * @param projectId    项目ID
     * @param recordDate   填报日期（可空 = 全部日期）
     * @param reviewStatus 审核状态（可空 = 全部状态）
     * @return 每个元素为 Map：id、projectId、itemId、category、workScope、unit、recordDate、
     *         workerName、workerUserId、quantity、reviewStatus、reviewRemark、reviewerName、reviewTime、createdAt
     */
    @Select("SELECT r.id AS `id`, r.project_id AS `projectId`, r.item_id AS `itemId`, " +
            "i.category AS `category`, i.work_scope AS `workScope`, i.unit AS `unit`, " +
            "r.record_date AS `recordDate`, r.worker_name AS `workerName`, r.worker_user_id AS `workerUserId`, " +
            "r.quantity AS `quantity`, r.review_status AS `reviewStatus`, r.review_remark AS `reviewRemark`, " +
            "r.reviewer_name AS `reviewerName`, r.review_time AS `reviewTime`, r.created_at AS `createdAt` " +
            "FROM biz_workload_record r " +
            "JOIN biz_workload_item i ON r.item_id = i.id " +
            "WHERE r.project_id = #{projectId} " +
            "AND (#{recordDate} IS NULL OR r.record_date = #{recordDate}) " +
            "AND (#{reviewStatus} IS NULL OR r.review_status = #{reviewStatus}) " +
            "ORDER BY r.record_date DESC, r.id ASC")
    List<Map<String, Object>> selectReviewRecords(@Param("projectId") Long projectId,
                                                  @Param("recordDate") LocalDate recordDate,
                                                  @Param("reviewStatus") String reviewStatus);

}
