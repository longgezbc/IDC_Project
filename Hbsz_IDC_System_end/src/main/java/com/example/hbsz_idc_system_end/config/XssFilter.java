package com.example.hbsz_idc_system_end.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XSS 防护过滤器
 * <p>
 * 通过 {@link Filter} 对进入系统的 HTTP 请求进行包装，对请求参数、请求头等文本内容
 * 进行 HTML 特殊字符转义，从源头拦截存储型/反射型 XSS 攻击。
 * 通过 {@code @Order(1)} 使其在 {@link ApiVersionFilter} 之后执行。
 * <p>
 * M6修复说明：本过滤器仅覆盖 URL 参数、表单参数和请求头，不覆盖 @RequestBody JSON 体。
 * JSON 体的 XSS 防护由前端输出编码和后端 @Valid 校验保证。
 *
 * @author hbsz
 */
@Component
@Order(1)
public class XssFilter implements Filter {

    /**
     * M6修复：使用 {@link XssHttpServletRequestWrapper} 包装原始请求后放行，
     * 使后续读取参数/请求头时自动完成 XSS 转义。
     * <p>
     * 文件上传（multipart/form-data）请求直接放行不包装，避免包装器影响 multipart 解析
     * （原实现两个分支逻辑相同属冗余代码）。
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
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String contentType = httpRequest.getContentType();
        if (contentType != null && contentType.contains("multipart/form-data")) {
            // M6修复：multipart 文件上传请求直接放行，不使用包装器，避免影响 multipart 解析
            chain.doFilter(httpRequest, response);
        } else {
            chain.doFilter(new XssHttpServletRequestWrapper(httpRequest), response);
        }
    }

    /**
     * XSS 请求包装类
     * <p>
     * 继承 {@link HttpServletRequestWrapper}，重写参数与请求头的读取方法，
     * 在返回值前统一进行 HTML 转义，实现对上层业务无感知的 XSS 清洗。
     */
    public static class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

        private byte[] body;

        public XssHttpServletRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        /**
         * 获取单个请求参数并进行 XSS 转义
         *
         * @param name 参数名
         * @return 转义后的参数值，原值为 null 时返回 null
         */
        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return value == null ? null : cleanXss(value);
        }

        /**
         * 获取同名的多个请求参数并逐一进行 XSS 转义
         *
         * @param name 参数名
         * @return 转义后的参数值数组，原值为 null 时返回 null
         */
        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) {
                return null;
            }
            String[] escapedValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                escapedValues[i] = cleanXss(values[i]);
            }
            return escapedValues;
        }

        /**
         * 获取全部请求参数映射，值均已完成 XSS 转义
         *
         * @return 参数名到（转义后的）参数值数组的映射
         */
        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> map = new LinkedHashMap<>();
            Enumeration<String> names = super.getParameterNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                map.put(name, getParameterValues(name));
            }
            return map;
        }

        /**
         * 获取请求头并进行 XSS 转义
         *
         * @param name 请求头名称
         * @return 转义后的请求头值，原值为 null 时返回 null
         */
        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            return value == null ? null : cleanXss(value);
        }

        /**
         * 简单的 XSS 清洗：转义 HTML 特殊字符（&lt; &gt; &amp; &quot; 单引号）
         *
         * @param value 原始文本
         * @return 转义后的文本；入参为 null 或空串时原样返回
         */
        private static String cleanXss(String value) {
            if (value == null || value.isEmpty()) {
                return value;
            }
            // 转义 HTML 特殊字符
            StringBuilder sb = new StringBuilder(value.length() * 2);
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '<':
                        sb.append("&lt;");
                        break;
                    case '>':
                        sb.append("&gt;");
                        break;
                    case '&':
                        sb.append("&amp;");
                        break;
                    case '"':
                        sb.append("&quot;");
                        break;
                    case '\'':
                        sb.append("&#39;");
                        break;
                    default:
                        sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}
