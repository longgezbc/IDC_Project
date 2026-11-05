package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 通用评论实体。支持项目/文档/任务等多业务对象的评论与回复，
 * content 中的 @用户名 会被解析并触发提及通知。
 * @author hbsz
 */
@Data
@TableName("biz_comment")
public class BizComment extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 业务类型：project/document/task */
    private String bizType;
    private Long bizId;
    /** 评论内容（支持 @用户名 提及） */
    private String content;
    /** 解析出的被提及用户ID列表（逗号分隔） */
    private String mentions;
    private Long authorId;
    private String authorName;
    /** 父评论ID（支持回复） */
    private Long parentId;
}
