package com.example.hbsz_idc_system_end.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等性注解
 * <p>
 * 标注在方法（通常为 Controller 接口）上，由 {@code IdempotentAspect} 切面拦截处理，
 * 基于 Redis 实现，防止用户重复提交造成重复数据。
 * 前端需在请求头中携带唯一请求标识（默认 X-Request-Id）。
 *
 * @author hbsz
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 幂等 key 前缀，默认使用“类名:方法名”
     */
    String key() default "";

    /**
     * 幂等保持时间（秒），默认 300 秒（5 分钟），期间重复请求会被拒绝
     */
    int expire() default 300;

    /**
     * 携带请求唯一标识的请求头名称，默认 X-Request-Id
     */
    String headerName() default "X-Request-Id";

}
