package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档-标签关联实体
 * <p>
 * 对应数据库表 biz_document_tag_relation，是文档（biz_document）与标签（biz_document_tag）
 * 多对多关系的中间表，每条记录表示为某文档打上某标签。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("biz_document_tag_relation")
public class BizDocumentTagRelation implements Serializable {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档ID，关联 biz_document.id */
    private Long documentId;

    /** 标签ID，关联 biz_document_tag.id */
    private Long tagId;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}
