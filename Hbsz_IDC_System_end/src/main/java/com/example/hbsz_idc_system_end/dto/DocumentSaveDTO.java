package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 文档保存 DTO（请求参数封装）。
 * 用于新增或修改项目文档时提交文档元数据。
 *
 * @author hbsz
 */
@Data
public class DocumentSaveDTO implements Serializable {

    /** 所属项目ID：@NotNull 校验不能为空 */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 文档名称：@NotBlank 校验不能为空 */
    @NotBlank(message = "文档名称不能为空")
    private String name;

    /** 文档分类：@NotBlank 校验不能为空 */
    @NotBlank(message = "文档分类不能为空")
    private String category;

    /** 版本号 */
    private String version;

    /** 文档状态 */
    private String status;

    /** 文件类型 */
    private String fileType;

    /** 文件大小（展示用文本，如 1.2MB） */
    private String size;

    /** 文件存储相对路径（由文件存储服务生成） */
    private String fileUrl;

    /** 文件大小（字节） */
    private Long fileSizeBytes;

    /** 文档描述 */
    private String description;

    /** 标签列表 */
    private List<String> tags;

}
