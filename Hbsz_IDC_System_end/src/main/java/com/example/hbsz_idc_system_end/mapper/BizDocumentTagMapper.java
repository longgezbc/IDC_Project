package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.BizDocumentTag;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档标签数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link BizDocumentTag}，映射数据表 {@code biz_document_tag}，负责文档标签的
 * 增删改查。通过关联 {@code biz_document_tag_relation} 提供按文档查询标签名称、批量查询
 * 多文档标签等方法，用于文档列表与详情的标签展示。
 *
 * @author hbsz
 */
@Mapper
public interface BizDocumentTagMapper extends BaseMapper<BizDocumentTag> {

    /**
     * 根据文档ID查询其关联的标签名称列表。
     * <p>
     * {@code biz_document_tag} INNER JOIN {@code biz_document_tag_relation} 关联查询，
     * 取出该文档绑定的标签名称。
     *
     * @param documentId 文档ID
     * @return 该文档的标签名称列表
     */
    @Select("SELECT t.tag_name FROM biz_document_tag t " +
            "INNER JOIN biz_document_tag_relation r ON t.id = r.tag_id " +
            "WHERE r.document_id = #{documentId}")
    List<String> selectTagNamesByDocumentId(@Param("documentId") Long documentId);

    /**
     * 批量查询多个文档的标签名称（解决 N+1 查询问题）。
     * <p>
     * {@code biz_document_tag} INNER JOIN {@code biz_document_tag_relation}，并以
     * {@code document_id IN (...)} 配合 {@code <foreach>} 一次性取回多个文档的标签，
     * 避免逐文档查询产生 N 次数据库往返；调用方可按 document_id 在内存中分组。
     * 建议 {@code biz_document_tag_relation.document_id} 建立索引。
     *
     * @param documentIds 文档ID列表，不应为空
     * @return 每行包含 {@code document_id} 与 {@code tag_name} 的映射列表
     */
    @Select({
        "<script>",
        "SELECT r.document_id, t.tag_name",
        "FROM biz_document_tag t",
        "INNER JOIN biz_document_tag_relation r ON t.id = r.tag_id",
        "WHERE r.document_id IN",
        "<foreach collection='documentIds' item='id' open='(' separator=',' close=')'>",
        "#{id}",
        "</foreach>",
        "</script>"
    })
    List<java.util.Map<String, Object>> selectTagNamesByDocumentIds(@Param("documentIds") List<Long> documentIds);

    /**
     * 修复 #5：批量插入文档标签（替代循环单条 insert）
     * <p>
     * 使用单条 INSERT ... VALUES (...),(...) 配合 foreach 一次性写入多条标签，
     * 避免循环逐条 insert 产生的 N 次数据库往返。
     * 通过 useGeneratedKeys=true 回填自增主键到入参 list 中各元素的 id 字段。
     *
     * @param list 文档标签列表，不应为空
     * @return 受影响行数
     */
    @Insert({
            "<script>",
            "INSERT INTO biz_document_tag (tag_name) VALUES",
            "<foreach collection='list' item='tag' separator=','>",
            "(#{tag.tagName})",
            "</foreach>",
            "</script>"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int batchInsert(@Param("list") List<BizDocumentTag> list);

}
