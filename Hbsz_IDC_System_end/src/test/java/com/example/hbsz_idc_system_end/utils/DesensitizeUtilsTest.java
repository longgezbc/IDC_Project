package com.example.hbsz_idc_system_end.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DesensitizeUtils 单元测试
 *
 * @author hbsz
 */
@DisplayName("数据脱敏工具类测试")
class DesensitizeUtilsTest {

    @Test
    @DisplayName("手机号脱敏")
    void maskPhone_normal() {
        assertEquals("138****5678", DesensitizeUtils.maskPhone("13812345678"));
    }

    @Test
    @DisplayName("手机号过短不脱敏")
    void maskPhone_short() {
        assertEquals("12345", DesensitizeUtils.maskPhone("12345"));
    }

    @Test
    @DisplayName("手机号为null")
    void maskPhone_null() {
        assertNull(DesensitizeUtils.maskPhone(null));
    }

    @Test
    @DisplayName("邮箱脱敏")
    void maskEmail_normal() {
        assertEquals("z***@ggkj.com", DesensitizeUtils.maskEmail("zhangming@ggkj.com"));
    }

    @Test
    @DisplayName("邮箱单字符用户名")
    void maskEmail_singleChar() {
        assertEquals("a@test.com", DesensitizeUtils.maskEmail("a@test.com"));
    }

    @Test
    @DisplayName("无@符号不脱敏")
    void maskEmail_noAt() {
        assertEquals("notanemail", DesensitizeUtils.maskEmail("notanemail"));
    }

    @Test
    @DisplayName("姓名脱敏-两字")
    void maskName_twoChar() {
        assertEquals("张*", DesensitizeUtils.maskName("张三"));
    }

    @Test
    @DisplayName("姓名脱敏-三字")
    void maskName_threeChar() {
        assertEquals("李**", DesensitizeUtils.maskName("李建国"));
    }

    @Test
    @DisplayName("姓名脱敏-单字不脱敏")
    void maskName_singleChar() {
        assertEquals("张", DesensitizeUtils.maskName("张"));
    }

    @Test
    @DisplayName("身份证脱敏")
    void maskIdCard_normal() {
        assertEquals("420101********1234", DesensitizeUtils.maskIdCard("420101199001011234"));
    }

    @Test
    @DisplayName("银行卡脱敏")
    void maskBankCard_normal() {
        assertEquals("6222****7890", DesensitizeUtils.maskBankCard("6222021234567890"));
    }
}
