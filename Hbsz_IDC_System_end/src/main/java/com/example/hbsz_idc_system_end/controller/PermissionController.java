package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.PermissionSaveDTO;
import com.example.hbsz_idc_system_end.dto.PermissionVO;
import com.example.hbsz_idc_system_end.service.IPermissionManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限管理控制器。
 * <p>
 * 业务模块：系统权限管理（RBAC 权限项维护）。
 * 基础请求路径：{@code /system/permissions}。
 * 提供权限项的精细化管理，支持权限粒度控制、权限继承（树形结构），涵盖平铺列表、权限树及增删改查。
 *
 * @author hbsz
 */
@Tag(name = "权限管理", description = "权限项管理、权限树")
@RestController
@RequestMapping("/system/permissions")
public class PermissionController {

    private final IPermissionManageService permissionManageService;

    public PermissionController(IPermissionManageService permissionManageService) {
        this.permissionManageService = permissionManageService;
    }

    /**
     * 查询所有权限（平铺列表）。
     * <p>HTTP：GET /system/permissions。返回不含层级结构的全部权限项。
     * 权限：{@code system:permission:query}。
     *
     * @return 权限项平铺列表
     */
    @Operation(summary = "查询所有权限（平铺列表）")
    @RequiresPermission("system:permission:query")
    @GetMapping
    public Result<List<PermissionVO>> listAll() {
        return Result.success(permissionManageService.listAll());
    }

    /**
     * 查询权限树形结构。
     * <p>HTTP：GET /system/permissions/tree。按父子关系组织权限项，返回树形结构。
     * 权限：{@code system:permission:query}。
     *
     * @return 权限项树形列表
     */
    @Operation(summary = "查询权限树形结构")
    @RequiresPermission("system:permission:query")
    @GetMapping("/tree")
    public Result<List<PermissionVO>> listTree() {
        return Result.success(permissionManageService.listTree());
    }

    /**
     * 查询权限详情。
     * <p>HTTP：GET /system/permissions/{id}。
     * 权限：{@code system:permission:query}。
     *
     * @param id 权限项 ID
     * @return 权限项详情
     */
    @Operation(summary = "查询权限详情")
    @RequiresPermission("system:permission:query")
    @GetMapping("/{id}")
    public Result<PermissionVO> getById(@PathVariable Long id) {
        return Result.success(permissionManageService.getById(id));
    }

    /**
     * 新增权限。
     * <p>HTTP：POST /system/permissions。
     * 权限：{@code system:permission:add}。
     *
     * @param dto 权限项保存数据
     * @return 新增后的权限项
     */
    @Operation(summary = "新增权限")
    @RequiresPermission("system:permission:add")
    @PostMapping
    public Result<PermissionVO> create(@Valid @RequestBody PermissionSaveDTO dto) {
        return Result.success("新增权限成功", permissionManageService.create(dto));
    }

    /**
     * 修改权限。
     * <p>HTTP：PUT /system/permissions/{id}。
     * 权限：{@code system:permission:edit}。
     *
     * @param id  权限项 ID
     * @param dto 权限项保存数据
     * @return 修改后的权限项
     */
    @Operation(summary = "修改权限")
    @RequiresPermission("system:permission:edit")
    @PutMapping("/{id}")
    public Result<PermissionVO> update(@PathVariable Long id, @Valid @RequestBody PermissionSaveDTO dto) {
        return Result.success("修改权限成功", permissionManageService.update(id, dto));
    }

    /**
     * 删除权限。
     * <p>HTTP：DELETE /system/permissions/{id}。
     * 权限：{@code system:permission:remove}。
     *
     * @param id 权限项 ID
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "删除权限")
    @RequiresPermission("system:permission:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        permissionManageService.delete(id);
        return Result.success("删除权限成功", null);
    }

}
