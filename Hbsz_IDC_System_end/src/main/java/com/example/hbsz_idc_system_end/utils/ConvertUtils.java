package com.example.hbsz_idc_system_end.utils;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 类型转换工具类
 * <p>
 * 背景：ProjectService、StatisticsService、ArchiveService 中均有 toInt / toBigDecimal 的
 * 重复实现，违反 DRY 原则，且各处实现细节略有差异（如是否打印日志、是否抛异常）。
 * 统一抽取后消除约 60 行重复代码，便于后续维护。
 * <p>
 * 设计原则：
 * - 所有方法对 null 安全（返回 0 / BigDecimal.ZERO）
 * - 对异常输入返回默认值，不抛异常（适用于 Map 中取值的统计场景）
 * - 不打印日志，调用方按需打印
 *
 * @author hbsz
 */
public final class ConvertUtils {

    /**
     * 工具类不允许实例化
     */
    private ConvertUtils() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 安全地将对象转为 int
     * <p>
     * 适用场景：从 MyBatis 聚合查询返回的 Map 中取整数值，
     * Map 中的值可能是 Long / Integer / BigInteger / Number / String 等多种类型。
     *
     * @param value 原始值，可为 null
     * @return 转换后的 int 值；null 或格式异常返回 0
     */
    public static int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 安全地将对象转为 BigDecimal
     * <p>
     * 适用场景：从 MyBatis 聚合查询返回的 Map 中取金额、平均值等数值。
     * 优先使用 BigDecimal 构造器避免 Double 精度损失。
     *
     * @param value 原始值，可为 null
     * @return 转换后的 BigDecimal 值；null 或格式异常返回 BigDecimal.ZERO
     */
    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            // 使用字符串构造避免 Double/Float 精度损失
            return new BigDecimal(((Number) value).toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /** 文件大小字符串解析正则：匹配 "2.5MB"、"1024" 等，单位不区分大小写 */
    private static final Pattern SIZE_PATTERN = Pattern.compile(
            "^\\s*(\\d+(?:\\.\\d+)?)\\s*([KMGTP]?B?)\\s*$",
            Pattern.CASE_INSENSITIVE);

    /** 单位对应字节数（B=1, KB=1024, MB=1024^2, ...） */
    private static final long[] UNIT_BYTES = {1L, 1024L, 1024L * 1024L, 1024L * 1024L * 1024L,
            1024L * 1024L * 1024L * 1024L};

    /**
     * 将可读文件大小字符串（如 "2.5MB"）解析为字节数。
     * <p>
     * 适用场景：前端传入的文件大小为字符串形式，需在落库时同步写入字节字段
     * 以支持按大小排序和精确统计。
     * <p>
     * 支持的单位（不区分大小写）：
     * <ul>
     *   <li>B / 空单位 → 字节</li>
     *   <li>KB → 千字节（1024）</li>
     *   <li>MB → 兆字节（1024^2）</li>
     *   <li>GB → 吉字节（1024^3）</li>
     *   <li>TB → 太字节（1024^4）</li>
     * </ul>
     *
     * @param sizeText 文件大小字符串，如 "2.5MB"、"1024"、"1.2 GB"
     * @return 字节数；null 或无法解析时返回 0
     */
    public static long parseSizeToBytes(String sizeText) {
        if (sizeText == null || sizeText.trim().isEmpty()) {
            return 0L;
        }
        Matcher m = SIZE_PATTERN.matcher(sizeText);
        if (!m.matches()) {
            return 0L;
        }
        double value;
        try {
            value = Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return 0L;
        }
        String unit = m.group(2).toUpperCase();
        int idx;
        if (unit.isEmpty() || unit.equals("B")) {
            idx = 0;
        } else if (unit.equals("KB")) {
            idx = 1;
        } else if (unit.equals("MB")) {
            idx = 2;
        } else if (unit.equals("GB")) {
            idx = 3;
        } else if (unit.equals("TB")) {
            idx = 4;
        } else {
            idx = 0;
        }
        return (long) (value * UNIT_BYTES[idx]);
    }
}
