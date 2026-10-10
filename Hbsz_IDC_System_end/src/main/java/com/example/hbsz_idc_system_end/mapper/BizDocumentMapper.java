package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.hbsz_idc_system_end.dto.DocumentQueryDTO;
import com.example.hbsz_idc_system_end.dto.DocumentVO;
import com.example.hbsz_idc_system_end.entity.BizDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文档数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link BizDocument}，映射数据表 {@code biz_document}，负责项目文档的增删改查。
 * 复杂查询（分页列表、详情、分类统计）以 XML 方式定义于
 * {@code resources/mapper/BizDocumentMapper.xml}，通过关联 {@code biz_project} 补全项目名称。
 *
 * @author hbsz
 */
@Mapper
public interface BizDocumentMapper extends BaseMapper<BizDocument> {

    /**
     * 分页查询文档列表（含所属项目名称）。
     * <p>
     * 对应 XML 中的 {@code selectDocumentPage}：以 {@code biz_document} 为主表 LEFT JOIN
     * {@code biz_project} 回填项目名称，并通过 {@code <where>/<if>} 组合关键字、分类、项目、
     * 状态、文件类型、上传人、上传时间区间等动态条件，最终按上传时间倒序分页。
     * 性能上建议对 {@code project_id} 及排序列 {@code upload_date} 建立索引，
     * 关键字/上传人的前后模糊匹配无法走索引，属可接受的范围过滤。
     *
     * @param page  MyBatis-Plus 分页对象，携带页码与每页条数
     * @param query 文档查询条件封装
     * @return 文档视图对象的分页结果
     */
    IPage<DocumentVO> selectDocumentPage(IPage<DocumentVO> page, @Param("query") DocumentQueryDTO query);

    /**
     * 根据文档ID查询文档详情（含所属项目名称）。
     * <p>
     * 对应 XML 中的 {@code selectDocumentDetailById}，主键等值查询后 LEFT JOIN
     * {@code biz_project} 回填项目名称。
     *
     * @param id 文档ID
     * @return 文档视图对象，未命中时返回 {@code null}
     */
    DocumentVO selectDocumentDetailById(@Param("id") Long id);

    /**
     * 按文档分类聚合统计文档数量。
     * <p>
     * 对应 XML 中的 {@code countByCategory}，过滤空分类后按 {@code category} 分组计数，
     * 用于文档分类分布图表。
     *
     * @return 每项包含 {@code category}（分类名）与 {@code count}（数量）的映射列表
     */
    List<Map<String, Object>> countByCategory();

    /**
     * CAS 原子更新文档审核状态（防止并发重复审核）。
     * <p>
     * 仅当文档状态为"待审核"时才执行更新，通过 WHERE 条件实现乐观锁。
     * 受影响行数为 0 时表示已被其他线程审核，调用方应抛出并发冲突异常。
     *
     * @param id           文档ID
     * @param newStatus    新状态（"已审核"/"已驳回"）
     * @param reviewerId   审核人ID
     * @param reviewTime   审核时间
     * @param reviewRemark 审核意见
     * @return 受影响行数（1=成功，0=并发冲突）
     */
    @Update("UPDATE biz_document SET status = #{newStatus}, reviewer_id = #{reviewerId}, " +
            "review_time = #{reviewTime}, review_remark = #{reviewRemark} " +
            "WHERE id = #{id} AND status = '待审核'")
    int updateReviewStatusCAS(@Param("id") Long id,
                              @Param("newStatus") String newStatus,
                              @Param("reviewerId") Long reviewerId,
                              @Param("reviewTime") LocalDateTime reviewTime,
                              @Param("reviewRemark") String reviewRemark);

}
