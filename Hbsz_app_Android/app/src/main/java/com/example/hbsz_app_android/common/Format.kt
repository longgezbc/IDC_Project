package com.example.hbsz_app_android.common

import java.util.Locale

/**
 * 通用展示格式化工具：日期 / 金额。
 * 统一收敛原先散落在各 Screen 里的 take()/replace("T"," ") 黑科技与重复的金额格式化。
 */

/**
 * 后端日期字符串 → "yyyy-MM-dd"。
 * 兼容 "2026-08-16"、"2026-08-16T10:30:00"、"2026-08-16 10:30:00" 等格式，
 * 解析失败返回 "-"。
 */
fun formatDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "-"
    val datePart = raw.trim().takeWhile { it != 'T' && it != ' ' }
    // 简单合法性校验：yyyy-M-d 或 yyyy-MM-dd
    val m = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""").find(datePart) ?: return "-"
    val (y, mo, d) = m.destructured
    return "%04d-%02d-%02d".format(Locale.CHINA, y.toInt(), mo.toInt(), d.toInt())
}

/**
 * 后端日期时间字符串 → "yyyy-MM-dd HH:mm"。
 * ISO 的 "T" 分隔符自动替换为空格；解析失败回退为纯日期；再失败返回 "-"。
 */
fun formatDateTime(raw: String?): String {
    if (raw.isNullOrBlank()) return "-"
    val normalized = raw.trim().replace('T', ' ')
    val m = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})[ ](\d{1,2}):(\d{2})""").find(normalized)
        ?: return formatDate(raw)
    val (y, mo, d, h, mi) = m.destructured
    return "%04d-%02d-%02d %02d:%02d".format(Locale.CHINA, y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt())
}

/** 金额展示：超大数值缩写为万元/亿元 */
fun formatAmount(yuan: Double): String = when {
    yuan >= 1e8 -> String.format(Locale.CHINA, "%.2f亿", yuan / 1e8)
    yuan >= 1e4 -> String.format(Locale.CHINA, "%.2f万", yuan / 1e4)
    else -> String.format(Locale.CHINA, "%.2f", yuan)
}

/**
 * 数量展示：去掉多余的尾部小数（12.0 → "12"，12.50 → "12.5"，null → "-"）。
 * 后端 BigDecimal 反序列化为 Double 后用于工作量数量等字段的展示。
 */
fun formatQuantity(value: Double?): String {
    if (value == null) return "-"
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}
