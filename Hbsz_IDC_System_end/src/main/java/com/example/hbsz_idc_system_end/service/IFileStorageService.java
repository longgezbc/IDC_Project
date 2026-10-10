package com.example.hbsz_idc_system_end.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * 文件存储服务接口
 * 负责文档文件的本地磁盘存储与读取
 *
 * @author hbsz
 */
public interface IFileStorageService {

    /**
     * 存储上传文件到本地磁盘
     *
     * @param file 上传的文件
     * @return 文件相对存储路径（如：2024/06/uuid.pdf）
     */
    String storeFile(MultipartFile file);

    /**
     * 加载文件为 java.io.File
     *
     * @param relativePath 相对存储路径
     * @return 文件对象
     */
    File loadFile(String relativePath);

    /**
     * 删除文件
     *
     * @param relativePath 相对存储路径
     * @return 是否删除成功
     */
    boolean deleteFile(String relativePath);

    /**
     * 获取文件存储根目录的绝对路径
     *
     * @return 绝对路径字符串
     */
    String getUploadDir();

}
