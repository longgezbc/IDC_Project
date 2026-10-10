package com.example.hbsz_idc_system_end.config;

import com.example.hbsz_idc_system_end.interceptor.JwtHandshakeInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * WebSocket 消息代理配置
 * <p>
 * 基于 STOMP 协议 + SockJS 实现，用于站内通知的实时推送。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>端点：{@code /ws}，前端通过 SockJS 连接，鉴权 token 优先通过 STOMP CONNECT 帧头
 *       {@code Authorization} 传递（由 {@link JwtChannelInterceptor} 校验），
 *       握手阶段 HTTP Authorization 头作为回退（由 {@link JwtHandshakeInterceptor} 注入属性）；
 *       不再使用 URL 查询参数 {@code ?token=xxx} 避免 token 泄露到日志/历史；</li>
 *   <li>消息代理前缀：{@code /topic}（广播）与 {@code /queue}（用户点对点），
 *       前端订阅 {@code /user/queue/notifications} 接收个人通知；</li>
 *   <li>应用目的地前缀：{@code /app}，前端发送消息到服务端的前缀。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final AuthenticatedHandshakeHandler handshakeHandler;
    private final JwtChannelInterceptor jwtChannelInterceptor;

    /**
     * M5修复：复用与 CorsConfig 相同的 CORS 白名单配置。
     * 开发环境默认 "*"；生产环境通过环境变量/配置文件 hbsz.cors.allowed-origins 指定具体域名。
     */
    @Value("${hbsz.cors.allowed-origins:*}")
    private String allowedOrigins;

    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor,
                           AuthenticatedHandshakeHandler handshakeHandler,
                           JwtChannelInterceptor jwtChannelInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.handshakeHandler = handshakeHandler;
        this.jwtChannelInterceptor = jwtChannelInterceptor;
    }

    /**
     * 注册 STOMP 端点
     * <p>
     * {@code /ws} 为 SockJS 端点。M5修复：不再硬编码允许所有源（"*"），
     * 改为读取 {@code hbsz.cors.allowed-origins} 配置，与 {@link CorsConfig} 复用同一白名单，
     * 开发环境默认 "*"，生产环境从环境变量读取具体域名，避免 WebSocket 通道被任意源连接。
     * 握手拦截器校验 JWT 并写入 userId 到 attributes；
     * {@link AuthenticatedHandshakeHandler} 读取 userId 构造 Principal，
     * 供 {@code convertAndSendToUser} 按用户精确推送。
     *
     * @param registry STOMP 端点注册表
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // M5修复：将逗号分隔的允许源字符串转为数组，传给 setAllowedOriginPatterns
        String[] originPatterns = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
        if (originPatterns.length == 0) {
            originPatterns = new String[]{"*"};
        }
        // 安全告警：当配置为通配符 * 时，任意源均可连接 WebSocket，仅适用于开发环境
        if (Arrays.asList(originPatterns).contains("*")) {
            log.warn("[WebSocket][安全告警] 当前 allowedOrigins 为通配符 *，任意源均可连接 WebSocket，"
                    + "此配置仅可用于开发环境！生产环境请通过环境变量指定显式白名单域名。");
        }
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(originPatterns)
                .addInterceptors(jwtHandshakeInterceptor)
                .setHandshakeHandler(handshakeHandler)
                .withSockJS();
    }

    /**
     * 配置消息代理
     * <ul>
     *   <li>服务端推送前缀：{@code /topic}（广播）、{@code /queue}（点对点）；
     *       用户专属通道为 {@code /user/{userId}/queue/notifications}；</li>
     *   <li>客户端发送前缀：{@code /app}。</li>
     * </ul>
     *
     * @param config 消息代理注册配置
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        // 用户点对点消息目的地前缀，配合 convertAndSendToUser 使用
        config.setUserDestinationPrefix("/user");
    }

    /**
     * 配置客户端入站通道
     * <p>
     * 安全修复 #23：注册 {@link JwtChannelInterceptor}，在 STOMP CONNECT 帧阶段完成 JWT 鉴权。
     * 配合 {@link JwtHandshakeInterceptor} 实现「CONNECT 帧头优先、握手属性回退」的双层鉴权，
     * 使前端可通过 STOMP CONNECT 帧头传递 token，避免 token 出现在 URL 查询参数中。
     *
     * @param registration 客户端入站通道注册配置
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }

}
