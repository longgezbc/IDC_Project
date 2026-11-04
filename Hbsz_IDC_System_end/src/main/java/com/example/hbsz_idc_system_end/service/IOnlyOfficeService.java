package com.example.hbsz_idc_system_end.service;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

/**
 * OnlyOffice 在线编辑服务。
 * <p>
 * 提供编辑配置生成、保存回调处理、文档原文件下载（供 OnlyOffice DS 拉取）能力。
 *
 * @author hbsz
 */
public interface IOnlyOfficeService {

    /**
     * 生成编辑器初始化配置（含 JWT 签名）。
     *
     * @param docId 文档ID
     * @param mode  edit 或 view
     * @return 配置 Map（前端传给 DocsAPI.DocEditor）
     */
    Map<String, Object> buildEditConfig(Long docId, String mode);

    /**
     * 处理 OnlyOffice DS 的保存回调，下载新版本文件并落盘。
     *
     * @param body 回调请求体
     * @return { error: 0 } 表示成功
     */
    Map<String, Object> handleCallback(Map<String, Object> body);

    /**
     * 供 OnlyOffice DS 拉取文档原文件（免用户认证）。
     *
     * @param docId   文档ID
     * @param response HTTP 响应
     */
    void downloadFile(Long docId, HttpServletResponse response);
}
