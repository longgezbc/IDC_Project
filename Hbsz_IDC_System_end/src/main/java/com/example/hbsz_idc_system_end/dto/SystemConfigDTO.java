package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 系统配置 DTO（请求/响应参数封装）。
 * 用于系统设置页面读取与保存全局配置，字段与前端 systemConfig 对齐。
 *
 * @author hbsz
 */
@Data
public class SystemConfigDTO implements Serializable {

    /** 系统名称 */
    private String name;

    /** 系统版本（只读，后端维护） */
    private String version;

    /** 主题色 */
    private String themeColor;

    /** 默认项目视图：table / card */
    private String defaultView;

    /** 每页显示条数 */
    private Integer pageSize;

    /** 表单自动保存 */
    private Boolean autoSave;

}
