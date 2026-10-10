package com.example.hbsz_idc_system_end.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * 带认证的 WebSocket 握手处理器
 * <p>
 * 继承 {@link DefaultHandshakeHandler}，在握手阶段从握手属性中读取
 * 由 {@link com.example.hbsz_idc_system_end.interceptor.JwtHandshakeInterceptor}
 * 写入的 userId，构造以 userId 为 name 的 {@link Principal}。
 * <p>
 * 此 Principal 将绑定到 WebSocket 会话，使 {@code SimpMessagingTemplate.convertAndSendToUser}
 * 能按 userId 精确路由消息到对应用户的订阅通道。
 *
 * @author hbsz
 */
@Component
public class AuthenticatedHandshakeHandler extends DefaultHandshakeHandler {

    /**
     * 确定握手后的用户主体。
     * <p>
     * 从握手属性中取出 userId 字符串，构造为匿名 Principal。
     * 若 attributes 中不存在 userId（未通过 JWT 校验），返回 null。
     *
     * @param request    握手请求
     * @param wsHandler  WebSocket 处理器
     * @param attributes 握手属性（由拦截器填充）
     * @return 以 userId 为 name 的 Principal，或 null
     */
    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        String userId = (String) attributes.get("userId");
        if (userId == null) {
            return null;
        }
        final String name = userId;
        return () -> name;
    }

}
