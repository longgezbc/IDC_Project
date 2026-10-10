package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.*;
import com.example.hbsz_idc_system_end.entity.BizDocument;
import com.example.hbsz_idc_system_end.service.IDocumentService;
import com.example.hbsz_idc_system_end.service.IFileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档管理控制器。
 * <p>
 * 业务模块：项目文档管理。
 * 基础请求路径：{@code /documents}。
 * 提供文档的增删改查、分类统计、版本管理以及文件上传、下载与在线预览等接口，
 * 文件操作依赖文件存储服务落盘与读取。
 *
 * @author hbsz
 */
@Tag(name = "文档管理", description = "文档CRUD、版本管理、下载预览等接口")
@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final IDocumentService documentService;
    private final IFileStorageService fileStorageService;
    // 引入 ObjectMapper 用于规范化序列化 JSON 响应，避免手动拼接字符串导致转义/编码异常
    private final ObjectMapper objectMapper;

    public DocumentController(IDocumentService documentService, IFileStorageService fileStorageService,
                              ObjectMapper objectMapper) {
        this.documentService = documentService;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    /**
     * 文档列表查询。
     * <p>HTTP：GET /documents。分页查询文档列表。
     * 权限：{@code document:query}。
     *
     * @param query 文档查询条件（含分页参数、筛选条件）
     * @return 文档分页结果
     */
    @Operation(summary = "文档列表查询", description = "分页查询文档列表")
    @RequiresPermission("document:query")
    @GetMapping
    public Result<PageResult<DocumentVO>> list(DocumentQueryDTO query) {
        return Result.success(documentService.queryPage(query));
    }

    /**
     * 文档分类统计。
     * <p>HTTP：GET /documents/category-stats。按分类统计文档数量，用于左侧分类菜单的计数显示。
     * 权限：{@code document:query}。
     *
     * @return 各分类的文档数量统计列表
     */
    @Operation(summary = "文档分类统计", description = "按分类统计文档数量，用于左侧分类菜单的计数显示")
    @RequiresPermission("document:query")
    @GetMapping("/category-stats")
    public Result<List<java.util.Map<String, Object>>> categoryStats() {
        return Result.success(documentService.countByCategory());
    }

    /**
     * 文档详情。
     * <p>HTTP：GET /documents/{id}。
     * 权限：{@code document:query}。
     *
     * @param id 文档 ID
     * @return 文档详情
     */
    @Operation(summary = "文档详情", description = "根据ID查询文档详情")
    @RequiresPermission("document:query")
    @GetMapping("/{id}")
    public Result<DocumentVO> detail(@PathVariable Long id) {
        return Result.success(documentService.getDetail(id));
    }

    /**
     * 上传文档。
     * <p>HTTP：POST /documents（consumes multipart/form-data）。先将文件落盘存储，
     * 再组装文档元信息（名称、分类、标签、版本、文件大小/类型等）并持久化。
     * 权限：{@code document:add}。
     *
     * @param file        上传的文件（multipart）
     * @param projectId   所属项目 ID
     * @param category    文档分类
     * @param name        文档名称，可选，为空时取原始文件名
     * @param description 文档描述，可选
     * @param tags        文档标签，可选，以英文逗号分隔
     * @param version     文档版本号，可选
     * @return 上传后的文档信息
     */
    @Operation(summary = "上传文档", description = "上传新文档（multipart/form-data）")
    @RequiresPermission("document:add")
    @PostMapping(consumes = "multipart/form-data")
    public Result<DocumentVO> create(
            @RequestParam("file") MultipartFile file,
            @RequestParam("projectId") Long projectId,
            @RequestParam("category") String category,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "version", required = false) String version) {
        // 存储文件到磁盘
        String fileUrl = fileStorageService.storeFile(file);
        DocumentSaveDTO dto = new DocumentSaveDTO();
        dto.setProjectId(projectId);
        dto.setCategory(category);
        dto.setName(name != null ? name : file.getOriginalFilename());
        dto.setDescription(description);
        dto.setVersion(version);
        dto.setFileUrl(fileUrl);
        dto.setFileSizeBytes(file.getSize());
        if (tags != null && !tags.isEmpty()) {
            dto.setTags(Arrays.asList(tags.split(",")));
        } else {
            dto.setTags(Collections.emptyList());
        }
        // 文件元信息
        dto.setFileType(extractFileType(file.getOriginalFilename()));
        dto.setSize(formatFileSize(file.getSize()));
        return Result.success("文档上传成功", documentService.create(dto));
    }

    /**
     * 更新文档。
     * <p>HTTP：PUT /documents/{id}。更新文档信息（不含文件内容）。
     * 权限：{@code document:edit}。
     *
     * @param id  文档 ID
     * @param dto 文档保存数据
     * @return 更新后的文档信息
     */
    @Operation(summary = "更新文档", description = "更新文档信息")
    @RequiresPermission("document:edit")
    @PutMapping("/{id}")
    public Result<DocumentVO> update(@PathVariable Long id, @Valid @RequestBody DocumentSaveDTO dto) {
        return Result.success("文档更新成功", documentService.update(id, dto));
    }

    /**
     * 删除文档。
     * <p>HTTP：DELETE /documents/{id}。
     * 权限：{@code document:remove}。
     *
     * @param id 文档 ID
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "删除文档", description = "根据ID删除文档")
    @RequiresPermission("document:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.success("文档删除成功", null);
    }

    /**
     * 新增文档版本。
     * <p>HTTP：POST /documents/{id}/versions（consumes multipart/form-data）。
     * 上传新版本文件并落盘，为指定文档追加版本记录。
     * 权限：{@code document:add}。
     *
     * @param id      文档 ID
     * @param file    新版本文件（multipart）
     * @param version 版本号
     * @param remark  版本备注，可选
     * @return 新增版本后的文档信息
     */
    @Operation(summary = "新增文档版本", description = "为文档新增版本（multipart/form-data）")
    @RequiresPermission("document:add")
    @PostMapping(value = "/{id}/versions", consumes = "multipart/form-data")
    public Result<DocumentVO> addVersion(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("version") String version,
            @RequestParam(value = "remark", required = false) String remark) {
        // 存储新版本文件
        String fileUrl = fileStorageService.storeFile(file);
        DocumentVersionDTO dto = new DocumentVersionDTO();
        dto.setVersion(version);
        dto.setRemark(remark);
        dto.setFileUrl(fileUrl);
        return Result.success("版本上传成功", documentService.addVersion(id, dto));
    }

    /**
     * 文档下载。
     * <p>HTTP：GET /documents/{id}/download。以附件形式输出文件二进制流；
     * 文件名按 UTF-8 编码，并声明 {@code Accept-Ranges: bytes} 以提示客户端支持断点续传能力
     * （当前实现未解析 Range 请求头，仅声明能力）。文档无文件时返回 JSON 错误提示。
     * 权限：{@code document:download}。
     *
     * @param id       文档 ID
     * @param response HTTP 响应对象，用于写出文件流与下载头
     * @throws IOException 读取文件或写出响应流发生 IO 异常时抛出
     */
    @Operation(summary = "文档下载", description = "下载文档文件")
    @RequiresPermission("document:download")
    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        BizDocument document = documentService.getEntityById(id);
        if (!StringUtils.hasText(document.getFileUrl())) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            // 使用 ObjectMapper 序列化替代手动 JSON 字符串拼接，
            // 避免特殊字符（如引号、换行）未转义导致的 JSON 注入或解析失败
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("code", 1);
            errorBody.put("message", "该文档无文件可下载");
            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            return;
        }
        File file = fileStorageService.loadFile(document.getFileUrl());
        String filename = URLEncoder.encode(document.getName(), StandardCharsets.UTF_8);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentLengthLong(file.length());
        // 优化：声明支持 Accept-Ranges，便于客户端断点续传与并发分块下载
        // 注意：当前实现未解析 Range 请求头，仅声明能力，
        // 客户端可基于此头部决定是否使用多线程下载；完整 Range 支持需解析 Range 头并返回 206
        response.setHeader("Accept-Ranges", "bytes");
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(file.toPath(), out);
            out.flush();
        }
    }

    /**
     * 文档预览。
     * <p>HTTP：GET /documents/{id}/preview。根据文件扩展名设置 Content-Type：
     * PDF、图片（jpg/jpeg/png/gif）、纯文本（txt/md/csv）直接内联预览，其余类型回退为附件下载。
     * 文档无文件时返回 JSON 错误提示。
     * 权限：{@code document:download}。
     *
     * @param id       文档 ID
     * @param response HTTP 响应对象，用于写出文件流与内容类型头
     * @throws IOException 读取文件或写出响应流发生 IO 异常时抛出
     */
    @Operation(summary = "文档预览", description = "在线预览文档（图片/PDF直接预览，其他类型下载）")
    @RequiresPermission("document:download")
    @GetMapping("/{id}/preview")
    public void preview(@PathVariable Long id, HttpServletResponse response) throws IOException {
        BizDocument document = documentService.getEntityById(id);
        if (!StringUtils.hasText(document.getFileUrl())) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            // 使用 ObjectMapper 序列化替代手动 JSON 字符串拼接，
            // 避免特殊字符未转义导致的 JSON 注入或解析失败
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("code", 1);
            errorBody.put("message", "该文档无文件可预览");
            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            return;
        }
        File file = fileStorageService.loadFile(document.getFileUrl());
        // 根据扩展名设置Content-Type
        String format = document.getFileFormat() != null ? document.getFileFormat().toLowerCase() : "";
        String contentType;
        switch (format) {
            case "pdf":
                contentType = MediaType.APPLICATION_PDF_VALUE;
                break;
            case "jpg":
            case "jpeg":
                contentType = MediaType.IMAGE_JPEG_VALUE;
                break;
            case "png":
                contentType = MediaType.IMAGE_PNG_VALUE;
                break;
            case "gif":
                contentType = MediaType.IMAGE_GIF_VALUE;
                break;
            case "txt":
            case "md":
            case "csv":
                contentType = MediaType.TEXT_PLAIN_VALUE;
                break;
            default:
                // 其他类型不支持在线预览，返回下载
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
                response.setHeader("Content-Disposition",
                        "attachment; filename=\"" + URLEncoder.encode(document.getName(), StandardCharsets.UTF_8) + "\"");
        }
        response.setContentType(contentType);
        response.setContentLengthLong(file.length());
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(file.toPath(), out);
            out.flush();
        }
    }

    /**
     * 审核文档。
     * <p>HTTP：POST /documents/{id}/review。
     * 审核通过将状态置为 "已审核"，驳回置为 "已驳回"，同时记录审核人与审核意见。
     * 权限：{@code document:review}。
     *
     * @param id  文档 ID
     * @param dto 审核参数（是否通过、审核意见）
     * @return 审核后的文档信息
     */
    @Operation(summary = "审核文档", description = "审核上传的文档（通过/驳回）")
    @RequiresPermission("document:review")
    @PostMapping("/{id}/review")
    public Result<DocumentVO> review(@PathVariable Long id, @Valid @RequestBody DocumentReviewDTO dto) {
        return Result.success("文档审核完成", documentService.review(id, dto));
    }

    /**
     * 从文件名提取文件类型（扩展名大写）
     */
    private String extractFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "UNKNOWN";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toUpperCase();
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1fMB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
        }
    }

}
