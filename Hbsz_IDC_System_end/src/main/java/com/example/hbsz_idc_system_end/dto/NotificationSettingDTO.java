package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 通知设置 DTO（请求/响应参数封装）。
 * 用于个人中心读取与保存消息通知偏好，字段与前端 notifyConfig 对齐。
 *
 * @author hbsz
 */
@Data
public class NotificationSettingDTO implements Serializable {

    /** 站内通知开关 */
    private Boolean inApp;

    /** 邮件通知开关 */
    private Boolean email;

    /** 短信通知开关 */
    private Boolean sms;

    /** 进度更新通知开关 */
    private Boolean progress;

    /** 任务到期通知开关 */
    private Boolean taskDue;

    /** 文档更新通知开关 */
    private Boolean docUpdate;

    /** 项目归档通知开关 */
    private Boolean archive;

}
