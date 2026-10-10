package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 通知发送 DTO
 * <p>
 * 由业务模块（进度填报、审核、归档等）在触发通知时构造，
 * 传入 {@code NotificationService.sendNotification} 完成投递。
 * </p>
 *
 * @author hbsz
 */
@Data
public class NotificationSendDTO implements Serializable {

    /** 接收用户ID */
    private Long userId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型：progress/task_due/doc_update/archive/system */
    private String notifyType;

    /** 业务类型：project/task/document（可空，用于点击跳转） */
    private String bizType;

    /** 业务ID（可空，用于点击跳转） */
    private Long bizId;

    /** 优先级：1普通 2重要 3紧急，默认1 */
    private Integer priority;

}
