package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知展示 VO
 * <p>
 * 在 {@link com.example.hbsz_idc_system_end.entity.SysNotification} 基础上无额外扩展字段，
 * 独立 VO 便于后续扩展（如附带发送人信息、业务对象名称等）且屏蔽实体内部字段差异。
 * </p>
 *
 * @author hbsz
 */
@Data
public class NotificationVO implements Serializable {

    /** 通知ID */
    private Long id;

    /** 接收用户ID */
    private Long userId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型：progress/task_due/doc_update/archive/system */
    private String notifyType;

    /** 业务类型：project/task/document */
    private String bizType;

    /** 业务ID */
    private Long bizId;

    /** 优先级：1普通 2重要 3紧急 */
    private Integer priority;

    /** 是否已读：0未读 1已读 */
    private Integer isRead;

    /** 阅读时间 */
    private LocalDateTime readTime;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
