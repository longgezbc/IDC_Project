package com.example.hbsz_idc_system_end.interceptor;

import com.example.hbsz_idc_system_end.service.IAuthService;
import com.example.hbsz_idc_system_end.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * WebSocket 握手 JWT 认证拦截器
 * <p>
 * STOMP 握手阶段拦截请求，从 Authorization 请求头中提取 JWT，
 * 校验有效后将 userId / username 写入握手属性（attributes），
 * 供自定义 {@code HandshakeHandler} 读取以构造 {@link java.security.Principal}。
 * <p>
 * 安全修复 #23：原实现优先从 URL 查询参数 {@code ?token=xxx} 读取 token，
 * 该方式会泄露到服务端访问日志、浏览器历史记录、Referer 头中，存在 token 泄露风险。
 * 修复策略：
 * <ul>
 *   <li>移除从 URL 查询参数读取 token 的逻辑；</li>
 *   <li>仅从 HTTP Authorization 头读取 token（无法被日志/历史记录捕获）；</li>
 *   <li>若握手阶段未携带 token（前端改为通过 STOMP CONNECT 帧头传递），
 *       不再拒绝握手，放行让 {@code JwtChannelInterceptor} 在 CONNECT 帧阶段完成鉴权。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtils jwtUtils;
    private final IAuthService authService;

    public JwtHandshakeInterceptor(JwtUtils jwtUtils, IAuthService authService) {
        this.jwtUtils = jwtUtils;
        this.authService = authService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();

        // 安全修复 #23：移除从 URL 查询参数 token 读取的逻辑，避免 token 泄露到日志/历史/Referer
        // 仅从 HTTP Authorization 头获取 token
        String token = null;
        String authHeader = httpRequest.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 握手阶段未携带 token：不拒绝握手，放行让 JwtChannelInterceptor 在 STOMP CONNECT 帧阶段完成鉴权
        // （前端已改用 STOMP CONNECT 帧头传递 token）
        if (!StringUtils.hasText(token)) {
            log.debug("[WS握手] 握手阶段未携带Token，将在STOMP CONNECT帧鉴权: uri={}", httpRequest.getRequestURI());
            return true;
        }

        // 检查黑名单
        if (authService.isTokenBlacklisted(token)) {
            log.warn("[WS握手] Token已在黑名单中，拒绝连接");
            return false;
        }

        Claims claims = jwtUtils.parseToken(token);
        if (claims == null || claims.getExpiration().before(new java.util.Date())) {
            log.warn("[WS握手] Token无效或已过期，拒绝连接");
            return false;
        }

        Object userIdObj = claims.get("userId");
        Long userId = null;
        if (userIdObj instanceof Integer) {
            userId = ((Integer) userIdObj).longValue();
        } else if (userIdObj instanceof Long) {
            userId = (Long) userIdObj;
        }

        if (userId == null) {
            log.warn("[WS握手] Token中不含userId，拒绝连接");
            return false;
        }

        // 写入握手属性，供 AuthenticatedHandshakeHandler 读取以构造 Principal
        String userIdStr = String.valueOf(userId);
        String username = claims.getSubject();
        attributes.put("userId", userIdStr);
        attributes.put("username", username);
        // 缓存 token，供 JwtChannelInterceptor 在 CONNECT 帧阶段回退使用
        attributes.put("token", token);
        log.info("[WS握手] 认证成功: userId={}, username={}", userId, username);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需后置处理
    }

}
