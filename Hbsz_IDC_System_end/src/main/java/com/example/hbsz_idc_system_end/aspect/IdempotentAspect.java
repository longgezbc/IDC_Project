package com.example.hbsz_idc_system_end.aspect;

import com.example.hbsz_idc_system_end.common.annotation.Idempotent;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.utils.RedisUtils;
import com.example.hbsz_idc_system_end.utils.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Method;

/**
 * 幂等性切面
 * <p>
 * 拦截标注了 {@link Idempotent} 的方法，基于 Redis 实现接口幂等，
 * 防止用户重复提交（如网络重试、快速双击）造成的重复数据。
 * <p>
 * 实现思路：以幂等 key（统一前缀 {@code hbsz:idempotent:}）利用 Redis 的
 * setIfAbsent（SET NX）原子占位，首个请求占位成功后放行；重复请求占位失败即拒绝。
 * 业务异常时删除占位 key，允许重试。
 * <p>
 * 幂等 key 的组成（安全修复：原实现缺少请求 ID 时直接放行，导致所有未携带
 * X-Request-Id 头的客户端完全绕过幂等保护，防重复提交形同虚设）：
 * <ul>
 *   <li>携带 X-Request-Id 请求头：前缀 + 请求 ID + 用户ID（原有行为）；</li>
 *   <li>未携带请求头：前缀 + 方法参数内容摘要（SHA-256）+ 用户ID 作为兜底，
 *       同一用户在有效期内提交完全相同的参数视为重复提交被拒绝；
 *       参数不同（含合法的再次创建）则正常放行，无需前端改造。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Aspect
@Component
public class IdempotentAspect {

    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    public IdempotentAspect(RedisUtils redisUtils, ObjectMapper objectMapper) {
        this.redisUtils = redisUtils;
        this.objectMapper = objectMapper;
    }

    /**
     * 环绕通知：对被拦截方法执行幂等控制。
     * <p>
     * 占位成功后执行业务，业务抛异常则删除幂等 key 允许下次重试，
     * 业务成功则保留 key 直至过期。
     *
     * @param point 连接点
     * @return 目标业务方法的返回值
     * @throws Throwable 业务方法抛出的异常；重复提交时抛出 {@link BusinessException}
     */
    @Around("@annotation(com.example.hbsz_idc_system_end.common.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        // 幂等键要素：优先使用请求 ID，缺失时退化为方法参数内容摘要
        String requestId = getRequestId(idempotent.headerName());
        if (requestId == null || requestId.isEmpty()) {
            // 安全修复：原实现此处直接 return point.proceed() 放行，幂等保护被完全绕过。
            // 改为基于参数内容摘要生成兜底请求 ID，保证未携带请求头的客户端同样受幂等保护
            requestId = "args:" + digestArgs(point);
        }

        // 构建幂等 key
        String key = buildKey(idempotent, method, requestId);

        // 尝试获取锁（setIfAbsent）
        boolean acquired = redisUtils.setIfAbsent(key, "1", idempotent.expire(), TimeUnit.SECONDS);
        if (!acquired) {
            log.warn("[幂等] 重复请求被拒绝: key={}", key);
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "请勿重复提交，请稍后再试");
        }

        try {
            return point.proceed();
        } catch (Throwable e) {
            // 业务异常时删除幂等 key，允许重试
            redisUtils.delete(key);
            throw e;
        }
    }

    /**
     * 计算方法参数的内容摘要（SHA-256 十六进制前 16 位）。
     * <p>
     * 将参数序列化为 JSON 后取哈希；对 {@link MultipartFile}、Servlet 对象等
     * 不可序列化类型仅记录类名占位，避免序列化异常。
     * 无参数方法返回固定串（此时幂等语义退化为“有效期内仅允许调用一次”，
     * 与原请求 ID 缺失场景一致，由调用频率与 expire 配置约束）。
     *
     * @param point 连接点
     * @return 参数内容摘要十六进制串
     */
    private String digestArgs(ProceedingJoinPoint point) {
        try {
            Object[] args = point.getArgs();
            if (args == null || args.length == 0) {
                return "noargs";
            }
            StringBuilder sb = new StringBuilder("[");
            for (Object arg : args) {
                if (arg instanceof MultipartFile || arg instanceof HttpServletRequest
                        || arg instanceof HttpServletResponse) {
                    sb.append("{\"type\":\"").append(arg.getClass().getSimpleName()).append("\"}");
                } else {
                    try {
                        sb.append(objectMapper.writeValueAsString(arg));
                    } catch (Exception e) {
                        sb.append("{\"unserializable\":\"").append(arg.getClass().getName()).append("\"}");
                    }
                }
                sb.append(",");
            }
            sb.append("]");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) { // 取前 8 字节（16 个十六进制字符）即可满足去重需求
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            log.warn("[幂等] 参数摘要计算失败，退化为时间随机值（本次不生效兜底保护）", e);
            return Long.toString(System.nanoTime());
        }
    }

    /**
     * 从当前请求头中获取幂等请求 ID。
     *
     * @param headerName 请求头名称（由注解配置，默认 X-Request-Id）
     * @return 请求 ID；无请求上下文或获取失败时返回 null
     */
    private String getRequestId(String headerName) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader(headerName);
            }
        } catch (Exception e) {
            log.warn("获取请求ID失败", e);
        }
        return null;
    }

    /**
     * 构造 Redis 幂等 key。
     * <p>
     * 格式为 {@code hbsz:idempotent:<前缀或类名:方法名>:<requestId>[:u:<userId>]}，
     * 未指定注解 key 时以“类名:方法名”作为前缀，并追加当前用户 ID 以隔离不同用户的幂等空间。
     * 重构：统一前缀为 hbsz: 命名空间，与系统其它缓存键对齐。
     *
     * @param idempotent 幂等注解
     * @param method     被拦截的方法
     * @param requestId  请求 ID（或参数摘要兜底值）
     * @return 完整的 Redis 幂等 key
     */
    private String buildKey(Idempotent idempotent, Method method, String requestId) {
        // 重构：统一 Redis 键前缀为 hbsz: 命名空间（hbsz:idempotent:）
        StringBuilder sb = new StringBuilder(CommonConstant.REDIS_IDEMPOTENT_PREFIX);
        if (!idempotent.key().isEmpty()) {
            sb.append(idempotent.key());
        } else {
            sb.append(method.getDeclaringClass().getSimpleName()).append(":").append(method.getName());
        }
        sb.append(":").append(requestId);

        // 加入用户 ID，隔离不同用户的幂等
        Long userId = UserContext.getUserId();
        if (userId != null) {
            sb.append(":u:").append(userId);
        }
        return sb.toString();
    }
}
