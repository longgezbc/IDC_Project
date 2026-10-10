package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.OperationLog;
import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.AssignRoleDTO;
import com.example.hbsz_idc_system_end.dto.ImportResultVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.ResetPasswordDTO;
import com.example.hbsz_idc_system_end.dto.UserManageVO;
import com.example.hbsz_idc_system_end.dto.UserQueryDTO;
import com.example.hbsz_idc_system_end.dto.UserSaveDTO;
import com.example.hbsz_idc_system_end.service.IUserManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 用户管理控制器（管理员视角）。
 * <p>
 * 业务模块：系统用户管理。
 * 基础请求路径：{@code /system/users}。
 * 提供用户 CRUD、密码重置、状态管理、角色分配等接口，关键写操作均记录操作日志。
 *
 * @author hbsz
 */
@Tag(name = "用户管理", description = "用户CRUD、密码重置、状态管理、角色分配")
@RestController
@RequestMapping("/system/users")
@Validated
public class UserManageController {

    private final IUserManageService userManageService;

    public UserManageController(IUserManageService userManageService) {
        this.userManageService = userManageService;
    }

    /**
     * 分页查询用户列表。
     * <p>HTTP：GET /system/users。
     * 权限：{@code system:user:query}。
     *
     * @param query 用户查询条件（含分页参数、筛选条件）
     * @return 用户分页结果
     */
    @Operation(summary = "分页查询用户列表")
    @RequiresPermission("system:user:query")
    @GetMapping
    public Result<PageResult<UserManageVO>> queryPage(UserQueryDTO query) {
        return Result.success(userManageService.queryPage(query));
    }

    /**
     * 查询所有启用用户（下拉选择用）。
     * <p>HTTP：GET /system/users/all。返回启用状态的全部用户，通常用于前端下拉选择。
     * 权限：{@code system:user:query}。
     *
     * @return 启用用户列表
     */
    @Operation(summary = "查询所有启用用户（下拉选择用）")
    @RequiresPermission("system:user:query")
    @GetMapping("/all")
    public Result<List<UserManageVO>> listAll() {
        return Result.success(userManageService.listAll());
    }

    /**
     * 查询用户详情。
     * <p>HTTP：GET /system/users/{id}。
     * 权限：{@code system:user:query}。
     *
     * @param id 用户 ID
     * @return 用户详情
     */
    @Operation(summary = "查询用户详情")
    @RequiresPermission("system:user:query")
    @GetMapping("/{id}")
    public Result<UserManageVO> getById(@PathVariable Long id) {
        return Result.success(userManageService.getById(id));
    }

    /**
     * 新增用户。
     * <p>HTTP：POST /system/users。请求体使用 {@code Create} 校验分组。
     * 权限：{@code system:user:add}；记录操作日志。
     *
     * @param dto 用户保存数据（新增分组校验）
     * @return 新增后的用户信息
     */
    @Operation(summary = "新增用户")
    @RequiresPermission("system:user:add")
    @OperationLog(module = "用户管理", type = "新增", description = "新增用户")
    @PostMapping
    public Result<UserManageVO> create(@Validated(UserSaveDTO.Create.class) @Valid @RequestBody UserSaveDTO dto) {
        return Result.success("新增用户成功", userManageService.create(dto));
    }

    /**
     * 修改用户。
     * <p>HTTP：PUT /system/users/{id}。请求体使用 {@code Update} 校验分组。
     * 权限：{@code system:user:edit}；记录操作日志。
     *
     * @param id  用户 ID
     * @param dto 用户保存数据（修改分组校验）
     * @return 修改后的用户信息
     */
    @Operation(summary = "修改用户")
    @RequiresPermission("system:user:edit")
    @OperationLog(module = "用户管理", type = "修改", description = "修改用户")
    @PutMapping("/{id}")
    public Result<UserManageVO> update(@PathVariable Long id, @Validated(UserSaveDTO.Update.class) @Valid @RequestBody UserSaveDTO dto) {
        return Result.success("修改用户成功", userManageService.update(id, dto));
    }

    /**
     * 删除用户。
     * <p>HTTP：DELETE /system/users/{id}。
     * 权限：{@code system:user:remove}；记录操作日志。
     *
     * @param id 用户 ID
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "删除用户")
    @RequiresPermission("system:user:remove")
    @OperationLog(module = "用户管理", type = "删除", description = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userManageService.delete(id);
        return Result.success("删除用户成功", null);
    }

    /**
     * 重置用户密码。
     * <p>HTTP：PUT /system/users/{id}/password。将指定用户的密码重置为新值。
     * 权限：{@code system:user:reset}；记录操作日志（不记录参数，避免密码泄露）。
     *
     * @param id  用户 ID
     * @param dto 重置密码数据（新密码等）
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "重置用户密码")
    @RequiresPermission("system:user:reset")
    @OperationLog(module = "用户管理", type = "重置密码", description = "重置用户密码", recordParams = false)
    @PutMapping("/{id}/password")
    // 启用 DTO 参数校验，防止非法输入
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordDTO dto) {
        userManageService.resetPassword(id, dto);
        return Result.success("密码重置成功", null);
    }

    /**
     * 修改用户状态（启用/禁用）。
     * <p>HTTP：PUT /system/users/{id}/status。
     * 权限：{@code system:user:status}；记录操作日志。
     *
     * @param id     用户 ID
     * @param status 目标状态（如 1=启用，0=禁用）
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "修改用户状态（启用/禁用）")
    @RequiresPermission("system:user:status")
    @OperationLog(module = "用户管理", type = "状态变更", description = "修改用户状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userManageService.updateStatus(id, status);
        return Result.success("状态修改成功", null);
    }

    /**
     * 分配用户角色。
     * <p>HTTP：PUT /system/users/{id}/roles。为指定用户重新配置其关联的角色集合。
     * 权限：{@code system:user:edit}；记录操作日志。
     *
     * @param id  用户 ID
     * @param dto 角色分配数据（含角色 ID 集合）
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "分配用户角色")
    @RequiresPermission("system:user:edit")
    @OperationLog(module = "用户管理", type = "分配角色", description = "分配用户角色")
    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRoleDTO dto) {
        userManageService.assignRoles(id, dto.getRoleIds());
        return Result.success("角色分配成功", null);
    }

    /**
     * 批量导入用户。
     * <p>HTTP：POST /system/users/import（multipart/form-data）。
     * 权限：{@code system:user:add}；记录操作日志。
     *
     * @param file Excel 文件
     * @return 导入结果汇总
     * @throws IOException 读取文件失败时抛出
     */
    @Operation(summary = "批量导入用户", description = "通过 Excel 批量导入用户")
    @RequiresPermission("system:user:add")
    @OperationLog(module = "用户管理", type = "导入", description = "批量导入用户")
    @PostMapping("/import")
    public Result<ImportResultVO> importUsers(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success("导入完成", userManageService.importUsers(file));
    }

    /**
     * 下载用户导入模板。
     * <p>HTTP：GET /system/users/import-template。
     * 权限：{@code system:user:query}。
     *
     * @param response HTTP 响应
     * @throws IOException 写出文件失败时抛出
     */
    @Operation(summary = "下载用户导入模板", description = "下载用户批量导入 Excel 模板")
    @RequiresPermission("system:user:query")
    @GetMapping("/import-template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        byte[] content = userManageService.downloadImportTemplate();
        String fileName = URLEncoder.encode("user_import_template.xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        response.getOutputStream().write(content);
    }

}
