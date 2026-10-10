package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 导出历史记录实体
 * <p>
 * 对应数据库表 biz_export_record，记录用户每次导出日报/周报的元信息，
 * 包括导出人、统计区间、格式、文件路径与大小等，用于导出历史查询与重新下载。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("biz_export_record")
public class BizExportRecord implements Serializable {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 导出人ID，关联 sys_user.id */
    private Long userId;

    /** 导出人姓名（冗余字段，避免联表查询） */
    private String userName;

    /** 报告类型：daily-日报 / weekly-周报 */
    private String reportType;

    /** 统计区间开始日期 */
    private LocalDate startDate;

    /** 统计区间结束日期 */
    private LocalDate endDate;

    /** 导出格式：xlsx / pdf */
    private String format;

    /** 生成的文件名（含扩展名） */
    private String fileName;

    /** 服务器端文件存储相对路径（相对于导出根目录） */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 导出状态：success / failed */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 逻辑删除字段：0-未删除 1-已删除，@TableLogic 由 MyBatis-Plus 自动处理 */
    @TableLogic
    private Integer deleted;
}
