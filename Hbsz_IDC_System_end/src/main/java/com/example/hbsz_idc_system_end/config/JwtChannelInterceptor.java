package com.example.hbsz_idc_system_end.config;

import com.example.hbsz_idc_system_end.service.IAuthService;
import com.example.hbsz_idc_system_end.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Map;

/**
 * STOMP CONNECT 帧 JWT 鉴权拦截器
 * <p>
 * 安全修复 #23：配合 {@link com.example.hbsz_idc_system_end.interceptor.JwtHandshakeInterceptor}
 * 完成基于 STOMP CONNECT 帧头传递 token 的新鉴权流程。
 * <p>
 * 鉴权优先级：
 * <ol>
 *   <li>STOMP CONNECT 帧的 {@code Authorization} 头（前端主路径，token 不进 URL/日志/历史）；</li>
 *   <li>握手阶段由 JwtHandshakeInterceptor 写入的 attributes（HTTP Authorization 头路径的回退）。</li>
 * </ol>
 * 鉴权通过后将 userId 作为 Principal 绑定到 STOMP 会话，
 * 使 {@code SimpMessagingTemplate.convertAndSendToUser} 可按 userId 精确路由消息。
 * 鉴权失败则中止 CONNECT 帧（返回 null），客户端将收到连接错误。
 *
 * @author hbsz
 */
@Slf4j
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final IAuthService authService;

    public JwtChannelInterceptor(JwtUtils jwtUtils, IAuthService authService) {
        this.jwtUtils = jwtUtils;
        this.authService = authService;
    }

    /**
     * 在消息发送到通道前拦截，仅在 STOMP CONNECT 帧时执行鉴权逻辑。
     *
     * @param message     STOMP 消息
     * @param channel     目标通道
     * @return 鉴权通过返回原消息；鉴权失败返回 null 中止发送
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        // 仅处理 CONNECT 帧，其他帧直接放行
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        // 优先从 STOMP CONNECT 帧头读取 Authorization
        String token = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else {
            token = null;
        }

        // 回退：从握手阶段写入的 attributes 读取 token
        if (!StringUtils.hasText(token)) {
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                Object attrToken = sessionAttributes.get("token");
                if (attrToken instanceof String s) {
                    token = s;
                }
            }
        }

        if (!StringUtils.hasText(token)) {
            log.warn("[WS鉴权] STOMP CONNECT 未携带Token，拒绝连接");
            return null;
        }

        // 检查黑名单
        if (authService.isTokenBlacklisted(token)) {
            log.warn("[WS鉴权] Token已在黑名单中，拒绝连接");
            return null;
        }

        Claims claims = jwtUtils.parseToken(token);
        if (claims == null || claims.getExpiration().before(new java.util.Date())) {
            log.warn("[WS鉴权] Token无效或已过期，拒绝连接");
            return null;
        }

        Object userIdObj = claims.get("userId");
        Long userId = null;
        if (userIdObj instanceof Integer) {
            userId = ((Integer) userIdObj).longValue();
        } else if (userIdObj instanceof Long) {
            userId = (Long) userIdObj;
        }

        if (userId == null) {
            log.warn("[WS鉴权] Token中不含userId，拒绝连接");
            return null;
        }

        // 将 userId 作为 Principal 绑定到 STOMP 会话，供 convertAndSendToUser 按用户路由
        final String userIdStr = String.valueOf(userId);
        accessor.setUser(new Principal() {
            @Override
            public String getName() {
                return userIdStr;
            }
        });
        log.info("[WS鉴权] STOMP CONNECT 认证成功: userId={}", userId);
        return message;
    }
}
