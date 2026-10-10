package com.example.hbsz_idc_system_end.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * 跨域资源共享(CORS)配置类
 * <p>
 * 通过注册全局 {@link CorsFilter}，允许前端应用以跨域方式访问后端 API。
 * 开发环境默认放开所有源，生产环境通过 {@code hbsz.cors.allowed-origins} 指定
 * 具体白名单域名，兼顾开发便利与生产安全。
 *
 * @author hbsz
 */
@Slf4j
@Configuration
public class CorsConfig {

    /**
     * 允许的源列表，逗号分隔。
     * 开发环境默认 "*"；生产环境通过环境变量/配置文件指定具体域名，如：
     *   hbsz.cors.allowed-origins=https://hbsz.example.com,https://www.hbsz.example.com
     */
    @Value("${hbsz.cors.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * 构建全局 CORS 过滤器。
     * <p>
     * 关键配置：
     * <ul>
     *   <li>allowCredentials=true：允许携带 Cookie 等凭证</li>
     *   <li>allowedOrigin(Pattern)：源为 "*" 时用 addAllowedOriginPattern 放开所有源
     *       （与凭证共存），否则按配置精确限定域名</li>
     *   <li>allowedHeader/exposedHeader="*"：放开全部请求头与响应头</li>
     *   <li>allowedMethod：允许 GET/POST/PUT/DELETE/OPTIONS/PATCH</li>
     *   <li>maxAge=3600：预检请求(OPTIONS)结果缓存 1 小时，减少预检开销</li>
     * </ul>
     * 该配置对所有路径（/**）生效。
     *
     * @return 应用到所有路径的 {@link CorsFilter}
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // 允许凭证（Cookie）
        config.setAllowCredentials(true);

        // 安全：生产环境必须通过环境变量 CORS_ALLOWED_ORIGINS 指定白名单域名，禁止使用 *
        // 说明：allowCredentials=true 与通配符 * 同时启用是危险的，任意站点都能携带 Cookie
        // 跨域访问本系统接口，存在 CSRF 与会话劫持风险。仅在开发环境允许使用 *。
        if ("*".equals(allowedOrigins.trim())) {
            // 开发环境：允许所有源。但 allowCredentials=true + * 存在安全隐患，
            // 此处打印 WARN 告警，提醒运维人员生产环境必须显式配置白名单域名。
            log.warn("[CORS][安全告警] 当前允许来源为通配符 * 且 allowCredentials=true，"
                    + "此配置仅可用于开发环境！生产环境请通过环境变量 CORS_ALLOWED_ORIGINS "
                    + "指定显式白名单域名（逗号分隔），禁止使用 *。");
            config.addAllowedOriginPattern("*");
        } else {
            // 生产环境：限制具体域名
            Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(config::addAllowedOrigin);
        }

        // 允许的请求头
        config.addAllowedHeader("*");

        // 暴露的响应头
        config.addExposedHeader("*");

        // 允许的HTTP方法
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");
        config.addAllowedMethod("PATCH");

        // 预检请求缓存时间（秒）
        config.setMaxAge(3600L);

        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

}
