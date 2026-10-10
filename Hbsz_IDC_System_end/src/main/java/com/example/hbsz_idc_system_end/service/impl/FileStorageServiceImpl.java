package com.example.hbsz_idc_system_end.service.impl;

import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.service.IFileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 文件存储服务实现
 * <p>
 * 负责文档文件在本地磁盘的存储、读取与删除，并内置多重安全防护：
 * <ul>
 *   <li>大小限制：单文件不超过 100MB；</li>
 *   <li>类型白名单：仅允许既定扩展名；</li>
 *   <li>魔数校验：比对文件头（magic number）与扩展名，阻断伪装文件上传；</li>
 *   <li>路径穿越防护：拒绝含 ".." 的相对路径，防止越权读写存储目录之外的文件。</li>
 * </ul>
 * 文件按 yyyy/MM/dd 目录分层存储，文件名使用 UUID 以避免重名冲突与原始文件名信息泄露。
 *
 * @author hbsz
 */
@Slf4j
@Service
public class FileStorageServiceImpl implements IFileStorageService {

    /** 文件存储根目录，可通过 hbsz.file.upload-dir 配置 */
    @Value("${hbsz.file.upload-dir:./uploads}")
    private String uploadDir;

    /** 允许的文件扩展名（白名单） */
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "PDF", "DOC", "DOCX", "XLS", "XLSX", "PPT", "PPTX",
            "TXT", "CSV", "MD",
            "JPG", "JPEG", "PNG", "GIF", "BMP",
            "ZIP", "RAR", "7Z",
            "DWG", "DXF", "VSD", "VSDX"
    ));

    /** 最大文件大小（100MB） */
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /**
     * 常见文件类型的 magic number（文件头）签名
     * 用于校验文件实际类型，防止伪装文件上传
     * <p>
     * P0-4.2 加固说明：
     *   原实现未为 DOCX/XLSX/PPTX（Office Open XML 格式）登记签名，
     *   导致 validateFileSignature 在 expected==null 时直接跳过校验，
     *   攻击者可将任意可执行文件改名为 .docx 上传绕过校验。
     *   现补充 OOXML 签名（PK\x03\x04，与 ZIP 共享 — 因 OOXML 本质是 ZIP 容器），
     *   至少要求文件头匹配 ZIP 格式，杜绝完全无关类型的伪装。
     *   注：要严格区分 ZIP 与 OOXML 需进一步解析 ZIP 内 [Content_Types].xml，
     *   该深度校验后续可通过 Apache Tika 实现（P1 任务）。
     */
    private static final java.util.Map<String, byte[]> FILE_SIGNATURES = new java.util.HashMap<>();
    static {
        FILE_SIGNATURES.put("PDF", new byte[]{0x25, 0x50, 0x44, 0x46}); // %PDF
        FILE_SIGNATURES.put("JPG", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        FILE_SIGNATURES.put("JPEG", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        FILE_SIGNATURES.put("PNG", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        FILE_SIGNATURES.put("GIF", new byte[]{0x47, 0x49, 0x46, 0x38}); // GIF8
        FILE_SIGNATURES.put("ZIP", new byte[]{0x50, 0x4B, 0x03, 0x04}); // PK..
        FILE_SIGNATURES.put("RAR", new byte[]{0x52, 0x61, 0x72, 0x21}); // Rar!
        FILE_SIGNATURES.put("DOC", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}); // Office 97-2003
        FILE_SIGNATURES.put("XLS", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});
        FILE_SIGNATURES.put("PPT", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});
        // P0-4.2：OOXML 格式（Office Open XML）本质为 ZIP 容器，文件头为 PK\x03\x04
        FILE_SIGNATURES.put("DOCX", new byte[]{0x50, 0x4B, 0x03, 0x04});
        FILE_SIGNATURES.put("XLSX", new byte[]{0x50, 0x4B, 0x03, 0x04});
        FILE_SIGNATURES.put("PPTX", new byte[]{0x50, 0x4B, 0x03, 0x04});
        // 7z 与 Rar5：原 RAR4 签名为 Rar!\x1A\x07，RAR5 为 Rar!\x1A\x07\x01\x00
        FILE_SIGNATURES.put("7Z", new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C}); // 7z\xBC\xAF\x27\x1C
    }

    /**
     * 存储上传文件到本地磁盘
     * <p>
     * 依次校验非空、大小上限、扩展名白名单与文件头签名，通过后按日期建立目录、
     * 以 UUID 重命名落盘，最终返回相对路径（相对存储根目录）供入库使用。
     */
    @Override
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED,
                    "文件大小超过限制（最大100MB）");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toUpperCase())) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED,
                    "不支持的文件类型：" + extension);
        }

        // 校验文件头（magic number），防止伪装文件上传
        validateFileSignature(file, extension);

        // 按日期分目录：yyyy/MM/dd
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String storedFilename = UUID.randomUUID().toString().replace("-", "") + "." + extension.toLowerCase();

        Path targetDir = Paths.get(uploadDir, datePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            log.error("[文件存储] 创建目录失败: {}", targetDir, e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "文件存储目录创建失败");
        }

        Path targetPath = targetDir.resolve(storedFilename);
        try {
            file.transferTo(targetPath.toFile());
            log.info("[文件存储] 文件保存成功: {} -> {}", originalFilename, targetPath);
        } catch (IOException e) {
            log.error("[文件存储] 文件保存失败: {}", originalFilename, e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "文件保存失败");
        }

        // 返回相对路径（用于入库）
        return datePath + "/" + storedFilename;
    }

    /**
     * 加载文件为 java.io.File
     */
    @Override
    public File loadFile(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "文件路径为空");
        }
        // 防御路径穿越攻击：
        // 1. 拒绝绝对路径（以 / 或盘符开头），避免 Paths.get(uploadDir, "/etc/passwd") 返回绝对路径绕过基础目录
        if (relativePath.startsWith("/") || relativePath.matches("^[A-Za-z]:.*")) {
            log.warn("[文件存储] 检测到绝对路径攻击: {}", relativePath);
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "非法文件路径");
        }
        // 2. 拒绝包含 .. 的相对路径穿越
        if (relativePath.contains("..")) {
            log.warn("[文件存储] 检测到路径穿越攻击: {}", relativePath);
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "非法文件路径");
        }
        // 3. normalize 后校验解析路径仍在 uploadDir 内，防止符号链接等绕过
        Path filePath = Paths.get(uploadDir, relativePath).toAbsolutePath().normalize();
        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!filePath.startsWith(basePath)) {
            log.warn("[文件存储] 检测到路径越界: {} (base={})", filePath, basePath);
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "非法文件路径");
        }
        File file = filePath.toFile();
        if (!file.exists() || !file.isFile()) {
            log.warn("[文件存储] 文件不存在: {}", filePath);
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "文件不存在");
        }
        return file;
    }

    /**
     * 删除文件
     */
    @Override
    public boolean deleteFile(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return false;
        }
        // 路径穿越防护（与 loadFile 保持一致）：拒绝绝对路径 + .. 越界 + normalize 后路径前缀校验
        if (relativePath.startsWith("/") || relativePath.matches("^[A-Za-z]:.*")) {
            log.warn("[文件存储] 删除文件检测到绝对路径攻击: {}", relativePath);
            return false;
        }
        if (relativePath.contains("..")) {
            log.warn("[文件存储] 检测到路径穿越攻击: {}", relativePath);
            return false;
        }
        try {
            Path filePath = Paths.get(uploadDir, relativePath).toAbsolutePath().normalize();
            Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!filePath.startsWith(basePath)) {
                log.warn("[文件存储] 删除文件检测到路径越界: {}", filePath);
                return false;
            }
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("[文件存储] 文件删除成功: {}", filePath);
            }
            return deleted;
        } catch (IOException e) {
            log.error("[文件存储] 文件删除失败: {}", relativePath, e);
            return false;
        }
    }

    /**
     * 从文件名提取扩展名（不含点号，小写）
     */
    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    /**
     * 校验文件头（magic number）与扩展名是否匹配
     * 对于无签名记录的扩展名（如 txt/csv/md/dwg），跳过校验
     *
     * @param file      上传的文件
     * @param extension 文件扩展名（大写）
     */
    private void validateFileSignature(MultipartFile file, String extension) {
        String upperExt = extension.toUpperCase();
        byte[] expected = FILE_SIGNATURES.get(upperExt);
        if (expected == null) {
            // 无签名记录的文件类型，跳过校验
            return;
        }
        byte[] header;
        try {
            header = new byte[expected.length];
            int read = file.getInputStream().read(header);
            if (read < expected.length) {
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED,
                        "文件内容不完整或类型不匹配");
            }
        } catch (IOException e) {
            log.error("[文件存储] 读取文件头失败: {}", file.getOriginalFilename(), e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "文件校验失败");
        }
        for (int i = 0; i < expected.length; i++) {
            if (header[i] != expected[i]) {
                log.warn("[文件存储] 文件头校验失败: {}，声明的扩展名: {}", file.getOriginalFilename(), extension);
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED,
                        "文件内容与扩展名不匹配，请勿伪装文件类型");
            }
        }
    }

    /**
     * 获取文件存储根目录的绝对路径
     */
    @Override
    public String getUploadDir() {
        return Paths.get(uploadDir).toAbsolutePath().normalize().toString();
    }

}
