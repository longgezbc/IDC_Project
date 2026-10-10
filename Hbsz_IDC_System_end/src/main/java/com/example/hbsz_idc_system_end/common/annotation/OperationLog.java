package com.example.hbsz_idc_system_end.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * <p>
 * 标注在 Controller 方法上，由 {@code OperationLogAspect} 切面拦截，
 * 自动采集并将操作日志持久化到数据库，用于业务操作审计。
 *
 * @author hbsz
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 操作模块（如：用户管理、项目管理）
     */
    String module();

    /**
     * 操作类型（如：新增、修改、删除、查询、导出、导入）
     */
    String type();

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否记录请求参数（默认记录）
     */
    boolean recordParams() default true;

    /**
     * 是否记录响应结果（默认不记录，避免大对象）
     */
    boolean recordResult() default false;

    /**
     * 是否在异常时也记录日志（默认记录）
     */
    boolean recordOnError() default true;
}
