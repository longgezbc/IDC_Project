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
 * 设计图纸实体
 * <p>
 * 对应数据库表 biz_design_drawing，管理项目的设计图纸文件信息，
 * projectId 关联项目（biz_project），uploadedBy 关联上传人（sys_user）。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("biz_design_drawing")
public class BizDesignDrawing implements Serializable {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目ID，关联 biz_project.id */
    private Long projectId;

    /** 图纸名称 */
    private String name;

    /** 图纸类型：如 施工图/结构图/电气图 */
    private String drawingType;

    /** 文件存储地址 */
    private String fileUrl;

    /** 文件大小（可读文本，如 2.5MB） */
    private String fileSize;

    /** 文件大小（字节数） */
    private Long fileSizeBytes;

    /** 上传日期 */
    private LocalDate uploadDate;

    /** 上传人ID，关联 sys_user.id */
    private Long uploadedBy;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}
