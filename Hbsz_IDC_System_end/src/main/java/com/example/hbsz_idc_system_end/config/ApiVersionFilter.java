package com.example.hbsz_idc_system_end.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * API 版本控制过滤器
 * <p>
 * 通过 Servlet {@link Filter} 在响应阶段统一注入 API 版本相关的响应头，
 * 便于客户端识别当前访问的接口版本、系统支持的最新版本以及版本弃用情况。
 * 接口版本通过 URL 前缀区分：/v1/xxx、/v2/xxx。
 * <p>
 * 通过 {@code @Order(0)} 将其置于过滤器链最前，优先于其它过滤器执行。
 *
 * @author hbsz
 */
@Component
@Order(0)
public class ApiVersionFilter implements Filter {

    /**
     * 系统当前（最新）支持的 API 版本，默认 v1，可通过配置 api.version.current 覆盖
     */
    @Value("${api.version.current:v1}")
    private String currentVersion;

    /**
     * 是否开启版本响应头写入，默认开启，可通过配置 api.version.header-enabled 关闭
     */
    @Value("${api.version.header-enabled:true}")
    private boolean headerEnabled;

    /**
     * 过滤逻辑：从请求路径解析版本号并写入响应头后放行请求。
     * <ul>
     *   <li>X-API-Version：本次请求命中的接口版本（无法解析时回退为当前版本）</li>
     *   <li>X-API-Supported-Version：系统当前支持的最新版本</li>
     *   <li>X-API-Deprecated：当请求版本低于当前版本时标记为 true</li>
     * </ul>
     *
     * @param request  Servlet 请求对象
     * @param response Servlet 响应对象
     * @param chain    过滤器链
     * @throws IOException      IO 异常
     * @throws ServletException Servlet 异常
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (headerEnabled) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            // 从请求路径中提取版本号（如 /v1/users -> v1）
            String requestUri = httpRequest.getRequestURI();
            String version = extractVersion(requestUri);

            // 响应头中返回当前接口版本和系统支持的最新版本
            httpResponse.setHeader("X-API-Version", version != null ? version : currentVersion);
            httpResponse.setHeader("X-API-Supported-Version", currentVersion);
            // 弃用标记：当请求的版本低于当前版本时标记
            if (version != null && !version.equals(currentVersion)) {
                httpResponse.setHeader("X-API-Deprecated", "true");
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 从请求 URI 中提取版本号
     * <p>
     * 先剥离 context-path 前缀（/api），再匹配 /v1、/v2 等版本前缀。
     *
     * @param uri 请求 URI
     * @return 解析出的版本号（如 "v1"）；无法解析时返回 null
     */
    private String extractVersion(String uri) {
        if (uri == null || uri.length() < 3) {
            return null;
        }
        // 去除 context-path 前缀（/api）
        String path = uri;
        if (path.startsWith("/api/")) {
            path = path.substring(5);
        } else if (path.startsWith("/api")) {
            path = path.substring(4);
        }
        // 匹配 /v1、/v2 等前缀
        if (path.startsWith("/v") && path.length() >= 3) {
            int slashIdx = path.indexOf('/', 1);
            if (slashIdx > 0) {
                return path.substring(1, slashIdx);
            }
            return path.substring(1);
        }
        return null;
    }
}
