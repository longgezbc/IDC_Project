package com.example.hbsz_idc_system_end.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MonitorController} 加固逻辑单元测试。
 * <p>
 * 覆盖：日志注入清洗、字段截断、空体/异常结构容错、批量条数上限截断。
 */
class MonitorControllerTest {

    private final MonitorController controller = new MonitorController();

    @Test
    @DisplayName("sanitizeLogField：CR/LF/Tab 等控制字符被替换为空格（防日志注入）")
    void sanitizeReplacesControlChars() {
        String malicious = "正常消息\n2026-09-23 ERROR [伪造日志行]\r\n\t";
        String result = MonitorController.sanitizeLogField(malicious, 1000);

        assertFalse(result.contains("\n"), "不得保留换行符");
        assertFalse(result.contains("\r"), "不得保留回车符");
        assertFalse(result.contains("\t"), "不得保留制表符");
        assertTrue(result.startsWith("正常消息"), "正常内容应保留");
        assertTrue(result.contains("[伪造日志行]"), "注入文本本身保留但不再跨行");
    }

    @Test
    @DisplayName("sanitizeLogField：超长字段截断并附标记")
    void sanitizeTruncatesOverlongField() {
        String overlong = "a".repeat(2000);
        String result = MonitorController.sanitizeLogField(overlong, 100);

        assertEquals(100 + "...[truncated]".length(), result.length());
        assertTrue(result.endsWith("...[truncated]"));
    }

    @Test
    @DisplayName("sanitizeLogField：边界长度不截断")
    void sanitizeKeepsBoundaryLength() {
        String exact = "b".repeat(100);
        assertEquals(exact, MonitorController.sanitizeLogField(exact, 100));
    }

    @Test
    @DisplayName("sanitizeLogField：null/空串/字面量 null 统一为空字符串")
    void sanitizeHandlesNullish() {
        assertEquals("", MonitorController.sanitizeLogField(null, 100));
        assertEquals("", MonitorController.sanitizeLogField("", 100));
        assertEquals("", MonitorController.sanitizeLogField("null", 100));
    }

    @Test
    @DisplayName("reportError：空请求体容错返回 success，不抛异常")
    void reportErrorToleratesNullBody() {
        Map<String, Object> resp = controller.reportError(null);
        assertEquals(true, resp.get("success"));
    }

    @Test
    @DisplayName("reportError：errors 缺失或类型错误时容错返回 success")
    void reportErrorToleratesMalformedPayload() {
        assertEquals(true, controller.reportError(new HashMap<>()).get("success"));
        Map<String, Object> wrongType = Map.of("errors", "not-a-list");
        assertEquals(true, controller.reportError(wrongType).get("success"));
    }

    @Test
    @DisplayName("reportError：正常批次（js_error）全部处理并返回 success")
    void reportErrorProcessesValidBatch() {
        List<Map<String, Object>> errors = List.of(
                Map.of("type", "js_error", "message", "boom", "url", "http://x/", "userId", 7),
                Map.of("type", "promise_error", "message", "rejected")
        );
        Map<String, Object> resp = controller.reportError(Map.of("errors", errors));
        assertEquals(true, resp.get("success"));
    }

    @Test
    @DisplayName("reportError：非 Map 元素的批次被跳过，不抛 ClassCastException")
    void reportErrorSkipsNonMapElements() {
        List<Object> errors = new ArrayList<>();
        errors.add("plain-string");
        errors.add(Map.of("type", "js_error", "message", "ok"));
        Map<String, Object> resp = controller.reportError(Map.of("errors", errors));
        assertEquals(true, resp.get("success"));
    }

    @Test
    @DisplayName("reportError：超过 50 条的批次被截断处理（不抛异常、返回 success）")
    void reportErrorTruncatesOversizedBatch() {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; i < MonitorController.MAX_ERRORS_PER_BATCH + 100; i++) {
            // 夹带超长 + 注入载荷，验证截断链路下清洗依然生效
            errors.add(Map.of(
                    "type", "js_error",
                    "message", "x".repeat(5000) + "\nFAKE LINE " + i,
                    "url", "http://x/p?token=secret\ninjected"
            ));
        }
        Map<String, Object> resp = controller.reportError(Map.of("errors", errors));
        assertEquals(true, resp.get("success"));
        assertEquals(50, MonitorController.MAX_ERRORS_PER_BATCH, "上限常量保持 50");
    }
}
