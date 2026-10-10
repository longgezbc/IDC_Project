package com.example.hbsz_idc_system_end.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 * <p>
 * 标注在方法上，由 {@code RateLimitAspect} 切面拦截处理，
 * 基于 Redis 计数的固定时间窗口实现，限制单位时间内的请求次数，防止接口被恶意刷取。
 * 限流维度：已登录按用户，未登录按客户端 IP。
 *
 * @author hbsz
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流 key 前缀，为空时默认使用“类名:方法名”
     */
    String key() default "";

    /**
     * 时间窗口（秒），默认 60 秒
     */
    int period() default 60;

    /**
     * 时间窗口内允许的最大请求次数，默认 10 次，超过则触发限流
     */
    int count() default 10;

}
