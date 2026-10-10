package com.example.hbsz_idc_system_end.interceptor;

import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.service.IAuthService;
import com.example.hbsz_idc_system_end.utils.JwtUtils;
import com.example.hbsz_idc_system_end.utils.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JWT 认证拦截器
 * <p>
 * 作为受保护接口的统一认证入口，在业务方法执行前校验请求携带的 JWT，
 * 校验通过后把当前用户信息写入 {@link UserContext}（基于 ThreadLocal），
 * 供后续业务与权限切面读取；请求结束后清理 ThreadLocal，防止线程复用导致的用户信息串号。
 * <p>
 * 免认证路径由 {@code WebMvcConfig} 统一在注册拦截器时排除。
 * <p>
 * 性能优化：对 JWT 黑名单检查结果做本地缓存（仅缓存"在黑名单中"的结果），
 * 避免每个请求都查询 Redis。安全权衡：不缓存"不在黑名单"的结果，否则用户登出后
 * 在缓存 TTL 内仍可访问，造成安全风险。
 * <p>
 * 认证失败响应策略：拦截器中认证失败时直接写入 HTTP 401 + 标准 {@link Result} JSON
 * 并返回 false，而不是抛出 {@code BusinessException}。原因：@RestControllerAdvice 的
 * @ExceptionHandler 只能可靠处理 Controller 阶段抛出的异常，拦截器 preHandle 阶段
 * 抛出的异常会穿透异常解析器直达 Tomcat（表现为 ERROR 堆栈 + HTTP 500），导致前端
 * 无法识别 401 并触发 Token 自动刷新。直接写响应同时避免了构造异常堆栈的开销。
 * <p>
 * 日志策略：
 * - 正常通过路径不打印日志（每个请求都过拦截器，INFO 会产生海量日志）
 * - 异常情况（未携带 / 黑名单 / 过期）保留 WARN，便于安全审计
 *
 * @author hbsz
 */
@Slf4j
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final IAuthService authService;
    private final ObjectMapper objectMapper;

    public JwtAuthInterceptor(JwtUtils jwtUtils, IAuthService authService, ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    /**
     * 黑名单本地缓存。
     * <p>
     * 仅缓存"在黑名单中"（true）的结果，键为 token，值为过期时间戳（毫秒）。
     * 不缓存"不在黑名单"（false）的结果——否则用户登出后会在 TTL 内绕过黑名单。
     * <p>
     * 使用 ConcurrentHashMap 保证多线程并发读写的安全性。
     */
    private final Map<String, Long> blacklistCache = new ConcurrentHashMap<>();

    /** 黑名单缓存 TTL（毫秒）。设为 60 秒：足够长以减少 Redis 调用，足够短以减少内存占用 */
    private static final long BLACKLIST_CACHE_TTL_MS = 60_000L;

    /** 缓存最大容量，超过时触发惰性清理 */
    private static final int CACHE_CLEAN_THRESHOLD = 1024;

    /** 写入计数器，用于触发周期性惰性清理 */
    private final AtomicLong writeCounter = new AtomicLong(0);

    /**
     * 请求进入 Controller 之前执行的认证逻辑。
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>OPTIONS 预检请求直接放行；</li>
     *   <li>校验 Authorization 请求头是否存在且以 "Bearer " 开头，缺失则写入 401（UNAUTHORIZED）响应；</li>
     *   <li>截取 Token，检查是否命中登出黑名单，命中则写入 401（TOKEN_EXPIRED）响应；</li>
     *   <li>解析并校验 Token 有效期，无效或过期则写入 401（TOKEN_EXPIRED）响应；</li>
     *   <li>将 userId、username 写入 {@link UserContext}（ThreadLocal）。</li>
     * </ol>
     * 由于 JWT 中 userId 反序列化可能为 Integer 或 Long，此处做了类型兼容转换。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  目标处理器
     * @return true 表示放行；false 表示认证失败，响应已由拦截器直接写入
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 系统配置查询接口公开（登录页需在未登录状态下读取系统名称/主题色等配置）。
        // 仅对 GET /api/system/config 放行且不填充 UserContext（无用户上下文信息）；
        // PUT /system/config（updateConfig）仍需完整 JWT 认证以填充 UserContext，
        // 否则 PermissionAspect 检查 @RequiresPermission("system:config:edit") 时
        // 会因 userId=null 直接拒绝（修复 admin 保存系统配置报"无权限"的 bug）。
        // 注意：不能用 WebMvcConfig.excludePathPatterns 排除 /system/config，因为
        // 该排除是路径级、不区分 HTTP 方法的，会导致 PUT 请求也被错误放行。
        if ("GET".equalsIgnoreCase(request.getMethod())
                && "/api/system/config".equals(request.getRequestURI())) {
            return true;
        }

        String authHeader = request.getHeader(CommonConstant.AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(CommonConstant.TOKEN_PREFIX)) {
            log.warn("[JWT] 请求未携带Token: uri={}", request.getRequestURI());
            writeUnauthorizedResponse(response, ResultCode.UNAUTHORIZED);
            return false;
        }

        String token = authHeader.substring(CommonConstant.TOKEN_PREFIX.length());
        // 检查Token是否在黑名单中（已登出的Token）
        // 优化：先查本地缓存，命中则跳过 Redis 调用；未命中再查 Redis，
        // 命中黑名单后写入本地缓存（仅缓存"在黑名单中"的结果，避免登出绕过）
        if (isTokenBlacklistedCached(token)) {
            log.warn("[JWT] Token已在黑名单中（已登出）: uri={}", request.getRequestURI());
            writeUnauthorizedResponse(response, ResultCode.TOKEN_EXPIRED);
            return false;
        }
        Claims claims = jwtUtils.parseToken(token);
        if (claims == null || claims.getExpiration().before(new java.util.Date())) {
            log.warn("[JWT] Token无效或已过期: uri={}", request.getRequestURI());
            writeUnauthorizedResponse(response, ResultCode.TOKEN_EXPIRED);
            return false;
        }

        // 将用户信息存入ThreadLocal
        Object userIdObj = claims.get("userId");
        Long userId = null;
        if (userIdObj instanceof Integer) {
            userId = ((Integer) userIdObj).longValue();
        } else if (userIdObj instanceof Long) {
            userId = (Long) userIdObj;
        }
        UserContext.setUserId(userId);
        UserContext.setUsername(claims.getSubject());

        return true;
    }

    /**
     * 请求完成后回调（无论正常返回还是抛出异常）。
     * <p>
     * 必须清理 {@link UserContext} 中的 ThreadLocal 数据。由于 Web 容器采用线程池复用线程，
     * 若不清理，下一个复用该线程的请求可能读到上一请求残留的用户信息，造成越权与数据串号，
     * 同时也可避免 ThreadLocal 引发的内存泄漏。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  目标处理器
     * @param ex       处理过程中抛出的异常，可能为 null
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 认证失败时直接向客户端写入标准错误响应。
     * <p>
     * 写入 HTTP 401 状态码 + 与 {@code GlobalExceptionHandler} 输出格式完全一致的
     * {@link Result} JSON 体（前端依据 HTTP 401 或业务码 401/2001/2002 触发 Token
     * 刷新流程，两种识别方式均兼容）。
     * <p>
     * 不通过抛出 {@code BusinessException} 交给全局异常处理器的原因：
     * {@code @RestControllerAdvice} 的 {@code @ExceptionHandler} 只能可靠处理
     * Controller 阶段抛出的异常，拦截器 preHandle 阶段抛出的异常会穿透异常
     * 解析器直达 Tomcat，产生 ERROR 堆栈日志且响应退化为 HTTP 500 默认错误页。
     * <p>
     * 防御性检查：响应已提交时跳过写入，避免二次写入抛 IllegalStateException。
     *
     * @param response   HTTP 响应
     * @param resultCode 业务错误码（UNAUTHORIZED=401 / TOKEN_EXPIRED=2002）
     */
    private void writeUnauthorizedResponse(HttpServletResponse response, ResultCode resultCode) {
        if (response.isCommitted()) {
            return;
        }
        try {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Result.error(resultCode)));
            response.getWriter().flush();
        } catch (IOException e) {
            // 客户端断开连接等 IO 异常：仅记录 WARN，不影响请求结束流程
            log.warn("[JWT] 写入认证失败响应时出现IO异常: {}", e.getMessage());
        }
    }

    /**
     * 检查 Token 是否在黑名单中（带本地缓存）。
     * <p>
     * 检查流程：
     * <ol>
     *   <li>先查本地缓存：如果缓存中存在且未过期，直接返回 true（命中黑名单）；</li>
     *   <li>缓存未命中时查询 Redis（authService.isTokenBlacklisted）；</li>
     *   <li>如果在黑名单中（true），写入本地缓存，TTL 为 60 秒；</li>
     *   <li>不在黑名单（false）则不写入缓存——避免用户登出后在 TTL 内绕过黑名单。</li>
     * </ol>
     * <p>
     * 安全权衡：仅缓存 true 的结果。这意味着合法的 token 每次请求仍会查询 Redis，
     * 但被攻击或重试的已登出 token 会因缓存命中而跳过 Redis 调用，大幅降低 Redis 压力。
     *
     * @param token 待检查的 JWT
     * @return true 表示在黑名单中（已登出），false 表示有效
     */
    private boolean isTokenBlacklistedCached(String token) {
        // 1. 先查本地缓存
        Long expireAt = blacklistCache.get(token);
        if (expireAt != null) {
            if (expireAt > System.currentTimeMillis()) {
                // 缓存命中且未过期，直接返回 true
                return true;
            }
            // 已过期，移除过期项
            blacklistCache.remove(token, expireAt);
        }

        // 2. 缓存未命中，查询 Redis
        boolean blacklisted = authService.isTokenBlacklisted(token);
        if (blacklisted) {
            // 3. 仅缓存"在黑名单中"的结果，避免登出绕过
            blacklistCache.put(token, System.currentTimeMillis() + BLACKLIST_CACHE_TTL_MS);
            // 触发惰性清理：每写入 256 次清理一次过期项，防止内存无限增长
            if (writeCounter.incrementAndGet() % 256 == 0) {
                cleanExpiredCacheEntries();
            }
        }
        return blacklisted;
    }

    /**
     * 清理过期的缓存项。
     * <p>
     * 由于使用 ConcurrentHashMap，遍历期间其他线程可继续读写，不会阻塞业务请求。
     * 清理操作是 O(n) 的，但只在写入计数达到阈值时触发，对性能影响可忽略。
     */
    private void cleanExpiredCacheEntries() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = blacklistCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (entry.getValue() <= now) {
                it.remove();
            }
        }
    }

}
