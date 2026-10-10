package com.example.hbsz_idc_system_end.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SqlUtils 单元测试
 *
 * @author hbsz
 */
@DisplayName("SQL工具类测试")
class SqlUtilsTest {

    @Test
    @DisplayName("转义百分号")
    void escapeLike_percent() {
        assertEquals("100\\%", SqlUtils.escapeLike("100%"));
    }

    @Test
    @DisplayName("转义下划线")
    void escapeLike_underscore() {
        assertEquals("user\\_name", SqlUtils.escapeLike("user_name"));
    }

    @Test
    @DisplayName("转义反斜杠")
    void escapeLike_backslash() {
        assertEquals("path\\\\to", SqlUtils.escapeLike("path\\to"));
    }

    @Test
    @DisplayName("混合特殊字符转义")
    void escapeLike_mixed() {
        // 期望值修正：_ 与 % 同为 SQL LIKE 通配符，必须一并转义（与 escapeLike_underscore 用例一致）；
        // 原期望值 100\%_test\\ 漏写了 \_，与 _ 用例自相矛盾
        assertEquals("100\\%\\_test\\\\", SqlUtils.escapeLike("100%_test\\"));
    }

    @Test
    @DisplayName("空值返回原值")
    void escapeLike_null() {
        assertNull(SqlUtils.escapeLike(null));
        assertEquals("", SqlUtils.escapeLike(""));
    }

    @Test
    @DisplayName("无特殊字符不转义")
    void escapeLike_normal() {
        assertEquals("张三", SqlUtils.escapeLike("张三"));
        assertEquals("hello world", SqlUtils.escapeLike("hello world"));
    }
}
