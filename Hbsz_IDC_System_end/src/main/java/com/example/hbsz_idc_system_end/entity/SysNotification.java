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
 * 站内通知实体
 * <p>
 * 对应数据库表 sys_notification，记录发送给单个用户的站内通知消息。
 * userId 关联接收用户（sys_user）；notifyType 标识通知类型；
 * bizType + bizId 用于关联回具体业务对象（project/task/document），便于点击跳转。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("sys_notification")
public class SysNotification implements Serializable {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收用户ID，关联 sys_user.id */
    private Long userId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型：progress/task_due/doc_update/archive/system */
    private String notifyType;

    /** 业务类型：project/task/document（可空） */
    private String bizType;

    /** 业务ID，关联具体业务对象主键（可空） */
    private Long bizId;

    /** 优先级：1普通 2重要 3紧急 */
    private Integer priority;

    /** 是否已读：0未读 1已读 */
    private Integer isRead;

    /** 阅读时间 */
    private LocalDateTime readTime;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}
