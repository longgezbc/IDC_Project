package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.BizTaskProgressLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务进度记录数据访问层（Mapper）。
 * <p>
 * 对应实体 {@code BizTaskProgressLog}，映射数据表 {@code biz_task_progress_log}，负责
 * 任务进度填报流水的增删改查等基础数据访问。本接口仅继承 MyBatis-Plus 的 {@code BaseMapper}，
 * 未声明自定义 SQL，通用单表操作由框架自动实现。
 *
 * @author hbsz
 */
@Mapper
public interface BizTaskProgressLogMapper extends BaseMapper<BizTaskProgressLog> {

    /**
     * 修复 #4：聚合查询指定用户的填报统计（一次查询替代 4 次 selectCount）
     * <p>
     * 使用 SUM(CASE WHEN...) 将 total/pending/approved/rejected 四个计数合并为单条 SQL，
     * 将工作台 getSummary 的 DB 查询从 11 次降至 5 次。
     *
     * @param userId 用户ID
     * @return Map 包含 total/pending/approved/rejected 四个键，值为 Long 类型
     */
    @Select("SELECT " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN review_status = 'pending' THEN 1 ELSE 0 END) AS pending, " +
            "SUM(CASE WHEN review_status = 'approved' THEN 1 ELSE 0 END) AS approved, " +
            "SUM(CASE WHEN review_status = 'rejected' THEN 1 ELSE 0 END) AS rejected " +
            "FROM biz_task_progress_log WHERE reporter_id = #{userId}")
    Map<String, Object> selectSubmissionCountsByUser(@Param("userId") Long userId);

    /**
     * CAS 式原子更新审核状态（修复重复审核竞态条件）。
     * <p>
     * 原实现为先 selectById 检查 reviewStatus='pending'，再 updateById 写入审核结果，
     * 两个项目经理并发审核同一条日志时，两个线程都通过 pending 检查，导致：
     * <ul>
     *   <li>任务进度被回写两次；</li>
     *   <li>通知被发送两次。</li>
     * </ul>
     * 改为单条 UPDATE 带 WHERE review_status='pending' 条件，利用 MySQL 行锁保证原子性：
     * 只有一个线程能成功更新（affectedRows=1），另一线程 affectedRows=0 据此抛出"已审核"异常。
     *
     * @param logId        进度日志ID
     * @param newStatus    新审核状态（approved / rejected）
     * @param reviewerId   审核人ID
     * @param reviewerName 审核人姓名
     * @param reviewTime   审核时间
     * @param reviewRemark 审核意见
     * @return 受影响行数（1=成功占位，0=已被其他线程审核）
     */
    @Update("UPDATE biz_task_progress_log SET " +
            "review_status = #{newStatus}, " +
            "reviewer_id = #{reviewerId}, " +
            "reviewer_name = #{reviewerName}, " +
            "review_time = #{reviewTime}, " +
            "review_remark = #{reviewRemark} " +
            "WHERE id = #{logId} AND review_status = 'pending'")
    int updateReviewStatusCAS(@Param("logId") Long logId,
                              @Param("newStatus") String newStatus,
                              @Param("reviewerId") Long reviewerId,
                              @Param("reviewerName") String reviewerName,
                              @Param("reviewTime") LocalDateTime reviewTime,
                              @Param("reviewRemark") String reviewRemark);

    /**
     * P1-3.3：按项目分组统计待审核进度日志数量。
     * <p>
     * 替代 DashboardServiceImpl.buildProjectRisks 中"selectList 全字段加载到内存 + 循环 merge 计数"的实现，
     * 减小网络传输与内存开销（尤其当 pending 日志数量较大时）。
     * <p>
     * SQL 使用 IN + GROUP BY，命中 P0-3.1 新增的复合索引
     * idx_project_review_time(project_id, review_status, report_time)。
     *
     * @param projectIds 项目ID集合（不可为空）
     * @return 每个元素为 Map，包含 `projectId`（Long）与 `pendingCount`（Long）两个键
     */
    @Select("<script>" +
            "SELECT project_id AS `projectId`, COUNT(*) AS `pendingCount` " +
            "FROM biz_task_progress_log " +
            "WHERE review_status = 'pending' " +
            "AND project_id IN " +
            "<foreach collection='projectIds' item='pid' open='(' separator=',' close=')'>" +
            "  #{pid}" +
            "</foreach> " +
            "GROUP BY project_id" +
            "</script>")
    List<Map<String, Object>> selectPendingCountByProjectIds(@Param("projectIds") List<Long> projectIds);
}
