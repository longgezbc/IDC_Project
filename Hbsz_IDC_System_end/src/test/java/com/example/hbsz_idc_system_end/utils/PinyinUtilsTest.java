package com.example.hbsz_idc_system_end.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PinyinUtils 单元测试：全拼/首字母转换、混合文本、空白与空值处理。
 *
 * @author hbsz
 */
class PinyinUtilsTest {

    @Test
    void fullPinyinChineseOnly() {
        assertEquals("jianan", PinyinUtils.toFullPinyin("建安"));
    }

    @Test
    void fullPinyinMixedWithAscii() {
        // 中文全拼 + 英文数字原样小写，标点转空格（乌兰察布建安为连续中文，拼音连写）
        assertEquals("tengxun wulanchabujianan gw m5",
                PinyinUtils.toFullPinyin("腾讯-乌兰察布建安-GW-M5"));
    }

    @Test
    void initialsChineseOnly() {
        assertEquals("ja", PinyinUtils.toInitials("建安"));
    }

    @Test
    void initialsMixed() {
        // 中文取首字母，英文字母数字原样小写保留，标点跳过
        assertEquals("txwlcbjagwm5", PinyinUtils.toInitials("腾讯-乌兰察布建安-GW-M5"));
    }

    @Test
    void asciiOnlyFullKeepsSeparators() {
        assertEquals("gw m5", PinyinUtils.toFullPinyin("GW-M5"));
    }

    @Test
    void asciiOnlyInitialsStripsSeparators() {
        assertEquals("gwm5", PinyinUtils.toInitials("GW-M5"));
    }

    @Test
    void pureAsciiUnchanged() {
        assertEquals("hb tx gw 20261102", PinyinUtils.toFullPinyin("HB-TX-GW-20261102"));
    }

    @Test
    void blankAndNullReturnNull() {
        assertNull(PinyinUtils.toFullPinyin(null));
        assertNull(PinyinUtils.toFullPinyin(""));
        assertNull(PinyinUtils.toFullPinyin("   "));
        assertNull(PinyinUtils.toInitials(null));
        assertNull(PinyinUtils.toInitials(""));
        assertNull(PinyinUtils.toInitials("  -  "));
    }

    @Test
    void longInputTruncatedTo255() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("安");
        }
        assertEquals(255, PinyinUtils.toFullPinyin(sb.toString()).length());
    }
}
