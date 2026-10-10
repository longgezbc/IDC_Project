package com.example.hbsz_idc_system_end.utils;

/**
 * 用户上下文工具类
 * <p>
 * 基于 {@link ThreadLocal} 在当前请求线程内保存已认证用户的 userId 与 username，
 * 供 Service、切面等在无需层层传参的情况下获取当前登录用户信息。
 * 通常由 {@code JwtAuthInterceptor} 在认证通过后写入，并在请求结束时调用 {@link #clear()} 清理。
 * <p>
 * 线程安全说明：每个线程持有独立副本，天然线程安全；但 Web 容器复用线程，
 * 请求结束必须清理，否则会造成用户信息串号（越权风险）及 ThreadLocal 内存泄漏。
 *
 * @author hbsz
 */
public class UserContext {

    /** 保存当前线程的用户 ID */
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    /** 保存当前线程的用户名 */
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    /**
     * 私有构造器，禁止实例化
     */
    private UserContext() {
    }

    /**
     * 设置当前线程的用户 ID
     *
     * @param userId 用户 ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取当前线程的用户 ID
     *
     * @return 用户 ID；未设置时返回 null
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 设置当前线程的用户名
     *
     * @param username 用户名
     */
    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    /**
     * 获取当前线程的用户名
     *
     * @return 用户名；未设置时返回 null
     */
    public static String getUsername() {
        return USERNAME.get();
    }

    /**
     * 清理当前线程保存的用户信息，防止线程复用导致的数据串号与内存泄漏。
     * 必须在请求处理结束时调用。
     */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
    }

}
