package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 项目模板实体。定义可复用的项目骨架（默认字段、阶段、文档清单等配置）。
 * @author hbsz
 */
@Data
@TableName("biz_project_template")
public class BizProjectTemplate extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String category;
    private String description;
    /** 模板配置JSON：默认字段值、阶段定义、文档清单、岗位职责等结构化配置 */
    private String configJson;
    private Integer status;
    private Long createdBy;
}
