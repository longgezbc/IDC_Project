package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.SystemConfigDTO;
import com.example.hbsz_idc_system_end.service.ISystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置控制器。
 * <p>
 * 业务模块：系统全局配置（对应前端 Settings.vue 系统配置模块）。
 * 基础请求路径：{@code /system/config}。
 * 负责系统名称、主题色、默认视图等全局配置项的读取与更新。
 *
 * @author hbsz
 */
@Tag(name = "系统配置", description = "系统名称、主题色、默认视图等全局配置")
@RestController
@RequestMapping("/system/config")
public class SystemConfigController {

    private final ISystemConfigService systemConfigService;

    public SystemConfigController(ISystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /**
     * 获取系统配置。
     * <p>HTTP：GET /system/config。读取当前系统全局配置项。
     *
     * @return 系统全局配置数据
     */
    @Operation(summary = "获取系统配置", description = "获取系统全局配置项")
    @GetMapping
    public Result<SystemConfigDTO> getConfig() {
        return Result.success(systemConfigService.getConfig());
    }

    /**
     * 更新系统配置。
     * <p>HTTP：PUT /system/config。更新系统全局配置项。
     * 权限：{@code system:config:edit}。
     *
     * @param dto 待更新的系统配置数据
     * @return 更新后的系统配置数据
     */
    @Operation(summary = "更新系统配置", description = "更新系统全局配置项（需要系统配置编辑权限）")
    @RequiresPermission("system:config:edit")
    @PutMapping
    public Result<SystemConfigDTO> updateConfig(@Valid @RequestBody SystemConfigDTO dto) {
        return Result.success("系统配置更新成功", systemConfigService.updateConfig(dto));
    }

}
