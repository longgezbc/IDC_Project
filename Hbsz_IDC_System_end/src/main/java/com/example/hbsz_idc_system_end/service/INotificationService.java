package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.NotificationQueryDTO;
import com.example.hbsz_idc_system_end.dto.NotificationSendDTO;
import com.example.hbsz_idc_system_end.dto.NotificationVO;
import com.example.hbsz_idc_system_end.dto.PageResult;

import java.util.List;

/**
 * 站内通知服务接口
 * <p>
 * 定义站内通知的核心能力：列表查询、未读统计、标记已读、删除，
 * 以及供业务模块调用的统一投递入口 {@link #sendNotification}。
 * </p>
 *
 * @author hbsz
 */
public interface INotificationService {

    /**
     * 分页查询当前登录用户的通知列表。
     *
     * @param query 查询条件（含 notifyType / isRead 筛选与分页参数）
     * @return 分页通知列表
     */
    PageResult<NotificationVO> queryPage(NotificationQueryDTO query);

    /**
     * 获取当前登录用户的未读通知数。
     *
     * @return 未读数量
     */
    int getUnreadCount();

    /**
     * 标记单条通知为已读。
     * <p>
     * 仅允许标记属于自己的通知，越权标记将被忽略。
     *
     * @param id 通知ID
     */
    void markAsRead(Long id);

    /**
     * 将当前用户全部未读通知标记为已读。
     */
    void markAllAsRead();

    /**
     * 删除单条通知。
     * <p>
     * 仅允许删除属于自己的通知。
     *
     * @param id 通知ID
     */
    void delete(Long id);

    /**
     * 投递一条通知（业务模块统一调用入口）。
     * <p>
     * 流程：
     * <ol>
     *   <li>读取目标用户通知偏好（sys_notification_config），检查对应类型开关；</li>
     *   <li>若用户关闭了该类型或站内通知，则跳过（不写入不推送）；</li>
     *   <li>写入 sys_notification 表；</li>
     *   <li>通过 WebSocket 实时推送给目标用户（若在线）。</li>
     * </ol>
     * <p>
     * 注意：此方法不抛异常给业务调用方——通知投递失败不应影响主业务流程。
     *
     * @param dto 通知内容
     */
    void sendNotification(NotificationSendDTO dto);

    /**
     * 批量投递通知。
     * <p>
     * 使用批量插入提升性能，适用于一次需要向多个用户发送相同或类似通知的场景。
     * <p>
     * 流程：
     * <ol>
     *   <li>逐个检查目标用户通知偏好，过滤掉已关闭对应类型通知的用户；</li>
     *   <li>使用批量插入一次性写入所有符合条件的通知；</li>
     *   <li>逐个通过 WebSocket 实时推送给在线用户。</li>
     * </ol>
     * <p>
     * 注意：此方法不抛异常给业务调用方——通知投递失败不应影响主业务流程。
     *
     * @param dtoList 通知内容列表
     */
    void sendNotifications(List<NotificationSendDTO> dtoList);

}
