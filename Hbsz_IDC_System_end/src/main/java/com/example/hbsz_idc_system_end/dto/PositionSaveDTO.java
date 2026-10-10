package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 岗位新增/修改 DTO（请求参数封装）。
 * 用于后台岗位管理的创建与更新，包含岗位编码、名称、职级等核心字段。
 *
 * @author hbsz
 */
@Data
public class PositionSaveDTO implements Serializable {

    /** 岗位编码（唯一，如 pm/engineer），3-50 位字母数字下划线 */
    @NotBlank(message = "岗位编码不能为空")
    @Size(min = 2, max = 50, message = "岗位编码长度需在2-50个字符之间")
    private String code;

    /** 岗位名称 */
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 50, message = "岗位名称长度不能超过50个字符")
    private String name;

    /** 职级（1最高，9最低） */
    @NotNull(message = "职级不能为空")
    private Integer level;

    /** 岗位描述 */
    @Size(max = 200, message = "岗位描述长度不能超过200个字符")
    private String description;

    /** 状态：1启用 0禁用（新增时可选，默认启用） */
    private Integer status;

}
