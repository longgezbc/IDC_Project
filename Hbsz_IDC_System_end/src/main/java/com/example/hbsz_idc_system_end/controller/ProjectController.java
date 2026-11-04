package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.Idempotent;
import com.example.hbsz_idc_system_end.common.annotation.OperationLog;
import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.ImportResultVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.ProjectQueryDTO;
import com.example.hbsz_idc_system_end.dto.ProjectSaveDTO;
import com.example.hbsz_idc_system_end.dto.ProjectStatisticsVO;
import com.example.hbsz_idc_system_end.dto.ProjectVO;
import com.example.hbsz_idc_system_end.service.IBatchOperationService;
import com.example.hbsz_idc_system_end.service.IDocumentService;
import com.example.hbsz_idc_system_end.service.IProjectService;
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
import java.util.Map;

/**
 * 项目管理控制器。
 * <p>
 * 业务模块：项目全生命周期管理。
 * 基础请求路径：{@code /projects}。
 * 提供项目的增删改查、归档/取消归档、统计概览及项目关联文档查询等接口，
 * 关键写操作记录操作日志，新建接口通过幂等控制防止重复提交。
 *
 * @author hbsz
 */
@Tag(name = "项目管理", description = "项目CRUD、归档、统计等接口")
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final IProjectService projectService;
    private final IDocumentService documentService;
    private final IBatchOperationService batchOperationService;

    public ProjectController(IProjectService projectService, IDocumentService documentService,
                              IBatchOperationService batchOperationService) {
        this.projectService = projectService;
        this.documentService = documentService;
        this.batchOperationService = batchOperationService;
    }

    /**
     * 项目列表查询。
     * <p>HTTP：GET /projects。分页查询项目列表，支持多条件筛选。
     * 权限：{@code project:query}。
     *
     * @param query 项目查询条件（含分页参数、筛选条件）
     * @return 项目分页结果
     */
    @Operation(summary = "项目列表查询", description = "分页查询项目列表，支持多条件筛选")
    @RequiresPermission("project:query")
    @GetMapping
    public Result<PageResult<ProjectVO>> list(@Valid ProjectQueryDTO query) {
        PageResult<ProjectVO> page = projectService.queryPage(query);
        return Result.success(page);
    }

    /**
     * 项目详情。
     * <p>HTTP：GET /projects/{id}。
     * 权限：{@code project:query}。
     *
     * @param id 项目 ID
     * @return 项目详情
     */
    @Operation(summary = "项目详情", description = "根据ID查询项目详情")
    @RequiresPermission("project:query")
    @GetMapping("/{id}")
    public Result<ProjectVO> detail(@Parameter(description = "项目ID", required = true) @PathVariable Long id) {
        return Result.success(projectService.getDetail(id));
    }

    /**
     * 新建项目。
     * <p>HTTP：POST /projects。创建新项目。
     * 权限：{@code project:add}；记录操作日志；通过 {@code @Idempotent} 幂等控制（300 秒内防重复提交）。
     *
     * @param dto 项目保存数据（经参数校验）
     * @return 新建后的项目
     */
    @Operation(summary = "新建项目", description = "创建新项目")
    @RequiresPermission("project:add")
    @OperationLog(module = "项目管理", type = "新增", description = "新建项目")
    @Idempotent(key = "project:create", expire = 300)
    @PostMapping
    public Result<ProjectVO> create(@Valid @RequestBody ProjectSaveDTO dto) {
        return Result.success("项目创建成功", projectService.create(dto));
    }

    /**
     * 更新项目。
     * <p>HTTP：PUT /projects/{id}。
     * 权限：{@code project:edit}；记录操作日志。
     *
     * @param id  项目 ID
     * @param dto 项目保存数据
     * @return 更新后的项目
     */
    @Operation(summary = "更新项目", description = "更新项目信息")
    @RequiresPermission("project:edit")
    @OperationLog(module = "项目管理", type = "修改", description = "更新项目")
    @PutMapping("/{id}")
    // 启用 DTO 参数校验，防止非法输入
    public Result<ProjectVO> update(@PathVariable Long id, @Valid @RequestBody ProjectSaveDTO dto) {
        return Result.success("项目更新成功", projectService.update(id, dto));
    }

    /**
     * 删除项目。
     * <p>HTTP：DELETE /projects/{id}。
     * 权限：{@code project:remove}；记录操作日志。
     *
     * @param id 项目 ID
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "删除项目", description = "根据ID删除项目")
    @RequiresPermission("project:remove")
    @OperationLog(module = "项目管理", type = "删除", description = "删除项目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.success("项目删除成功", null);
    }

    /**
     * 归档项目。
     * <p>HTTP：PUT /projects/{id}/archive。将项目标记为已归档。
     * 权限：{@code project:archive}；记录操作日志。
     *
     * @param id 项目 ID
     * @return 归档后的项目
     */
    @Operation(summary = "归档项目", description = "将项目标记为已归档")
    @RequiresPermission("project:archive")
    @OperationLog(module = "项目管理", type = "归档", description = "归档项目")
    @PutMapping("/{id}/archive")
    public Result<ProjectVO> archive(@PathVariable Long id) {
        return Result.success("项目已归档", projectService.archive(id));
    }

    /**
     * 取消归档。
     * <p>HTTP：PUT /projects/{id}/unarchive。取消项目的归档状态。
     * 权限：{@code project:archive}；记录操作日志。
     *
     * @param id 项目 ID
     * @return 取消归档后的项目
     */
    @Operation(summary = "取消归档", description = "取消项目归档状态")
    @RequiresPermission("project:archive")
    @OperationLog(module = "项目管理", type = "取消归档", description = "取消归档")
    @PutMapping("/{id}/unarchive")
    public Result<ProjectVO> unarchive(@PathVariable Long id) {
        return Result.success("已取消归档", projectService.unarchive(id));
    }

    /**
     * 批量归档项目。
     * <p>HTTP：PUT /projects/batch/archive。
     * 权限：{@code project:archive}；记录操作日志。
     * 全部在单一事务内执行，任一失败整体回滚。
     *
     * @param ids 项目ID列表
     * @return 成功归档数量
     */
    @Operation(summary = "批量归档项目", description = "批量归档项目，事务保证原子性")
    @RequiresPermission("project:archive")
    @OperationLog(module = "项目管理", type = "批量归档", description = "批量归档项目")
    @PutMapping("/batch/archive")
    public Result<Integer> batchArchive(@RequestBody List<Long> ids) {
        int count = batchOperationService.batchArchiveProjects(ids);
        return Result.success("批量归档完成，共 " + count + " 个项目", count);
    }

    /**
     * 批量删除项目。
     * <p>HTTP：DELETE /projects/batch。
     * 权限：{@code project:remove}；记录操作日志。
     * 全部在单一事务内执行，任一失败整体回滚。
     *
     * @param ids 项目ID列表
     * @return 成功删除数量
     */
    @Operation(summary = "批量删除项目", description = "批量删除项目，事务保证原子性")
    @RequiresPermission("project:remove")
    @OperationLog(module = "项目管理", type = "批量删除", description = "批量删除项目")
    @DeleteMapping("/batch")
    public Result<Integer> batchDelete(@RequestBody List<Long> ids) {
        int count = batchOperationService.batchDeleteProjects(ids);
        return Result.success("批量删除完成，共 " + count + " 个项目", count);
    }

    /**
     * 项目统计概览。
     * <p>HTTP：GET /projects/statistics。获取工作台首页的项目统计数据。
     * 权限：{@code project:query}。
     *
     * @return 项目统计概览数据
     */
    @Operation(summary = "项目统计概览", description = "获取工作台首页项目统计数据")
    @RequiresPermission("project:query")
    @GetMapping("/statistics")
    public Result<ProjectStatisticsVO> statistics() {
        return Result.success(projectService.getStatistics());
    }

    /**
     * 按项目查询文档。
     * <p>HTTP：GET /projects/{projectId}/documents。根据项目 ID 查询其关联文档列表。
     * 权限：{@code document:query}。
     *
     * @param projectId 项目 ID
     * @return 该项目下的文档列表
     */
    @Operation(summary = "按项目查询文档", description = "根据项目ID查询文档列表")
    @RequiresPermission("document:query")
    @GetMapping("/{projectId}/documents")
    public Result<List<com.example.hbsz_idc_system_end.dto.DocumentVO>> documentsByProject(
            @Parameter(description = "项目ID", required = true) @PathVariable Long projectId) {
        return Result.success(documentService.listByProject(projectId));
    }

    /**
     * 批量导入项目。
     * <p>HTTP：POST /projects/import（multipart/form-data）。
     * 权限：{@code project:add}；记录操作日志。
     *
     * @param file Excel 文件
     * @return 导入结果汇总
     * @throws IOException 读取文件失败时抛出
     */
    @Operation(summary = "批量导入项目", description = "通过 Excel 批量导入项目")
    @RequiresPermission("project:add")
    @OperationLog(module = "项目管理", type = "导入", description = "批量导入项目")
    @PostMapping("/import")
    public Result<ImportResultVO> importProjects(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success("导入完成", projectService.importProjects(file));
    }

    /**
     * 下载项目导入模板。
     * <p>HTTP：GET /projects/import-template。
     * 权限：{@code project:query}。
     *
     * @param response HTTP 响应
     * @throws IOException 写出文件失败时抛出
     */
    @Operation(summary = "下载项目导入模板", description = "下载项目批量导入 Excel 模板")
    @RequiresPermission("project:query")
    @GetMapping("/import-template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        byte[] content = projectService.downloadImportTemplate();
        String fileName = URLEncoder.encode("project_import_template.xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        response.getOutputStream().write(content);
    }

}
