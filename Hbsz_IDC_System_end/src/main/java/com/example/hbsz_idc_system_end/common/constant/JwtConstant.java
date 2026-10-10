package com.example.hbsz_idc_system_end.common.constant;

/**
 * JWT 相关常量
 * <p>
 * 定义 JWT Token 的 claim 键名与默认有效期，供 {@code JwtUtils} 等生成/解析 Token 时统一引用。
 * 该类为纯常量类，禁止实例化。
 *
 * @author hbsz
 */
public class JwtConstant {

    /**
     * 私有构造器，禁止实例化
     */
    private JwtConstant() {
    }

    /**
     * JWT Token 的 claim 中用户ID的key
     */
    public static final String CLAIM_USER_ID = "userId";

    /**
     * JWT Token 的 claim 中用户名的key
     */
    public static final String CLAIM_USERNAME = "username";

    /**
     * JWT Token 的 claim 中用户姓名的key
     */
    public static final String CLAIM_NAME = "name";

    /**
     * 默认 Token 有效期（毫秒）: 24小时
     */
    public static final long DEFAULT_TOKEN_EXPIRATION = 24 * 60 * 60 * 1000L;

}
