package com.example.hbsz_idc_system_end.service.impl;

import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.config.OnlyOfficeConfig;
import com.example.hbsz_idc_system_end.entity.BizDocument;
import com.example.hbsz_idc_system_end.mapper.BizDocumentMapper;
import com.example.hbsz_idc_system_end.service.IFileStorageService;
import com.example.hbsz_idc_system_end.service.IOnlyOfficeService;
import com.example.hbsz_idc_system_end.utils.UserContext;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletResponse;
import javax.crypto.SecretKey;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * OnlyOffice 在线编辑服务实现。
 * <p>
 * 核心能力：
 * <ul>
 *   <li>{@link #buildEditConfig}：生成前端编辑器初始化配置（document/documentType/editorConfig + JWT 签名）</li>
 *   <li>{@link #handleCallback}：处理 OnlyOffice DS 的保存回调，下载新版本文件并落盘</li>
 *   <li>{@link #downloadFile}：供 OnlyOffice DS 拉取文档原文件（免用户认证，走 JWT 白名单）</li>
 * </ul>
 * <p>
 * 支持编辑的格式：docx/xlsx/pptx（OnlyOffice 原生编辑）；doc/xls/ppt 等旧格式仅预览。
 *
 * @author hbsz
 */
@Slf4j
@Service
public class OnlyOfficeServiceImpl implements IOnlyOfficeService {

    private final OnlyOfficeConfig config;
    private final BizDocumentMapper documentMapper;
    private final IFileStorageService fileStorageService;

    public OnlyOfficeServiceImpl(OnlyOfficeConfig config, BizDocumentMapper documentMapper,
                                  IFileStorageService fileStorageService) {
        this.config = config;
        this.documentMapper = documentMapper;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 生成编辑器初始化配置。
     *
     * @param docId 文档ID
     * @param mode  edit 或 view
     * @return 配置 Map（前端直接传给 DocsAPI.DocEditor）
     */
    @Override
    public Map<String, Object> buildEditConfig(Long docId, String mode) {
        if (!config.isEnabled()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "OnlyOffice 在线编辑未启用");
        }
        BizDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        String ext = extractExt(doc.getFileFormat());
        String documentType = getDocumentType(ext);
        if (documentType == null) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED,
                    "该文档类型不支持在线编辑：" + ext + "（支持 docx/xlsx/pptx）");
        }

        // 文档唯一 key：用 id + 版本号 + 更新时间，确保 OnlyOffice 识别为同一文档版本
        String docKey = "hbsz_" + doc.getId() + "_" + doc.getCurrentVersion() + "_"
                + (doc.getUploadDate() != null ? doc.getUploadDate().toEpochDay() : 0);

        Map<String, Object> document = new HashMap<>();
        document.put("fileType", ext);
        document.put("key", docKey);
        document.put("title", doc.getName() + "." + ext);
        // OnlyOffice DS 从此后端 URL 拉取文档原文件（docker 网络内部地址）
        document.put("url", config.getBackendUrl() + "/api/documents/onlyoffice/" + doc.getId() + "/file");

        Map<String, Object> editorConfig = new HashMap<>();
        editorConfig.put("mode", StringUtils.hasText(mode) ? mode : "edit");
        editorConfig.put("lang", "zh-CN");
        editorConfig.put("callbackUrl", config.getBackendUrl() + "/api/documents/onlyoffice/callback");
        Map<String, Object> user = new HashMap<>();
        user.put("id", String.valueOf(UserContext.getUserId()));
        user.put("name", UserContext.getUsername());
        editorConfig.put("user", user);

        Map<String, Object> result = new HashMap<>();
        result.put("document", document);
        result.put("documentType", documentType);
        result.put("editorConfig", editorConfig);
        result.put("token", config.getServerUrl() != null ? config.getServerUrl() : "");

        // JWT 签名（OnlyOffice DS 校验配置完整性，防篡改）
        if (StringUtils.hasText(config.getJwtSecret())) {
            SecretKey key = Keys.hmacShaKeyFor(config.getJwtSecret().getBytes(StandardCharsets.UTF_8));
            String token = Jwts.builder()
                    .setClaims(result)
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();
            result.put("token", token);
        }
        log.info("[OnlyOffice] 生成编辑配置: docId={}, mode={}, type={}", docId, mode, documentType);
        return result;
    }

    /**
     * 处理 OnlyOffice DS 保存回调。
     * <p>
     * status：1=editing 2=ready to save 3=corrupted 4=force saved 6=force saved with error
     * status=2/4/6 时从 body.url 下载新版本文件并落盘，更新文档 fileUrl。
     *
     * @param body 回调请求体
     * @return { error: 0 } 表示成功
     */
    @Override
    public Map<String, Object> handleCallback(Map<String, Object> body) {
        if (body == null) {
            return Collections.singletonMap("error", 1);
        }
        Object statusObj = body.get("status");
        int status = statusObj instanceof Integer ? (Integer) statusObj : 0;
        log.info("[OnlyOffice] 回调: status={}, key={}, url={}", status, body.get("key"), body.get("url"));

        // status=2（保存）或 4/6（强制保存）时下载新版本
        if (status == 2 || status == 4 || status == 6) {
            String downloadUrl = (String) body.get("url");
            String key = (String) body.get("key");
            if (StringUtils.hasText(downloadUrl) && StringUtils.hasText(key)) {
                Long docId = parseDocIdFromKey(key);
                if (docId != null) {
                    try {
                        BizDocument doc = documentMapper.selectById(docId);
                        if (doc != null) {
                            String ext = extractExt(doc.getFileFormat());
                            String newFileUrl = saveCallbackFile(downloadUrl, ext);
                            doc.setFileUrl(newFileUrl);
                            doc.setUploadDate(LocalDate.now());
                            documentMapper.updateById(doc);
                            log.info("[OnlyOffice] 文档新版本已保存: docId={}, fileUrl={}", docId, newFileUrl);
                        }
                    } catch (Exception e) {
                        log.error("[OnlyOffice] 回调保存失败: docId={}", docId, e);
                        return Collections.singletonMap("error", 1);
                    }
                }
            }
        }
        return Collections.singletonMap("error", 0);
    }

    /**
     * 供 OnlyOffice DS 拉取文档原文件（免用户认证，走 JWT 白名单）。
     *
     * @param docId   文档ID
     * @param response HTTP 响应
     */
    @Override
    public void downloadFile(Long docId, HttpServletResponse response) {
        BizDocument doc = documentMapper.selectById(docId);
        if (doc == null || !StringUtils.hasText(doc.getFileUrl())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        File file = fileStorageService.loadFile(doc.getFileUrl());
        String ext = extractExt(doc.getFileFormat());
        response.setContentType(getContentType(ext));
        response.setHeader("Content-Disposition", "attachment; filename=\""
                + doc.getName() + "." + ext + "\"");
        response.setContentLengthLong(file.length());
        try (InputStream in = Files.newInputStream(file.toPath());
             var out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        } catch (IOException e) {
            log.error("[OnlyOffice] 文件下载失败: docId={}", docId, e);
        }
    }

    /** 从 OnlyOffice 回调的 url 下载文件并保存到磁盘，返回相对路径 */
    private String saveCallbackFile(String downloadUrl, String ext) throws IOException {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String storedFilename = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase();
        Path targetDir = Paths.get(fileStorageService.getUploadDir(), datePath).toAbsolutePath().normalize();
        Files.createDirectories(targetDir);
        Path targetPath = targetDir.resolve(storedFilename);
        try (InputStream in = new URL(downloadUrl).openStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return datePath + "/" + storedFilename;
    }

    /** 从 docKey 解析文档ID（格式：hbsz_{id}_{version}_{date}） */
    private Long parseDocIdFromKey(String key) {
        if (key == null || !key.startsWith("hbsz_")) return null;
        String[] parts = key.split("_");
        if (parts.length < 2) return null;
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 提取扩展名（小写） */
    private String extractExt(String fileFormat) {
        if (fileFormat == null || fileFormat.isEmpty()) return "";
        return fileFormat.toLowerCase().trim();
    }

    /** 扩展名 → OnlyOffice documentType 映射，不支持返回 null */
    private String getDocumentType(String ext) {
        switch (ext) {
            case "docx": case "txt": case "rtf": case "odt": return "word";
            case "xlsx": case "csv": case "ods": return "cell";
            case "pptx": case "odp": return "slide";
            // doc/xls/ppt 旧格式 OnlyOffice 需转换，MVP 暂不支持编辑
            default: return null;
        }
    }

    /** 扩展名 → Content-Type */
    private String getContentType(String ext) {
        switch (ext) {
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "pdf": return "application/pdf";
            default: return "application/octet-stream";
        }
    }
}
