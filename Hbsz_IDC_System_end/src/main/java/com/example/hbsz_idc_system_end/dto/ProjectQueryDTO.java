package com.example.hbsz_idc_system_end.dto;

import com.example.hbsz_idc_system_end.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 项目查询 DTO（查询对象）。
 * 继承 PageQuery，用于项目列表的关键字、状态、归档等条件分页查询。
 *
 * @author hbsz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectQueryDTO extends PageQuery implements Serializable {

    /** 关键字（模糊查询） */
    private String keyword;

    /** 项目状态 */
    private String status;

    /** 项目类型 */
    private String type;

    /** 优先级 */
    private String priority;

    /** 是否已归档 */
    private Boolean archived;

    /** 项目经理 */
    private String manager;

    /** 开始日期起始 */
    private String startDateBegin;

    /** 开始日期截止 */
    private String startDateEnd;

    /** 工程师可见的项目ID列表（由服务层注入，用于数据隔离；前端不传） */
    private java.util.List<Long> visibleProjectIds;

}
