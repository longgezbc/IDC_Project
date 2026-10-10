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
 * 项目归档记录实体
 * <p>
 * 对应数据库表 biz_archive_record，记录项目归档/撤销归档等操作的流水，用于操作追溯。
 * projectId 关联项目（biz_project），operatorId 关联操作人（sys_user）；
 * projectCode/projectName 为归档时冗余留存的项目信息。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("biz_archive_record")
public class BizArchiveRecord implements Serializable {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID，关联 biz_project.id */
    private Long projectId;

    /** 项目编号（归档时冗余留存） */
    private String projectCode;

    /** 项目名称（归档时冗余留存） */
    private String projectName;

    /** 操作类型：如 archive归档/unarchive撤销归档 */
    private String operationType;

    /** 归档日期 */
    private LocalDate archiveDate;

    /** 操作原因/说明 */
    private String reason;

    /** 操作人ID，关联 sys_user.id */
    private Long operatorId;

    /** 操作人姓名（冗余字段） */
    private String operatorName;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}
