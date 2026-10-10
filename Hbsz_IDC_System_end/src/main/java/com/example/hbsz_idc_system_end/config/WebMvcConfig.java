package com.example.hbsz_idc_system_end.config;

import com.example.hbsz_idc_system_end.interceptor.JwtAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 * <p>
 * 实现 {@link WebMvcConfigurer}，作为 Spring MVC 的扩展入口，负责向框架注册自定义组件。
 * 当前主要用于注册 JWT 认证拦截器，并统一维护免认证（白名单）路径，
 * 是整个后端访问控制链路的第一道关卡。
 *
 * @author hbsz
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * JWT 认证拦截器，通过构造器注入，由 Spring 容器管理其生命周期
     */
    private final JwtAuthInterceptor jwtAuthInterceptor;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    /**
     * 注册拦截器
     * <p>
     * 将 {@link JwtAuthInterceptor} 应用到所有请求路径（/**），
     * 同时排除无需登录即可访问的公开路径，包括：登录/刷新令牌、验证码、
     * 系统健康检查与信息、接口文档（doc.html/swagger/knife4j）、favicon 及错误页等。
     * <p>
     * 系统配置接口说明（修复 admin 保存系统配置报"无权限"的 bug）：
     * 不能用 excludePathPatterns 排除 /system/config，因为该排除是路径级、不区分 HTTP 方法的，
     * 会导致 PUT /system/config（updateConfig）也绕过 JWT 认证 → UserContext 未填充 →
     * PermissionAspect 检查时 userId=null → 即使 admin 有权限也被拒绝。
     * 正确做法：在 JwtAuthInterceptor 内部对 GET /system/config 做方法级放行，
     * PUT/POST 仍走完整 JWT 认证流程以填充 UserContext，让 @RequiresPermission 正常工作。
     * <p>
     * 安全说明：Druid 监控（/druid/**）与 Actuator 端点（/actuator/**）已从白名单移除，
     * 必须经过 JWT 认证后才能访问，防止未认证用户获取数据库监控信息或系统运行时指标。
     *
     * @param registry 拦截器注册表，由 Spring MVC 提供
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/refresh",
                        "/captcha",
                        "/system/health",
                        "/system/info",
                        // 安全：移除 /actuator/** 白名单——Actuator 端点暴露运行时指标、
                        // 线程堆栈、环境变量等敏感信息，必须经过 JWT 认证后才能访问。
                        // 生产环境已通过 application-prod.yml 仅暴露 health 端点进一步收敛。
                        "/doc.html",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        // 安全：移除 /druid/** 白名单——Druid 监控页暴露 SQL 执行统计、
                        // 慢查询、数据源连接信息等，未认证访问可导致敏感数据泄露，
                        // 必须经过 JWT 认证后才能访问。
                        "/favicon.ico",
                        "/error",
                        // 前端错误监控上报（sendBeacon 在页面卸载时不携带 token）
                        "/monitor/**",
                        // 通用文件预览接口公开（用于进度填报施工图片直接 <img src> 展示）
                        // 上传接口 /files/upload 仍受 @RequiresPermission("task:progress") 保护
                        // 文件名使用 UUID，几乎不可猜测；IFileStorageService 已内置路径穿越防护
                        "/files/preview/**",
                        // WebSocket 端点（STOMP 握手由 JwtHandshakeInterceptor 单独校验 JWT）
                        "/ws/**"
                );
    }

}
