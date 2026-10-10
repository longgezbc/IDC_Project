package com.example.hbsz_idc_system_end.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link IpUtils} 受信代理与真实客户端 IP 解析逻辑测试。
 * <p>
 * 覆盖：默认不信任任何代理、精确 IP 白名单、CIDR 网段边界、
 * X-Forwarded-For 从右向左跳过受信代理（防伪造）、X-Real-IP 回退、非法配置容错。
 */
class IpUtilsTest {

    private IpUtils ipUtils;

    @BeforeEach
    void setUp() {
        ipUtils = new IpUtils();
    }

    private void configure(String trustedProxies) {
        ReflectionTestUtils.setField(ipUtils, "trustedProxiesConfig", trustedProxies);
        ipUtils.parseConfig();
    }

    private MockHttpServletRequest request(String remoteAddr, String xff, String xRealIp) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(remoteAddr);
        if (xff != null) {
            req.addHeader("X-Forwarded-For", xff);
        }
        if (xRealIp != null) {
            req.addHeader("X-Real-IP", xRealIp);
        }
        return req;
    }

    @Test
    @DisplayName("未配置受信代理：即使带 XFF 也使用 TCP 直连 IP（防伪造）")
    void noTrustedProxiesUsesRemoteAddr() {
        configure("");
        MockHttpServletRequest req = request("172.18.0.5", "1.2.3.4", "1.2.3.4");
        assertEquals("172.18.0.5", ipUtils.getClientIp(req));
    }

    @Test
    @DisplayName("精确 IP 白名单：直连受信时采信 XFF")
    void exactTrustedProxyUsesXff() {
        configure("172.18.0.5");
        MockHttpServletRequest req = request("172.18.0.5", "203.0.113.9", null);
        assertEquals("203.0.113.9", ipUtils.getClientIp(req));
    }

    @Test
    @DisplayName("精确 IP 白名单：不在名单内的代理头被忽略")
    void untrustedProxyHeaderIgnored() {
        configure("172.18.0.5");
        MockHttpServletRequest req = request("10.0.0.99", "203.0.113.9", "203.0.113.9");
        assertEquals("10.0.0.99", ipUtils.getClientIp(req));
    }

    @Test
    @DisplayName("CIDR /16：网段内的 Nginx 容器 IP 受信，网段外不受信")
    void cidrSixteenMatching() {
        configure("172.18.0.0/16");
        assertEquals("203.0.113.9",
                ipUtils.getClientIp(request("172.18.0.5", "203.0.113.9", null)));
        assertEquals("172.19.0.5",
                ipUtils.getClientIp(request("172.19.0.5", "203.0.113.9", null)));
    }

    @Test
    @DisplayName("CIDR /24 边界：网段内视为受信跳转，跨网段地址不受信")
    void cidrTwentyFourBoundary() {
        configure("10.0.0.0/24");
        // 直连对端 10.0.0.1 命中 /24；XFF 链中 10.0.0.254（网段广播地址）亦命中被跳过，
        // 最终取首个非受信地址 10.0.1.1（跨网段，不命中）
        assertEquals("10.0.1.1",
                ipUtils.getClientIp(request("10.0.0.1", "10.0.1.1, 10.0.0.254", null)));
        // 直连对端跨网段不受信，代理头直接忽略
        assertEquals("10.0.1.1",
                ipUtils.getClientIp(request("10.0.1.1", "10.0.0.254", null)));
    }

    @Test
    @DisplayName("XFF 链路取最右非受信：客户端伪造的最左值被忽略（核心防绕过）")
    void xffChainTakesRightmostNonTrusted() {
        configure("172.18.0.0/16");
        // 客户端自填 1.2.3.4，Nginx 追加真实对端 203.0.113.9
        MockHttpServletRequest req = request("172.18.0.5", "1.2.3.4, 203.0.113.9", null);
        assertEquals("203.0.113.9", ipUtils.getClientIp(req));
    }

    @Test
    @DisplayName("多级代理：从右向左跳过受信代理跳转后取真实客户端")
    void xffChainSkipsTrustedHops() {
        configure("172.18.0.0/16");
        // 链路：客户端伪造 1.2.3.4 → 真实客户端 203.0.113.9 → 受信中间代理 172.18.0.7
        MockHttpServletRequest req = request("172.18.0.5", "1.2.3.4, 203.0.113.9, 172.18.0.7", null);
        assertEquals("203.0.113.9", ipUtils.getClientIp(req));
    }

    @Test
    @DisplayName("XFF 全为受信代理：无法定位真实客户端，回退直连 IP")
    void xffChainAllTrustedFallsBackToRemoteAddr() {
        configure("172.18.0.0/16");
        MockHttpServletRequest req = request("172.18.0.5", "172.18.0.7, 172.18.0.8", null);
        assertEquals("172.18.0.5", ipUtils.getClientIp(req));
    }

    @Test
    @DisplayName("XFF 含 unknown 占位时跳过，继续向左找有效值")
    void xffChainSkipsUnknown() {
        configure("172.18.0.0/16");
        MockHttpServletRequest req = request("172.18.0.5", "1.2.3.4, unknown", null);
        assertEquals("1.2.3.4", ipUtils.getClientIp(req));
    }

    @Test
    @DisplayName("XFF 缺失时回退 X-Real-IP（Nginx 以 $remote_addr 覆写，不可伪造）")
    void fallsBackToXRealIp() {
        configure("172.18.0.0/16");
        MockHttpServletRequest req = request("172.18.0.5", null, "203.0.113.20");
        assertEquals("203.0.113.20", ipUtils.getClientIp(req));
    }

    @Test
    @DisplayName("混合配置：精确 IP 与 CIDR 同时生效，含空格容错")
    void mixedExactAndCidrConfig() {
        configure(" 127.0.0.1 , 172.18.0.0/16 ");
        assertEquals("203.0.113.1",
                ipUtils.getClientIp(request("127.0.0.1", "203.0.113.1", null)));
        assertEquals("203.0.113.2",
                ipUtils.getClientIp(request("172.18.0.99", "203.0.113.2", null)));
    }

    @Test
    @DisplayName("非法配置项被忽略，合法项继续生效")
    void invalidConfigEntryIgnored() {
        configure("not-an-ip,172.18.0.0/16,bad-cidr/99");
        assertEquals("203.0.113.7",
                ipUtils.getClientIp(request("172.18.0.5", "203.0.113.7", null)));
    }

    @Test
    @DisplayName("null 请求返回 unknown")
    void nullRequestReturnsUnknown() {
        configure("172.18.0.0/16");
        assertEquals("unknown", ipUtils.getClientIp(null));
    }
}
