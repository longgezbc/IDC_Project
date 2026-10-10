package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.AssignPermissionDTO;
import com.example.hbsz_idc_system_end.dto.PageQuery;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.RoleSaveDTO;
import com.example.hbsz_idc_system_end.dto.RoleVO;
import com.example.hbsz_idc_system_end.service.IRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理控制器。
 * <p>
 * 业务模块：系统角色管理（RBAC 角色维护）。
 * 基础请求路径：{@code /system/roles}。
 * 提供角色定义、角色分配、权限配置等接口，涵盖角色增删改查及角色-权限关联维护。
 *
 * @author hbsz
 */
@Tag(name = "角色管理", description = "角色定义、权限配置")
@RestController
@RequestMapping("/system/roles")
public class RoleController {

    private final IRoleService roleService;

    public RoleController(IRoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * 分页查询角色列表。
     * <p>HTTP：GET /system/roles。
     * 权限：{@code system:role:query}。
     *
     * @param query 分页查询条件
     * @return 角色分页结果
     */
    @Operation(summary = "分页查询角色列表")
    @RequiresPermission("system:role:query")
    @GetMapping
    public Result<PageResult<RoleVO>> queryPage(PageQuery query) {
        return Result.success(roleService.queryPage(query));
    }

    /**
     * 查询所有启用角色（下拉选择用）。
     * <p>HTTP：GET /system/roles/all。返回启用状态的全部角色，通常用于前端下拉选择。
     * 权限：{@code system:role:query}。
     *
     * @return 启用角色列表
     */
    @Operation(summary = "查询所有启用角色（下拉选择用）")
    @RequiresPermission("system:role:query")
    @GetMapping("/all")
    public Result<List<RoleVO>> listAll() {
        return Result.success(roleService.listAll());
    }

    /**
     * 查询角色详情。
     * <p>HTTP：GET /system/roles/{id}。
     * 权限：{@code system:role:query}。
     *
     * @param id 角色 ID
     * @return 角色详情
     */
    @Operation(summary = "查询角色详情")
    @RequiresPermission("system:role:query")
    @GetMapping("/{id}")
    public Result<RoleVO> getById(@PathVariable Long id) {
        return Result.success(roleService.getById(id));
    }

    /**
     * 新增角色。
     * <p>HTTP：POST /system/roles。
     * 权限：{@code system:role:add}。
     *
     * @param dto 角色保存数据
     * @return 新增后的角色
     */
    @Operation(summary = "新增角色")
    @RequiresPermission("system:role:add")
    @PostMapping
    public Result<RoleVO> create(@Valid @RequestBody RoleSaveDTO dto) {
        return Result.success("新增角色成功", roleService.create(dto));
    }

    /**
     * 修改角色。
     * <p>HTTP：PUT /system/roles/{id}。
     * 权限：{@code system:role:edit}。
     *
     * @param id  角色 ID
     * @param dto 角色保存数据
     * @return 修改后的角色
     */
    @Operation(summary = "修改角色")
    @RequiresPermission("system:role:edit")
    @PutMapping("/{id}")
    public Result<RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleSaveDTO dto) {
        return Result.success("修改角色成功", roleService.update(id, dto));
    }

    /**
     * 删除角色。
     * <p>HTTP：DELETE /system/roles/{id}。
     * 权限：{@code system:role:remove}。
     *
     * @param id 角色 ID
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "删除角色")
    @RequiresPermission("system:role:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success("删除角色成功", null);
    }

    /**
     * 分配角色权限。
     * <p>HTTP：PUT /system/roles/{id}/permissions。为指定角色重新配置其拥有的权限集合。
     * 权限：{@code system:role:permission}。
     *
     * @param id  角色 ID
     * @param dto 权限分配数据（含权限 ID 集合）
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "分配角色权限")
    @RequiresPermission("system:role:permission")
    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id, @Valid @RequestBody AssignPermissionDTO dto) {
        roleService.assignPermissions(id, dto);
        return Result.success("权限分配成功", null);
    }

}
