package com.example.hbsz_idc_system_end.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OnlyOffice 在线编辑配置。
 * <p>
 * 对应 application.yml 中 {@code hbsz.onlyoffice.*} 配置项。
 * <ul>
 *   <li>serverUrl：前端浏览器访问 OnlyOffice Document Server 的地址（如 https://your-domain.com/onlyoffice 或 http://localhost:8082）</li>
 *   <li>jwtSecret：与 OnlyOffice DS 的 JWT_SECRET 一致，用于对编辑配置签名，DS 校验通过后才加载文档</li>
 *   <li>backendUrl：OnlyOffice DS 容器访问后端 API 的内部地址（docker 网络内，如 http://backend:8080）</li>
 *   <li>enabled：是否启用在线编辑（false 时前端不显示编辑按钮）</li>
 * </ul>
 *
 * @author hbsz
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "hbsz.onlyoffice")
public class OnlyOfficeConfig {

    /** 是否启用 OnlyOffice 在线编辑 */
    private boolean enabled = false;

    /** 前端浏览器访问 OnlyOffice DS 的地址（不带末尾斜杠） */
    private String serverUrl = "http://localhost:8082";

    /** JWT 签名密钥，须与 OnlyOffice DS 的 JWT_SECRET 一致 */
    private String jwtSecret = "";

    /** OnlyOffice DS 容器访问后端 API 的内部地址（docker 网络内，不带末尾斜杠） */
    private String backendUrl = "http://backend:8080";
}
