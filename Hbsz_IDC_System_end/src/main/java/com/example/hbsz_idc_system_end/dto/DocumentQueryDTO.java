package com.example.hbsz_idc_system_end.dto;

import com.example.hbsz_idc_system_end.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 文档查询 DTO（查询对象）。
 * 继承 PageQuery，用于文档列表的关键字、分类、时间等条件分页查询。
 *
 * @author hbsz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentQueryDTO extends PageQuery implements Serializable {

    /** 关键字（模糊查询） */
    private String keyword;

    /** 文档分类 */
    private String category;

    /** 所属项目ID */
    private Long projectId;

    /** 文档状态 */
    private String status;

    /** 文件类型 */
    private String fileType;

    /** 上传人 */
    private String uploader;

    /** 上传日期起始 */
    private String uploadDateBegin;

    /** 上传日期截止 */
    private String uploadDateEnd;

    /** 当前用户可见的项目ID列表（由服务层注入，用于数据隔离；前端不传） */
    private List<Long> visibleProjectIds;

}
