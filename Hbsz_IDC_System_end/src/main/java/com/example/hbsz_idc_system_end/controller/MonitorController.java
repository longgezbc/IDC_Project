package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RateLimit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 前端错误监控接收端点
 * <p>
 * 接收前端 errorReporter 上报的 JS 错误、Promise 异常、资源加载失败等，
 * 统一记录到日志（WARN/ERROR 级别），便于生产环境排查前端问题。
 * <p>
 * 该端点已加入 JwtAuthInterceptor 白名单，前端 sendBeacon 在页面卸载时
 * 可能不携带 token，故不做鉴权要求。
 * <p>
 * 安全防护（匿名端点，2026-09-23 加固）：
 * <ul>
 *   <li>{@link RateLimit}：按客户端 IP 限流 60 次/分钟，防止匿名刷上报灌日志；
 *       阈值高于前端合法峰值（0.3 采样 + 指纹去重 + 批量单请求），并兼容企业 NAT 多用户共享出口</li>
 *   <li>批量条数上限 {@link #MAX_ERRORS_PER_BATCH}：超出截断，防止超大数组打满处理与日志</li>
 *   <li>字段截断 {@link #MAX_MESSAGE_LENGTH}/{@link #MAX_URL_LENGTH}：防止超长字符串灌爆日志行</li>
 *   <li>日志注入清洗：去除 CR/LF 等控制字符，防止伪造日志行（CRLF log injection）</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@RestController
@RequestMapping("/monitor")
public class MonitorController {

    /** 单批次最多接收的错误条数，超出部分截断丢弃 */
    static final int MAX_ERRORS_PER_BATCH = 50;

    /** message 字段最大记录长度（超出截断） */
    static final int MAX_MESSAGE_LENGTH = 1000;

    /** url 字段最大记录长度（超出截断） */
    static final int MAX_URL_LENGTH = 500;

    /**
     * 接收前端错误批量上报
     * <p>
     * 前端 errorReporter 将采集到的错误批量 POST 到此端点，
     * 每条错误包含 type、message、stack、url、userId 等字段。
     * 服务端仅记录日志，不做持久化存储（避免 DB 写入压力）。
     *
     * @param payload 包含 errors 数组的请求体
     * @return 空响应（200）
     */
    @RateLimit(key = "monitor_error", period = 60, count = 60)
    @PostMapping("/error")
    public Map<String, Object> reportError(@RequestBody(required = false) Map<String, Object> payload) {
        // 空体/非预期结构直接吞掉（监控端点不向客户端报错，避免触发上报方重试循环）
        if (payload == null) {
            return Map.of("success", true);
        }
        Object errorsObj = payload.get("errors");
        if (!(errorsObj instanceof List<?> errors)) {
            return Map.of("success", true);
        }

        // 批量条数上限：超出截断，防止超大数组刷日志
        int total = errors.size();
        List<?> bounded = total > MAX_ERRORS_PER_BATCH ? errors.subList(0, MAX_ERRORS_PER_BATCH) : errors;
        if (total > MAX_ERRORS_PER_BATCH) {
            log.warn("[前端监控] 单批次错误条数 {} 超过上限 {}，已截断多余部分", total, MAX_ERRORS_PER_BATCH);
        }

        for (Object err : bounded) {
            if (err instanceof Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> errMap = (Map<String, Object>) rawMap;
                String type = sanitizeLogField(String.valueOf(errMap.getOrDefault("type", "unknown")), 32);
                String message = sanitizeLogField(String.valueOf(errMap.getOrDefault("message", "")), MAX_MESSAGE_LENGTH);
                String url = sanitizeLogField(String.valueOf(errMap.getOrDefault("url", "")), MAX_URL_LENGTH);
                Object userId = errMap.get("userId");
                // JS 运行时错误和 Vue 组件错误用 ERROR 级别，其余用 WARN
                if ("js_error".equals(type) || "vue_error".equals(type)) {
                    log.error("[前端监控] type={}, userId={}, url={} | msg={}", type, userId, url, message);
                } else {
                    log.warn("[前端监控] type={}, userId={}, url={} | msg={}", type, userId, url, message);
                }
            }
        }
        return Map.of("success", true);
    }

    /**
     * 日志字段清洗：去除 CR/LF 等控制字符（防 CRLF 日志注入伪造日志行），并截断到指定长度。
     * <p>
     * 包内可见以便单元测试覆盖。null 入参返回空字符串。
     *
     * @param value     原始字段值
     * @param maxLength 允许的最大字符数（含截断标记）
     * @return 清洗并截断后的安全字符串
     */
    static String sanitizeLogField(String value, int maxLength) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return "";
        }
        // 所有 ASCII 控制字符（含 \r \n \t）替换为空格，杜绝跨行伪造日志
        String cleaned = value.replaceAll("\\p{Cntrl}", " ");
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength) + "...[truncated]";
    }
}
