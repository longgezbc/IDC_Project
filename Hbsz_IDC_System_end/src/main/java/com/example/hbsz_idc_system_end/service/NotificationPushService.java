package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.NotificationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 通知 WebSocket 推送服务
 * <p>
 * 封装 {@link SimpMessagingTemplate}，将通知消息按 userId 推送到对应用户的
 * STOMP 订阅通道 {@code /user/{userId}/queue/notifications}。
 * <p>
 * 用户未在线时推送消息将被 SimpleBroker 丢弃，但通知已持久化到数据库，
 * 用户下次拉取列表时仍可看到，保证消息不丢。
 * <p>
 * 独立为单独 Service 的目的：隔离 WebSocket 依赖，便于在无 WebSocket 场景下
 * （如定时任务、非 Web 请求线程）安全调用。
 *
 * @author hbsz
 */
@Slf4j
@Service
public class NotificationPushService {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationPushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 向指定用户推送通知。
     * <p>
     * 使用 {@code convertAndSendToUser}，消息将路由到以 userId 为 Principal name
     * 的 WebSocket 会话的 {@code /queue/notifications} 订阅通道。
     * 若用户当前无活动会话，消息将被丢弃（无副作用）。
     *
     * @param userId       目标用户ID
     * @param notification 通知内容（VO）
     */
    public void pushToUser(Long userId, NotificationVO notification) {
        if (userId == null || notification == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/notifications",
                    notification
            );
            log.debug("[通知推送] 推送成功: userId={}, notificationId={}", userId, notification.getId());
        } catch (Exception e) {
            // 推送失败不影响主流程，仅记录日志
            log.warn("[通知推送] 推送失败（用户可能离线）: userId={}, notificationId={}, error={}",
                    userId, notification.getId(), e.getMessage());
        }
    }

}
