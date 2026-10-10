package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.OperationLog;
import com.example.hbsz_idc_system_end.common.annotation.RateLimit;
import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.service.IFileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用文件控制器。
 * <p>
 * 业务模块：通用文件上传与预览。
 * 基础请求路径：{@code /files}。
 * <p>
 * 主要用于"进度填报"等场景下上传施工图片：
 * <ul>
 *   <li>{@code POST /files/upload}：上传单个文件，返回相对路径，前端将路径随填报数据一起提交；</li>
 *   <li>{@code GET /files/preview/**}：通过相对路径直接预览图片，
 *       已加入 WebMvcConfig 白名单，便于 {@code <img src>} 直接访问。</li>
 * </ul>
 * 安全说明：
 * <ul>
 *   <li>上传接口需登录且具备 {@code task:progress} 权限；</li>
 *   <li>预览接口对匿名开放，但图片文件名使用 UUID，几乎不可猜测；
 *       {@link IFileStorageService#loadFile(String)} 已内置 ".." 路径穿越防护。</li>
 * </ul>
 *
 * @author hbsz
 */
@Tag(name = "通用文件", description = "通用文件上传、预览接口")
@RestController
@RequestMapping("/files")
public class FileController {

    private final IFileStorageService fileStorageService;
    // 引入 ObjectMapper 用于规范化序列化 JSON 响应，避免手动拼接字符串导致转义/编码异常
    private final ObjectMapper objectMapper;

    public FileController(IFileStorageService fileStorageService, ObjectMapper objectMapper) {
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    /**
     * 上传单个文件（用于进度填报施工图片等场景）。
     * <p>HTTP：POST /files/upload（consumes multipart/form-data）。
     * 权限：{@code task:progress}（工程师具备此权限）。
     * 限流：60秒内最多上传10个文件，防止恶意上传消耗存储资源。
     * 操作日志：记录文件上传操作（不记录请求参数，避免文件内容泄露）。
     *
     * @param file 上传的文件（multipart）
     * @return 含 url 字段的对象，url 为相对存储路径，可直接用于 &lt;img src&gt; 与 /files/preview 拼接
     */
    @Operation(summary = "上传文件", description = "上传单个文件（如施工图片），返回相对路径")
    @RequiresPermission("task:progress")
    @RateLimit(key = "file_upload", period = 60, count = 10)
    @OperationLog(module = "文件管理", type = "上传", description = "上传文件", recordParams = false)
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String relativePath = fileStorageService.storeFile(file);
        Map<String, String> data = new HashMap<>();
        // 相对路径，前端预览时拼接 /files/preview/ 前缀
        data.put("url", relativePath);
        data.put("previewUrl", "/files/preview/" + relativePath);
        return Result.success("上传成功", data);
    }

    /**
     * 文件预览（内联输出，便于浏览器直接展示图片）。
     * <p>HTTP：GET /files/preview/**。根据扩展名设置 Content-Type；
     * 图片类型直接内联展示，其他类型回退为下载。
     * <p>已加入 WebMvcConfig 白名单，便于 {@code <img src="/files/preview/xxx">} 直接访问。
     *
     * @param request  HTTP 请求对象，用于提取剩余路径
     * @param response HTTP 响应对象
     * @throws IOException 读取或写出文件流时发生 IO 异常
     */
    @Operation(summary = "文件预览", description = "按相对路径预览文件（图片内联展示，其他下载）")
    @GetMapping("/preview/**")
    public void preview(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 从请求 URI 中提取 "/preview/" 之后的部分作为相对路径
        String requestUri = request.getRequestURI();
        String prefix = "/files/preview/";
        int idx = requestUri.indexOf(prefix);
        String relativePath = idx >= 0 ? requestUri.substring(idx + prefix.length()) : "";
        // URL 解码（中文文件名场景）
        relativePath = UriUtils.decode(relativePath, StandardCharsets.UTF_8);

        if (!StringUtils.hasText(relativePath)) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            // 使用 ObjectMapper 序列化替代手动 JSON 字符串拼接，
            // 避免特殊字符未转义导致的 JSON 注入或解析失败
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("code", 1);
            errorBody.put("message", "文件路径为空");
            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            return;
        }
        File file;
        try {
            file = fileStorageService.loadFile(relativePath);
        } catch (BusinessException e) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            // 使用 ObjectMapper 序列化，避免 e.getMessage() 含有特殊字符（如引号、换行）
            // 导致 JSON 解析失败或注入风险
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("code", e.getCode());
            errorBody.put("message", e.getMessage());
            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            return;
        }
        String extension = extractExtension(relativePath);
        String contentType = resolveContentType(extension);
        response.setContentType(contentType);
        response.setContentLengthLong(file.length());
        // 非图片类型，强制附件下载
        if (MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)) {
            String filename = file.getName();
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + UriUtils.encode(filename, StandardCharsets.UTF_8) + "\"");
            // 文件下载响应必须包含 Accept-Ranges: bytes 头部（项目硬约束）
            response.setHeader("Accept-Ranges", "bytes");
        }
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(file.toPath(), out);
            out.flush();
        }
    }

    /**
     * 从文件路径提取扩展名（小写）
     */
    private String extractExtension(String path) {
        if (path == null || !path.contains(".")) {
            return "";
        }
        return path.substring(path.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 根据扩展名映射 Content-Type；未识别类型回退为通用二进制流
     * <p>
     * P0-4.2 补充：原实现仅覆盖图片与少量文本类型，DOCX/XLSX/PPTX/PPT/DWG 等
     * 类型未明确映射，统一回退为 OCTET_STREAM 触发下载（虽安全但用户体验欠佳）。
     * 现补充 Office 与 CAD 类型映射。SVG 故意不在此映射 —— SVG 可内嵌 JavaScript
     * 与外部资源引用，强制以 OCTET_STREAM 下载避免 XSS 风险。
     */
    private String resolveContentType(String extension) {
        switch (extension) {
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG_VALUE;
            case "png":
                return MediaType.IMAGE_PNG_VALUE;
            case "gif":
                return MediaType.IMAGE_GIF_VALUE;
            case "bmp":
                return "image/bmp";
            case "pdf":
                return MediaType.APPLICATION_PDF_VALUE;
            case "txt":
            case "md":
            case "csv":
                return MediaType.TEXT_PLAIN_VALUE;
            // P0-4.2：补充 Office 与 CAD 类型 Content-Type
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "dwg":
                return "application/acad";
            case "dxf":
                return "application/dxf";
            case "vsd":
                return "application/vnd.visio";
            case "vsdx":
                return "application/vnd.visio2013";
            case "zip":
                return "application/zip";
            case "rar":
                return "application/x-rar-compressed";
            case "7z":
                return "application/x-7z-compressed";
            default:
                return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

}
