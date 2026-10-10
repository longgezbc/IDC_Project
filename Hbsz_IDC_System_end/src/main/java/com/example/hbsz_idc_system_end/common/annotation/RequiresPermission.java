package com.example.hbsz_idc_system_end.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 * <p>
 * 用于 Controller 方法上，声明访问该接口所需的权限标识，由 {@code PermissionAspect}
 * 切面在方法执行前进行鉴权。
 * <p>
 * 逻辑：默认（logicalAnd=true）当前用户必须同时拥有所有指定的权限标识才可访问，
 * 否则抛出 NO_PERMISSION 异常；logicalAnd=false 时满足任一权限即可。
 * 超级管理员（role_code = admin）拥有全部权限，直接放行。
 * <p>
 * 岗位职级校验（requirePosition）：可选字段，声明该方法 additionally 要求的岗位编码集合。
 * 当该数组非空时，切面在功能权限校验通过后，额外校验当前用户绑定的岗位编码是否在列表中，
 * 不在则抛出 FORBIDDEN 异常（"岗位职级不足，无权操作"）。超级管理员（admin）跳过岗位校验。
 *
 * @author hbsz
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * 权限标识，支持多个，默认多个之间为 AND 关系
     */
    String[] value();

    /**
     * 多个权限标识的逻辑关系：true 为逻辑与（AND，默认），false 为逻辑或（OR）
     */
    boolean logicalAnd() default true;

    /**
     * 额外要求的岗位编码集合（可选，默认为空表示不校验岗位）。
     * <p>
     * 非空时，切面在功能权限校验通过后，校验当前用户绑定的岗位编码是否在列表中。
     * 例如：requirePosition = {"pm", "senior_engineer"} 表示仅项目经理与高级工程师可访问。
     * 超级管理员（admin）跳过岗位校验。
     */
    String[] requirePosition() default {};

}
