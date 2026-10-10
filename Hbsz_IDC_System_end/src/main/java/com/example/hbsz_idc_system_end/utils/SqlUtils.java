package com.example.hbsz_idc_system_end.utils;

/**
 * SQL 工具类
 * <p>
 * 提供 LIKE 查询通配符转义等安全工具方法，防止用户输入干扰 SQL 语义或引发安全问题。
 * 该类为纯静态工具类，禁止实例化。
 *
 * @author hbsz
 */
public final class SqlUtils {

    /**
     * 私有构造器，禁止实例化
     */
    private SqlUtils() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 转义 LIKE 查询中的特殊字符（%、_、\）
     * 防止用户输入的通配符被当作SQL通配符执行，导致全表扫描或注入
     *
     * @param keyword 原始关键字
     * @return 转义后的关键字（配合 MyBatis-Plus like() 使用，默认转义符为 \）
     */
    public static String escapeLike(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return keyword;
        }
        // 依次转义 \、%、_ 三个特殊字符
        StringBuilder sb = new StringBuilder(keyword.length() * 2);
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
