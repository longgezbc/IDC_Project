package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.hbsz_idc_system_end.dto.ProjectQueryDTO;
import com.example.hbsz_idc_system_end.dto.ProjectVO;
import com.example.hbsz_idc_system_end.entity.BizProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 项目数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link BizProject}，映射数据表 {@code biz_project}，负责项目主数据的增删改查。
 * 复杂查询（关联客户的分页/详情、归档列表、多维聚合统计与概览指标）以 XML 方式定义于
 * {@code resources/mapper/BizProjectMapper.xml}，通过关联 {@code biz_customer} 补全客户信息，
 * 并以单条聚合查询替代全表加载以降低内存与网络开销。
 *
 * @author hbsz
 */
@Mapper
public interface BizProjectMapper extends BaseMapper<BizProject> {

    /**
     * 分页查询项目列表（关联客户信息）。
     * <p>
     * 对应 XML 中的 {@code selectProjectPage}：以 {@code biz_project} 为主表 LEFT JOIN
     * {@code biz_customer} 回填客户名称与联系方式，通过 {@code <where>/<if>} 组合是否归档、
     * 关键字、状态、类型、优先级、负责人、开始日期区间等动态条件，按创建时间倒序分页。
     * 性能上建议对 {@code customer_id} 及排序列 {@code created_at} 建立索引；
     * 关键字/负责人的前后模糊匹配无法走索引，属可接受的范围过滤。
     *
     * @param page  MyBatis-Plus 分页对象，携带页码与每页条数
     * @param query 项目查询条件封装
     * @return 项目视图对象的分页结果
     */
    IPage<ProjectVO> selectProjectPage(IPage<ProjectVO> page, @Param("query") ProjectQueryDTO query);

    /**
     * 根据项目ID查询项目详情（含客户信息）。
     * <p>
     * 对应 XML 中的 {@code selectProjectDetailById}，主键等值查询后 LEFT JOIN
     * {@code biz_customer} 回填客户信息。
     *
     * @param id 项目ID
     * @return 项目视图对象，未命中时返回 {@code null}
     */
    ProjectVO selectProjectDetailById(@Param("id") Long id);

    /**
     * 分页查询归档项目列表。
     * <p>
     * 对应 XML 中的 {@code selectArchivedProjectPage}，固定过滤 {@code is_archived = 1}，
     * LEFT JOIN {@code biz_customer} 回填客户信息，支持按关键字模糊过滤，按归档时间倒序分页。
     *
     * @param page    MyBatis-Plus 分页对象，携带页码与每页条数
     * @param keyword 项目名称/编码模糊关键字，可为空
     * @return 归档项目视图对象的分页结果
     */
    IPage<ProjectVO> selectArchivedProjectPage(IPage<ProjectVO> page, @Param("keyword") String keyword);

    /**
     * 统计各状态的未归档项目数量。
     * <p>
     * 对应 XML 中的 {@code countByStatus}，过滤 {@code is_archived = 0} 后按 {@code status}
     * 分组计数，用于状态分布图表。
     *
     * @return 每项包含 {@code name}（状态）与 {@code value}（数量）的映射列表
     */
    List<Map<String, Object>> countByStatus();

    /**
     * 统计各类型的未归档项目数量。
     * <p>
     * 对应 XML 中的 {@code countByType}，过滤 {@code is_archived = 0} 后按 {@code project_type}
     * 分组计数，用于类型分布图表。
     *
     * @return 每项包含 {@code name}（类型）与 {@code value}（数量）的映射列表
     */
    List<Map<String, Object>> countByType();

    /**
     * 统计各优先级的未归档项目数量。
     * <p>
     * 对应 XML 中的 {@code countByPriority}，过滤 {@code is_archived = 0} 后按 {@code priority}
     * 分组计数，用于优先级分布图表。
     *
     * @return 每项包含 {@code name}（优先级）与 {@code value}（数量）的映射列表
     */
    List<Map<String, Object>> countByPriority();

    /**
     * 按月统计指定年度的项目数量趋势（新增数与完成数）。
     * <p>
     * 对应 XML 中的 {@code countByMonth}，按 {@code DATE_FORMAT(created_at,'%Y-%m')} 分组，
     * 同时用 {@code SUM(CASE WHEN status='已完成' ...)} 聚合当月完成数。注意 {@code YEAR(created_at)}
     * 为函数条件，无法命中 {@code created_at} 索引，数据量大时可考虑改写为区间比较。
     *
     * @param year 统计年度（四位年份）
     * @return 每项包含 {@code month}、{@code newCount}、{@code completedCount} 的映射列表（按月份升序）
     */
    List<Map<String, Object>> countByMonth(@Param("year") Integer year);

    /**
     * 统计未归档项目的合同金额与预算汇总。
     * <p>
     * 对应 XML 中的 {@code sumContractAmount}，一次聚合返回总合同额、进行中/已完成合同额及总预算，
     * 空值以 {@code IFNULL} 兜底为 0。
     *
     * @return 含 {@code totalAmount}、{@code activeAmount}、{@code completedAmount}、{@code totalBudget} 的映射
     */
    Map<String, Object> sumContractAmount();

    /**
     * 项目概览统计（一次聚合查询替代全表加载）。
     * <p>
     * 对应 XML 中的 {@code selectOverviewStatistics}，用单条带 {@code CASE WHEN} 的聚合查询
     * 同时算出总数、进行中数、已完成数、归档数、合同总额与进行中项目平均进度，
     * 避免将全表数据加载到应用层再统计带来的内存与网络开销。
     *
     * @return 含 {@code totalProjects}、{@code activeProjects}、{@code completedProjects}、
     *         {@code archivedProjects}、{@code totalContractAmount}、{@code avgProgress} 的映射
     */
    Map<String, Object> selectOverviewStatistics();

    /**
     * 归档统计（一次聚合查询替代全表加载）。
     * <p>
     * 对应 XML 中的 {@code selectArchiveStatistics}，固定过滤 {@code is_archived = 1}，
     * 用单条带 {@code CASE WHEN} 的聚合查询同时算出归档总数、合同总额、当年归档数与当月归档数。
     *
     * @param currentYear  当前年份（用于统计当年/当月归档）
     * @param currentMonth 当前月份（用于统计当月归档）
     * @return 含 {@code totalCount}、{@code totalContractAmount}、{@code currentYearCount}、
     *         {@code currentMonthCount} 的映射
     */
    Map<String, Object> selectArchiveStatistics(@Param("currentYear") Integer currentYear,
                                                 @Param("currentMonth") Integer currentMonth);

    /**
     * 按客户ID批量统计关联项目数（GROUP BY 聚合，XML 实现）。
     * <p>
     * 【修复M10】替代原加载全部项目实体后内存 for 循环计数的低效实现，
     * DB 侧一次 GROUP BY 聚合完成计数，显著降低内存与网络开销。
     *
     * @param customerIds 客户ID列表
     * @return 每项包含 {@code customerId} 与 {@code cnt} 的映射列表
     */
    List<Map<String, Object>> countByCustomerIds(@Param("customerIds") List<Long> customerIds);

}
