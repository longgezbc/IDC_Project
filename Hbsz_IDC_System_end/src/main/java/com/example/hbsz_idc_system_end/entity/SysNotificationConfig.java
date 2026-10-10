package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 通知配置实体
 * <p>
 * 对应数据库表 sys_notification_config，保存单个用户的消息通知偏好设置，
 * 与用户（sys_user）一对一关联（userId）。各开关字段统一取值：1开启 0关闭。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("sys_notification_config")
public class SysNotificationConfig extends BaseEntity {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID，关联 sys_user.id */
    private Long userId;

    /** 站内通知开关：1开启 0关闭 */
    private Integer inAppNotify;

    /** 邮件通知开关：1开启 0关闭 */
    private Integer emailNotify;

    /** 短信通知开关：1开启 0关闭 */
    private Integer smsNotify;

    /** 进度更新通知开关：1开启 0关闭 */
    private Integer progressNotify;

    /** 任务到期通知开关：1开启 0关闭 */
    private Integer taskDueNotify;

    /** 文档更新通知开关：1开启 0关闭 */
    private Integer docUpdateNotify;

    /** 归档通知开关：1开启 0关闭 */
    private Integer archiveNotify;

}
