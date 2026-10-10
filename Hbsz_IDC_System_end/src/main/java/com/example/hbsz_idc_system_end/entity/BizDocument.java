package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 文档实体
 * <p>
 * 对应数据库表 biz_document，管理项目相关文档的元信息与审核状态。
 * projectId 关联项目（biz_project），uploaderId/reviewerId 关联用户（sys_user）；
 * 历史版本记录于 biz_document_version，标签通过 biz_document_tag_relation 关联 biz_document_tag。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("biz_document")
public class BizDocument extends BaseEntity {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档编号（唯一） */
    private String documentCode;

    /** 所属项目ID，关联 biz_project.id */
    private Long projectId;

    /** 文档名称 */
    private String name;

    /** 文档分类 */
    private String category;

    /** 当前版本号 */
    private String currentVersion;

    /** 文档状态：如 draft草稿/pending待审核/approved已通过/rejected已驳回 */
    private String status;

    /** 文件格式：如 pdf/docx/dwg */
    private String fileFormat;

    /** 文件存储地址 */
    private String fileUrl;

    /** 文件大小（可读文本，如 2.5MB） */
    private String fileSize;

    /** 文件大小（字节数） */
    private Long fileSizeBytes;

    /** 文档描述 */
    private String description;

    /** 上传人ID，关联 sys_user.id */
    private Long uploaderId;

    /** 上传人姓名（冗余字段） */
    private String uploaderName;

    /** 上传日期 */
    private LocalDate uploadDate;

    /** 审核人ID，关联 sys_user.id */
    private Long reviewerId;

    /** 审核时间 */
    private LocalDateTime reviewTime;

    /** 审核备注 */
    private String reviewRemark;

}
