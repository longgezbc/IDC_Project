package com.example.hbsz_idc_system_end.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 岗位分页查询 DTO（请求参数封装）。
 * 继承 {@link PageQuery} 获取分页参数，并附加岗位专属筛选条件（编码/名称/状态）。
 * 所有筛选条件均为可选，留空表示不限制。
 *
 * @author hbsz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PositionQueryDTO extends PageQuery implements Serializable {

    /** 岗位编码（模糊匹配） */
    private String code;

    /** 岗位名称（模糊匹配） */
    private String name;

    /** 状态：1启用 0禁用（留空表示不限） */
    private Integer status;

}
