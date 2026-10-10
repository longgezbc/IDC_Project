package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.Idempotent;
import com.example.hbsz_idc_system_end.common.annotation.OperationLog;
import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.CustomerQueryDTO;
import com.example.hbsz_idc_system_end.dto.CustomerSaveDTO;
import com.example.hbsz_idc_system_end.dto.CustomerVO;
import com.example.hbsz_idc_system_end.dto.ImportResultVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.service.ICustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 客户管理控制器。
 * <p>
 * 业务模块：客户档案信息管理。
 * 基础请求路径：{@code /customers}。
 * 提供客户的增删改查、状态切换及下拉列表等接口，关键写操作记录操作日志，
 * 新建接口通过幂等控制防止重复提交。
 *
 * @author hbsz
 */
@Tag(name = "客户管理", description = "客户CRUD、状态切换、下拉列表等接口")
@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final ICustomerService customerService;

    public CustomerController(ICustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * 客户列表查询。
     * <p>HTTP：GET /customers。分页查询客户列表，支持关键字与状态筛选。
     * 权限：{@code customer:query}。
     *
     * @param query 客户查询条件（含分页参数、筛选条件）
     * @return 客户分页结果（每条含关联项目数）
     */
    @Operation(summary = "客户列表查询", description = "分页查询客户列表，支持关键字与状态筛选")
    @RequiresPermission("customer:query")
    @GetMapping
    public Result<PageResult<CustomerVO>> list(CustomerQueryDTO query) {
        return Result.success(customerService.queryPage(query));
    }

    /**
     * 查询所有启用客户（下拉用，不分页）。
     * <p>HTTP：GET /customers/all。返回 status=1 的全部客户，按名称升序。
     * 权限：{@code customer:query}。
     *
     * @return 启用客户列表
     */
    @Operation(summary = "启用客户列表", description = "查询所有启用状态的客户（下拉用，不分页）")
    @RequiresPermission("customer:query")
    @GetMapping("/all")
    public Result<List<CustomerVO>> listAll() {
        return Result.success(customerService.listAllEnabled());
    }

    /**
     * 客户详情。
     * <p>HTTP：GET /customers/{id}。
     * 权限：{@code customer:query}。
     *
     * @param id 客户 ID
     * @return 客户详情（含关联项目数）
     */
    @Operation(summary = "客户详情", description = "根据ID查询客户详情")
    @RequiresPermission("customer:query")
    @GetMapping("/{id}")
    public Result<CustomerVO> detail(@Parameter(description = "客户ID", required = true) @PathVariable Long id) {
        return Result.success(customerService.getDetail(id));
    }

    /**
     * 新建客户。
     * <p>HTTP：POST /customers。创建新客户，客户编号唯一性由 Service 校验。
     * 权限：{@code customer:add}；记录操作日志；通过 {@code @Idempotent} 幂等控制（300 秒内防重复提交）。
     *
     * @param dto 客户保存数据（经参数校验）
     * @return 新建后的客户
     */
    @Operation(summary = "新建客户", description = "创建新客户")
    @RequiresPermission("customer:add")
    @OperationLog(module = "客户管理", type = "新增", description = "新建客户")
    @Idempotent(key = "customer:create", expire = 300)
    @PostMapping
    public Result<CustomerVO> create(@Valid @RequestBody CustomerSaveDTO dto) {
        return Result.success("客户创建成功", customerService.create(dto));
    }

    /**
     * 更新客户。
     * <p>HTTP：PUT /customers/{id}。
     * 权限：{@code customer:edit}；记录操作日志。
     *
     * @param id  客户 ID
     * @param dto 客户保存数据
     * @return 更新后的客户
     */
    @Operation(summary = "更新客户", description = "更新客户信息")
    @RequiresPermission("customer:edit")
    @OperationLog(module = "客户管理", type = "修改", description = "更新客户")
    @PutMapping("/{id}")
    public Result<CustomerVO> update(@PathVariable Long id, @Valid @RequestBody CustomerSaveDTO dto) {
        return Result.success("客户更新成功", customerService.update(id, dto));
    }

    /**
     * 删除客户。
     * <p>HTTP：DELETE /customers/{id}。若存在关联项目则禁止删除。
     * 权限：{@code customer:remove}；记录操作日志。
     *
     * @param id 客户 ID
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "删除客户", description = "根据ID删除客户（有关联项目时禁止删除）")
    @RequiresPermission("customer:remove")
    @OperationLog(module = "客户管理", type = "删除", description = "删除客户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return Result.success("客户删除成功", null);
    }

    /**
     * 批量导入客户。
     * <p>HTTP：POST /customers/import（multipart/form-data）。
     * 权限：{@code customer:add}；记录操作日志。
     *
     * @param file Excel 文件
     * @return 导入结果汇总
     * @throws IOException 读取文件失败时抛出
     */
    @Operation(summary = "批量导入客户", description = "通过 Excel 批量导入客户")
    @RequiresPermission("customer:add")
    @OperationLog(module = "客户管理", type = "导入", description = "批量导入客户")
    @PostMapping("/import")
    public Result<ImportResultVO> importCustomers(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success("导入完成", customerService.importCustomers(file));
    }

    /**
     * 下载客户导入模板。
     * <p>HTTP：GET /customers/import-template。
     * 权限：{@code customer:query}。
     *
     * @param response HTTP 响应
     * @throws IOException 写出文件失败时抛出
     */
    @Operation(summary = "下载客户导入模板", description = "下载客户批量导入 Excel 模板")
    @RequiresPermission("customer:query")
    @GetMapping("/import-template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        byte[] content = customerService.downloadImportTemplate();
        String fileName = URLEncoder.encode("customer_import_template.xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        response.getOutputStream().write(content);
    }

}
