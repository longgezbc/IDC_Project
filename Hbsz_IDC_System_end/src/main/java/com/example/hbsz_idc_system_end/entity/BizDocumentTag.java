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
 * 文档标签实体
 * <p>
 * 对应数据库表 biz_document_tag，定义可复用的文档标签，
 * 通过中间表 biz_document_tag_relation 与文档（biz_document）多对多关联。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("biz_document_tag")
public class BizDocumentTag implements Serializable {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名称 */
    private String tagName;

    /** 标签颜色（如 #FF0000，用于前端展示） */
    private String tagColor;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}
