package com.example.hbsz_idc_system_end.utils;

import com.github.promeg.pinyinhelper.Pinyin;
import org.springframework.util.StringUtils;

/**
 * 汉字转拼音工具（基于 TinyPinyin，无多音字词典负担，取常用音）。
 * <p>
 * 供全局搜索的拼音冗余列生成使用：
 * <ul>
 *   <li>{@link #toFullPinyin(String)}：全拼，如「腾讯-乌兰察布建安-GW-M5」→「tengxun wulanchabujianan gw m5」</li>
 *   <li>{@link #toInitials(String)}：首字母，如「乌兰察布建安-GW-M5」→「wlcbjagwm5」</li>
 * </ul>
 * 转换规则：
 * <ul>
 *   <li>中文字符逐个转拼音（小写）；</li>
 *   <li>全拼模式下，英文字母/数字原样小写保留，空白与标点替换为空格（保证 LIKE 分词边界）；</li>
 *   <li>首字母模式下，中文取拼音首字母，英文字母/数字原样小写保留，其余字符跳过。</li>
 * </ul>
 * 列存储建议 VARCHAR(255)，名称超长时截断（搜索为前缀/包含匹配，截断影响可忽略）。
 *
 * @author hbsz
 */
public final class PinyinUtils {

    /** 防止拼接出的拼音串超出列宽（utf8mb4 VARCHAR(255)） */
    private static final int MAX_LENGTH = 255;

    private PinyinUtils() {
    }

    /**
     * 转全拼：中文→全拼（小写），英文/数字→原样小写，空白与标点→空格。
     * 例：「腾讯-乌兰察布建安-GW-M5」→「tengxun wulanchabu jianan gw m5」
     *
     * @param text 原始文本，null 返回 null
     * @return 全拼串（小写），入参空白时返回 null
     */
    public static String toFullPinyin(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            appendChar(sb, c, /* initials */ false);
        }
        return normalize(sb);
    }

    /**
     * 转首字母：中文→拼音首字母，英文/数字→原样小写，其余字符跳过。
     * 例：「乌兰察布建安-GW-M5」→「wlcbjagwm5」
     *
     * @param text 原始文本，null 返回 null
     * @return 首字母串（小写），入参空白时返回 null
     */
    public static String toInitials(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            appendChar(sb, c, /* initials */ true);
        }
        return normalize(sb);
    }

    private static void appendChar(StringBuilder sb, char c, boolean initials) {
        if (Pinyin.isChinese(c)) {
            String pinyin = Pinyin.toPinyin(c).toLowerCase();
            if (initials) {
                sb.append(pinyin.charAt(0));
            } else {
                sb.append(pinyin);
            }
            return;
        }
        if (Character.isLetterOrDigit(c)) {
            sb.append(Character.toLowerCase(c));
            return;
        }
        // 全拼模式：标点/符号/空白统一为单空格，保留 LIKE 匹配的语义边界；
        // 首字母模式：全部跳过
        if (!initials) {
            sb.append(' ');
        }
    }

    /** 压缩连续空白为单空格并去首尾；空串返回 null；超长截断 */
    private static String normalize(StringBuilder sb) {
        String result = sb.toString().replaceAll("\\s+", " ").trim();
        if (result.isEmpty()) {
            return null;
        }
        return result.length() > MAX_LENGTH ? result.substring(0, MAX_LENGTH) : result;
    }
}
