package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位视图对象（响应结果封装）。
 * 用于向前端返回岗位基本信息及关联用户数量统计，便于岗位管理列表展示。
 *
 * @author hbsz
 */
@Data
public class PositionVO implements Serializable {

    /** 岗位ID */
    private Long id;

    /** 岗位编码 */
    private String code;

    /** 岗位名称 */
    private String name;

    /** 职级（1最高，9最低） */
    private Integer level;

    /** 岗位描述 */
    private String description;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 关联该岗位的用户数量（用于删除前提示） */
    private Integer userCount;

}
