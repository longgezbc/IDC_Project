package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.BizDocumentVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档版本数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link BizDocumentVersion}，映射数据表 {@code biz_document_version}，负责文档
 * 历史版本的增删改查。额外提供按多文档批量查询版本的方法，用于文档详情的版本历史展示。
 *
 * @author hbsz
 */
@Mapper
public interface BizDocumentVersionMapper extends BaseMapper<BizDocumentVersion> {

    /**
     * 批量查询多个文档的版本记录（解决 N+1 查询问题）。
     * <p>
     * 以 {@code document_id IN (...)} 配合 {@code <foreach>} 一次性取回多个文档的全部版本，
     * 避免逐文档查询产生 N 次数据库往返，并按 {@code document_id, upload_date DESC} 排序，
     * 便于调用方按文档分组且各文档内版本按时间倒序。建议 {@code document_id} 建立索引。
     *
     * @param documentIds 文档ID列表，不应为空
     * @return 匹配文档下的版本记录列表（按文档、上传时间倒序排列）
     */
    @Select({
        "<script>",
        "SELECT id, document_id, version, upload_date, uploader_name, remark",
        "FROM biz_document_version",
        "WHERE document_id IN",
        "<foreach collection='documentIds' item='id' open='(' separator=',' close=')'>",
        "#{id}",
        "</foreach>",
        "ORDER BY document_id, upload_date DESC",
        "</script>"
    })
    List<BizDocumentVersion> selectByDocumentIds(@Param("documentIds") List<Long> documentIds);

}
