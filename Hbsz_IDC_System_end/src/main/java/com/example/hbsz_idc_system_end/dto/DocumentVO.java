package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 文档视图对象（响应结果封装）。
 * 用于向前端返回文档详情，包含版本历史与标签。
 *
 * @author hbsz
 */
@Data
public class DocumentVO implements Serializable {

    /** 文档ID */
    private String id;

    /** 所属项目ID */
    private String projectId;

    /** 所属项目名称 */
    private String projectName;

    /** 文档名称 */
    private String name;

    /** 文档分类 */
    private String category;

    /** 当前版本号 */
    private String version;

    /** 文档状态：待审核/已审核/已驳回 */
    private String status;

    /** 审核人姓名 */
    private String reviewerName;

    /** 审核时间 */
    private String reviewTime;

    /** 审核意见 */
    private String reviewRemark;

    /** 文件大小（展示用文本） */
    private String size;

    /** 文件类型 */
    private String fileType;

    /** 上传人 */
    private String uploader;

    /** 上传日期 */
    private String uploadDate;

    /** 文档描述 */
    private String description;

    /** 版本历史列表 */
    private List<VersionVO> versions;

    /** 标签列表 */
    private List<String> tags;

    /**
     * 版本VO
     */
    @Data
    public static class VersionVO implements Serializable {
        private String version;
        private String date;
        private String uploader;
        private String remark;
    }

}
