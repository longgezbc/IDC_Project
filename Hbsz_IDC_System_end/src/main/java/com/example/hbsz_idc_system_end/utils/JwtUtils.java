package com.example.hbsz_idc_system_end.utils;

import com.example.hbsz_idc_system_end.common.constant.JwtConstant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 * <p>
 * 负责访问 Token / 刷新 Token 的生成、解析与校验，是系统无状态认证的核心工具。
 * 使用 HMAC-SHA256 对称签名，密钥与过期时间由配置项 {@code jwt.secret}、{@code jwt.expiration} 注入。
 *
 * @author hbsz
 */
@Slf4j
@Component
public class JwtUtils {

    /**
     * JWT 签名密钥，来源于配置 {@code jwt.secret}
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * 访问 Token 有效期（毫秒），默认 1800000（30 分钟）
     * <p>
     * 安全修复 #41：原默认 86400000（24 小时）过长，token 一旦泄露风险窗口大。
     * 缩短至 30 分钟，配合刷新 Token 机制在保证用户体验的同时降低泄露风险。
     */
    @Value("${jwt.expiration:1800000}")
    private long expiration;

    /**
     * 刷新 Token 有效期（毫秒），默认 604800000（7 天）
     * <p>
     * 安全修复 #41：原刷新 Token 有效期硬编码在代码中，现改为配置项化。
     * 用于在访问 Token 过期后换取新的访问 Token。
     */
    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    /**
     * 安全修复 #40：JWT 签发方（issuer），来源于配置 {@code hbsz.jwt.issuer}，默认 hbsz-idc。
     * 用于在 token 中声明签发者并在解析时校验，防止跨服务 token 误用。
     */
    @Value("${hbsz.jwt.issuer:hbsz-idc}")
    private String issuer;

    /**
     * 安全修复 #40：JWT 受众（audience），来源于配置 {@code hbsz.jwt.audience}，默认 hbsz-client。
     * 用于在 token 中声明接收方并在解析时校验，防止 token 被其他服务冒用。
     */
    @Value("${hbsz.jwt.audience:hbsz-client}")
    private String audience;

    /**
     * 根据配置密钥构造 HMAC-SHA 签名密钥。
     *
     * @return 签名用的 {@link SecretKey}
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成访问 Token。
     * <p>
     * 将 userId、username、name 写入自定义 claims，并以 username 作为 subject，
     * 使用配置的过期时间与 HS256 算法签名。
     *
     * @param userId   用户 ID
     * @param username 用户名（作为 subject）
     * @param name     用户姓名
     * @return 签名后的 JWT 字符串
     */
    public String generateToken(Long userId, String username, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtConstant.CLAIM_USER_ID, userId);
        claims.put(JwtConstant.CLAIM_USERNAME, username);
        claims.put(JwtConstant.CLAIM_NAME, name);
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        // 安全修复 #40：写入 issuer 与 audience，解析时校验，防止跨服务 token 误用
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuer(issuer)
                .setAudience(audience)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 生成刷新 Token（有效期固定 7 天）。
     * <p>
     * 额外写入 {@code type=refresh} 以区分访问 Token，用于在访问 Token 过期后换取新令牌。
     *
     * @param userId   用户 ID
     * @param username 用户名（作为 subject）
     * @return 签名后的刷新 Token 字符串
     */
    public String generateRefreshToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtConstant.CLAIM_USER_ID, userId);
        claims.put(JwtConstant.CLAIM_USERNAME, username);
        claims.put("type", "refresh");
        Date now = new Date();
        // 安全修复 #41：使用配置的刷新 Token 有效期，替代原硬编码的 7 天
        Date expiryDate = new Date(now.getTime() + refreshExpiration);
        // 安全修复 #40：刷新 Token 同样写入 issuer 与 audience
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuer(issuer)
                .setAudience(audience)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 Token 并返回其 Claims。
     * <p>
     * 内部会校验签名、格式、issuer 与 audience，解析失败（签名不符/格式错误/已过期/签发方或受众不匹配等）
     * 时记录 WARN 并返回 null，不抛异常。
     * <p>
     * 安全修复 #40：解析时要求 issuer 与 audience 与配置一致，防止跨服务 token 误用。
     *
     * @param token JWT 字符串
     * @return 解析出的 {@link Claims}；解析失败时返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.warn("Token解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 校验 Token 是否有效（可解析且未过期）。
     *
     * @param token JWT 字符串
     * @return 有效返回 true，否则返回 false
     */
    public boolean validateToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return false;
        }
        return !claims.getExpiration().before(new Date());
    }

    /**
     * 从 Token 中获取用户 ID。
     * <p>
     * 兼容 JWT 反序列化后 userId 为 Integer 或 Long 的两种情形。
     *
     * @param token JWT 字符串
     * @return 用户 ID；解析失败或不含该字段时返回 null
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        Object userId = claims.get(JwtConstant.CLAIM_USER_ID);
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }
        if (userId instanceof Long) {
            return (Long) userId;
        }
        return null;
    }

    /**
     * 从 Token 中获取用户名（subject）。
     *
     * @param token JWT 字符串
     * @return 用户名；解析失败时返回 null
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims == null ? null : claims.getSubject();
    }

    /**
     * 获取访问 Token 的有效期（秒）。
     *
     * @return 有效期秒数（由毫秒配置换算）
     */
    public long getExpirationSeconds() {
        return expiration / 1000;
    }

    /**
     * 获取刷新 Token 的有效期（秒）。
     * <p>
     * 用于登出时间戳的 Redis TTL 设置：登出标记只需存活到所有存量 refreshToken
     * 自然过期为止，无需永久保留。
     *
     * @return 刷新 Token 有效期秒数（由毫秒配置换算）
     */
    public long getRefreshExpirationSeconds() {
        return refreshExpiration / 1000;
    }

}
