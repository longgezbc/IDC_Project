package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 新增文档版本 DTO（请求参数封装）。
 * 用于为已有文档提交新的版本号及说明。
 *
 * @author hbsz
 */
@Data
public class DocumentVersionDTO implements Serializable {

    /** 版本号：@NotBlank 校验不能为空 */
    @NotBlank(message = "版本号不能为空")
    private String version;

    /** 版本备注 */
    private String remark;

    /** 版本文件存储路径（Controller 层设置，Service 层落库） */
    private String fileUrl;

}
