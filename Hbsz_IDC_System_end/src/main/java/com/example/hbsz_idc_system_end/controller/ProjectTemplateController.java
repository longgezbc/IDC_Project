package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.OperationLog;
import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.BizProjectTemplate;
import com.example.hbsz_idc_system_end.service.IProjectTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目模板控制器。提供模板的增删改查与启用列表。
 * @author hbsz
 */
@Tag(name = "项目模板", description = "项目模板CRUD与管理")
@RestController
@RequestMapping("/project-templates")
public class ProjectTemplateController {

    private final IProjectTemplateService templateService;

    public ProjectTemplateController(IProjectTemplateService templateService) {
        this.templateService = templateService;
    }

    @Operation(summary = "模板分页查询")
    @RequiresPermission("project:query")
    @GetMapping
    public Result<PageResult<BizProjectTemplate>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return Result.success(templateService.queryPage(pageNum, pageSize, keyword, category));
    }

    @Operation(summary = "启用模板列表（下拉选择用）")
    @RequiresPermission("project:query")
    @GetMapping("/enabled")
    public Result<List<BizProjectTemplate>> enabled() {
        return Result.success(templateService.listEnabled());
    }

    @Operation(summary = "模板详情")
    @RequiresPermission("project:query")
    @GetMapping("/{id}")
    public Result<BizProjectTemplate> detail(@PathVariable Long id) {
        return Result.success(templateService.getTemplateDetail(id));
    }

    @Operation(summary = "新建模板")
    @RequiresPermission("project:add")
    @OperationLog(module = "项目模板", type = "新增", description = "新建项目模板")
    @PostMapping
    public Result<BizProjectTemplate> create(@Valid @RequestBody BizProjectTemplate template) {
        return Result.success("模板创建成功", templateService.createTemplate(template));
    }

    @Operation(summary = "更新模板")
    @RequiresPermission("project:edit")
    @OperationLog(module = "项目模板", type = "修改", description = "更新项目模板")
    @PutMapping("/{id}")
    public Result<BizProjectTemplate> update(@PathVariable Long id, @Valid @RequestBody BizProjectTemplate template) {
        return Result.success("模板更新成功", templateService.updateTemplate(id, template));
    }

    @Operation(summary = "删除模板")
    @RequiresPermission("project:remove")
    @OperationLog(module = "项目模板", type = "删除", description = "删除项目模板")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.removeById(id);
        return Result.success("模板删除成功", null);
    }
}
