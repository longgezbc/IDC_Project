package com.example.hbsz_idc_system_end.utils;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户端 IP 解析工具
 * <p>
 * 集中实现"可信代理校验 + 真实客户端 IP 提取"逻辑，供认证服务（登录 IP 记录）、
 * 操作日志切面（审计 IP）与限流切面（IP 维度限流）共用，保证三处行为一致。
 * <p>
 * 安全背景：X-Forwarded-For / X-Real-IP 均为客户端可任意伪造的请求头。
 * 若无条件信任，攻击者可以：
 * <ul>
 *   <li>伪造 X-Forwarded-For 绕过基于 IP 的限流（每次请求换一个假 IP）；</li>
 *   <li>向操作日志注入虚假 IP，污染审计追踪；</li>
 *   <li>伪装登录来源 IP，误导安全排查。</li>
 * </ul>
 * <p>
 * 解析策略：
 * <ol>
 *   <li>默认使用 Servlet 容器感知的 {@code remoteAddr}（TCP 直连对端 IP，无法伪造）；</li>
 *   <li>仅当 remoteAddr 命中配置 {@code hbsz.security.trusted-proxies}（逗号分隔的白名单，
 *       支持精确 IP 与 CIDR 网段，如 {@code 172.18.0.0/16}）时，才回退读取代理头；</li>
 *   <li>X-Forwarded-For 为逗号分隔列表时，<strong>从右向左</strong>跳过命中白名单的受信
 *       代理跳转，取第一个非受信条目——它是最靠近本系统的真实 TCP 对端（Nginx 的
 *       {@code $proxy_add_x_forwarded_for} 在最右追加该地址）；取最左会采信客户端自填的伪造 IP；</li>
 *   <li>X-Forwarded-For 缺失时回退 X-Real-IP（Nginx 以 {@code $remote_addr} 覆写该头，不可伪造）。</li>
 * </ol>
 *
 * @author hbsz
 */
@Slf4j
@Component
public class IpUtils {

    /**
     * 受信代理配置原文（逗号分隔），来源于配置 {@code hbsz.security.trusted-proxies}。
     * 默认为空：不信任任何代理头，一律使用 remoteAddr。
     */
    @Value("${hbsz.security.trusted-proxies:}")
    private String trustedProxiesConfig;

    /** 精确 IP 白名单 */
    private Set<String> trustedExact = Collections.emptySet();

    /** CIDR 网段白名单（如 172.18.0.0/16） */
    private List<CidrBlock> trustedCidrs = Collections.emptyList();

    /**
     * 启动时解析受信代理配置：精确 IP 与 CIDR 分开存放，非法条目告警并忽略。
     */
    @PostConstruct
    void parseConfig() {
        if (!StringUtils.hasText(trustedProxiesConfig)) {
            return;
        }
        Set<String> exact = new HashSet<>();
        List<CidrBlock> cidrs = new ArrayList<>();
        for (String raw : trustedProxiesConfig.split("\\s*,\\s*")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            try {
                if (entry.contains("/")) {
                    cidrs.add(new CidrBlock(entry));
                } else {
                    // 归一化为标准数值形式（兼容 127.0.0.1 等写法差异）
                    exact.add(InetAddress.getByName(entry).getHostAddress());
                }
            } catch (Exception e) {
                log.warn("[IpUtils] 忽略非法受信代理配置项: {} ({})", entry, e.getMessage());
            }
        }
        this.trustedExact = Set.copyOf(exact);
        this.trustedCidrs = List.copyOf(cidrs);
        log.info("[IpUtils] 受信代理白名单已加载: 精确IP {} 个, CIDR 网段 {} 个", exact.size(), cidrs.size());
    }

    /**
     * 判断直连对端 IP 是否为受信代理（精确匹配或落入受信 CIDR）。
     *
     * @param remoteAddr TCP 直连对端 IP
     * @return true 表示可采信其代理头
     */
    private boolean isTrustedProxy(String remoteAddr) {
        if (!StringUtils.hasText(remoteAddr)) {
            return false;
        }
        if (trustedExact.contains(remoteAddr)) {
            return true;
        }
        for (CidrBlock cidr : trustedCidrs) {
            if (cidr.contains(remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取客户端真实 IP（带可信代理校验）。
     * <p>
     * 仅当 TCP 直连对端命中受信代理白名单时才采信 X-Forwarded-For / X-Real-IP，
     * 否则一律使用 remoteAddr，防止请求头伪造。
     *
     * @param request HTTP 请求
     * @return 客户端 IP；无法获取时返回 "unknown"
     */
    public String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        // 默认使用 TCP 直连对端 IP（不可伪造）
        String remoteAddr = request.getRemoteAddr();
        // 仅当直连对端为受信代理时，才允许使用代理头转发的客户端 IP
        if (StringUtils.hasText(remoteAddr) && isTrustedProxy(remoteAddr)) {
            String ip = extractClientIpFromXff(request.getHeader("X-Forwarded-For"));
            if (!StringUtils.hasText(ip)) {
                // X-Real-IP 由 Nginx 以 $remote_addr 覆写，客户端无法伪造
                ip = request.getHeader("X-Real-IP");
            }
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip.trim();
            }
        }
        return StringUtils.hasText(remoteAddr) ? remoteAddr : "unknown";
    }

    /**
     * 从 X-Forwarded-For 链中提取真实客户端 IP。
     * <p>
     * 链路形如 {@code 客户端自填值(可能伪造), 真实客户端, 代理1, ..., 直连受信代理}，
     * 由右向左遍历：右侧条目依次由各级受信基础设施追加，命中受信白名单则跳过继续左移，
     * 遇到的第一个非受信地址即为真实客户端（它是最靠近本系统的、非基础设施的 TCP 对端）。
     * <p>
     * 典型单跳 Nginx 场景：客户端伪造 {@code 1.2.3.4}，Nginx 追加真实对端后链路为
     * {@code 1.2.3.4, 58.47.x.x}，本方法返回 {@code 58.47.x.x}，伪造值被忽略。
     *
     * @param xff X-Forwarded-For 原始头值
     * @return 第一个非受信的有效 IP；链为空或全部为受信代理时返回 null
     */
    private String extractClientIpFromXff(String xff) {
        if (!StringUtils.hasText(xff)) {
            return null;
        }
        List<String> parts = Arrays.asList(xff.split(","));
        for (int i = parts.size() - 1; i >= 0; i--) {
            String candidate = parts.get(i).trim();
            if (candidate.isEmpty() || "unknown".equalsIgnoreCase(candidate)) {
                continue;
            }
            // 受信基础设施跳转（代理/网关）跳过；首个非受信地址即真实客户端
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * CIDR 网段匹配器（IPv4/IPv6 通用，基于 BigInteger 位掩码）。
     */
    private static final class CidrBlock {
        private final BigInteger network;
        private final BigInteger mask;
        /** 地址字节数：4=IPv4，16=IPv6，用于地址族不一致时快速排除 */
        private final int addrLen;

        CidrBlock(String cidr) {
            String[] pieces = cidr.split("/");
            if (pieces.length != 2) {
                throw new IllegalArgumentException("CIDR 格式非法: " + cidr);
            }
            try {
                byte[] addrBytes = InetAddress.getByName(pieces[0]).getAddress();
                int prefixLength = Integer.parseInt(pieces[1]);
                int totalBits = addrBytes.length * 8;
                if (prefixLength < 0 || prefixLength > totalBits) {
                    throw new IllegalArgumentException("前缀长度越界: " + prefixLength);
                }
                this.addrLen = addrBytes.length;
                // 显式构造高 prefixLength 位为 1 的掩码，避免取反产生符号位
                BigInteger builtMask = BigInteger.ZERO;
                for (int b = totalBits - 1; b >= totalBits - prefixLength; b--) {
                    builtMask = builtMask.setBit(b);
                }
                this.mask = builtMask;
                this.network = new BigInteger(1, addrBytes).and(builtMask);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("CIDR 地址无法解析: " + cidr, e);
            }
        }

        boolean contains(String ip) {
            try {
                byte[] candidateBytes = InetAddress.getByName(ip).getAddress();
                // IPv4/IPv6 地址族不一致直接不匹配
                if (candidateBytes.length != addrLen) {
                    return false;
                }
                BigInteger candidate = new BigInteger(1, candidateBytes);
                return candidate.and(mask).equals(network);
            } catch (UnknownHostException e) {
                return false;
            }
        }
    }
}
