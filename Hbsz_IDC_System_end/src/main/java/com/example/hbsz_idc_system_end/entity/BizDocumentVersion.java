package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 文档版本实体
 * <p>
 * 对应数据库表 biz_document_version，保存文档（biz_document）的历史版本记录，
 * documentId 关联所属文档，uploaderId 关联上传人（sys_user）。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("biz_document_version")
public class BizDocumentVersion implements Serializable {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文档ID，关联 biz_document.id */
    private Long documentId;

    /** 版本号 */
    private String version;

    /** 文件存储地址 */
    private String fileUrl;

    /** 文件大小（可读文本，如 2.5MB） */
    private String fileSize;

    /** 上传人ID，关联 sys_user.id */
    private Long uploaderId;

    /** 上传人姓名（冗余字段） */
    private String uploaderName;

    /** 版本备注 */
    private String remark;

    /** 上传日期 */
    private LocalDate uploadDate;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}
