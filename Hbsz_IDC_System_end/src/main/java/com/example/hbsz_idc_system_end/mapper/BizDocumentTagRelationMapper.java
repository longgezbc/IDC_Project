package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.BizDocumentTagRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档标签关联数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link BizDocumentTagRelation}，映射文档与标签的多对多关联表
 * {@code biz_document_tag_relation}，负责维护文档与标签的绑定关系。批量插入 SQL 定义于
 * {@code resources/mapper/BizDocumentTagRelationMapper.xml}。
 *
 * @author hbsz
 */
@Mapper
public interface BizDocumentTagRelationMapper extends BaseMapper<BizDocumentTagRelation> {

    /**
     * 批量插入文档标签关联记录。
     * <p>
     * 对应 XML 中的 {@code batchInsert}，使用单条 {@code INSERT ... VALUES (...),(...)}
     * 配合 {@code <foreach>} 一次性写入多条关联，避免循环单条插入产生的多次数据库往返。
     *
     * @param list 文档标签关联列表，不应为空
     * @return 受影响行数
     */
    int batchInsert(@Param("list") List<BizDocumentTagRelation> list);

}
