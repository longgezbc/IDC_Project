package com.example.hbsz_idc_system_end.aspect;

import com.example.hbsz_idc_system_end.common.annotation.RateLimit;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.utils.IpUtils;
import com.example.hbsz_idc_system_end.utils.RedisUtils;
import com.example.hbsz_idc_system_end.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 限流切面
 * <p>
 * 拦截标注了 {@link RateLimit} 的方法，基于 Redis 计数实现固定时间窗口限流，
 * 保护接口在高并发下不被刷爆。
 * <p>
 * 实现思路：为“方法 + 用户/IP”生成限流 key，每次访问对该 key 计数自增（INCR），
 * 首次计数时设置窗口过期时间；当计数超过阈值时拒绝请求并抛出限流异常。
 *
 * @author hbsz
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private final RedisUtils redisUtils;
    private final IpUtils ipUtils;

    public RateLimitAspect(RedisUtils redisUtils, IpUtils ipUtils) {
        this.redisUtils = redisUtils;
        this.ipUtils = ipUtils;
    }

    /**
     * 环绕通知：对被拦截方法执行限流控制。
     *
     * @param point 连接点
     * @return 目标业务方法的返回值
     * @throws Throwable 业务方法抛出的异常；超过限流阈值时抛出 {@link BusinessException}
     */
    @Around("@annotation(com.example.hbsz_idc_system_end.common.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        // 构建限流 key：前缀 + 用户ID + IP + 方法名
        String key = buildKey(rateLimit, method);
        // 重构：统一 Redis 键前缀为 hbsz: 命名空间，避免 rate_limit: 散落键与系统其它缓存隔离
        String redisKey = CommonConstant.REDIS_RATE_LIMIT_PREFIX + key;

        // 原子计数 +1 并在首次访问时设置过期时间
        // 修复并发问题：原实现为 increment + expire 两步操作，进程在两步之间崩溃
        // 会导致 key 永不过期，限流永久生效。改为 Lua 脚本原子执行后无此风险。
        long count = redisUtils.incrementWithExpire(redisKey, rateLimit.period());

        if (count > rateLimit.count()) {
            log.warn("[限流] 接口访问超限: key={}, count={}/{}", key, count, rateLimit.count());
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }

        return point.proceed();
    }

    /**
     * 构造限流维度 key。
     * <p>
     * 前缀取注解 key 或“类名:方法名”；已登录用户按 userId 维度限流，
     * 未登录用户则退化为按客户端 IP 维度限流。
     *
     * @param rateLimit 限流注解
     * @param method    被拦截的方法
     * @return 限流 key（不含 "hbsz:rate_limit:" 前缀，前缀在调用处拼接）
     */
    private String buildKey(RateLimit rateLimit, Method method) {
        StringBuilder sb = new StringBuilder();
        // 前缀
        if (!rateLimit.key().isEmpty()) {
            sb.append(rateLimit.key()).append(":");
        } else {
            sb.append(method.getDeclaringClass().getSimpleName()).append(":").append(method.getName()).append(":");
        }
        // 用户 ID
        Long userId = UserContext.getUserId();
        if (userId != null) {
            sb.append("u:").append(userId);
        } else {
            // 未登录用户使用 IP（IpUtils 内含可信代理校验，防止伪造 X-Forwarded-For 绕过限流）
            String ip = getClientIp();
            sb.append("ip:").append(ip);
        }
        return sb.toString();
    }

    /**
     * 获取客户端真实 IP（委托 {@link IpUtils}，含可信代理校验）。
     * <p>
     * 安全修复 #22：默认使用 Servlet 容器感知到的 {@code remoteAddr}（TCP 直连对端 IP，无法伪造）；
     * 仅当 remoteAddr 命中配置 {@code hbsz.security.trusted-proxies} 中的受信代理白名单时，
     * 才允许回退到 X-Forwarded-For / X-Real-IP（取第一个 IP，处理多级代理场景）。
     *
     * @return 客户端 IP；无法获取时返回 "unknown"
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return ipUtils.getClientIp(request);
            }
        } catch (Exception e) {
            log.warn("获取客户端IP失败", e);
        }
        return "unknown";
    }
}
