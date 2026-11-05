package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.OperationLog;
import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.ImportResultVO;
import com.example.hbsz_idc_system_end.dto.MaterialFlowVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.BizMaterial;
import com.example.hbsz_idc_system_end.entity.BizMaterialAlert;
import com.example.hbsz_idc_system_end.entity.BizMaterialTransaction;
import com.example.hbsz_idc_system_end.service.IMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 材料库存管理控制器。
 * <p>
 * 提供材料 CRUD、入库/出库/调整、变更流水查询、库存预警查询与处理。
 * @author hbsz
 */
@Tag(name = "材料库存", description = "材料入库/出库/库存/预警管理")
@RestController
@RequestMapping("/materials")
public class MaterialController {

    private final IMaterialService materialService;

    public MaterialController(IMaterialService materialService) {
        this.materialService = materialService;
    }

    // ===== 材料 CRUD =====

    @Operation(summary = "材料分页查询")
    @RequiresPermission("material:query")
    @GetMapping
    public Result<PageResult<BizMaterial>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return Result.success(materialService.queryPage(pageNum, pageSize, keyword, category));
    }

    @Operation(summary = "材料详情")
    @RequiresPermission("material:query")
    @GetMapping("/{id}")
    public Result<BizMaterial> detail(@PathVariable Long id) {
        return Result.success(materialService.getById(id));
    }

    @Operation(summary = "新建材料")
    @RequiresPermission("material:add")
    @OperationLog(module = "材料库存", type = "新增", description = "新建材料")
    @PostMapping
    public Result<BizMaterial> create(@Valid @RequestBody BizMaterial material) {
        if (material.getStatus() == null) material.setStatus(1);
        if (material.getCurrentStock() == null) material.setCurrentStock(BigDecimal.ZERO);
        materialService.save(material);
        return Result.success("材料创建成功", material);
    }

    @Operation(summary = "更新材料")
    @RequiresPermission("material:edit")
    @OperationLog(module = "材料库存", type = "修改", description = "更新材料")
    @PutMapping("/{id}")
    public Result<BizMaterial> update(@PathVariable Long id, @Valid @RequestBody BizMaterial material) {
        material.setId(id);
        materialService.updateById(material);
        return Result.success("更新成功", material);
    }

    @Operation(summary = "删除材料")
    @RequiresPermission("material:remove")
    @OperationLog(module = "材料库存", type = "删除", description = "删除材料")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        materialService.removeById(id);
        return Result.success("删除成功", null);
    }

    // ===== 批量导入 =====

    @Operation(summary = "批量导入材料", description = "按出库物料导入模板（标准型号/物料类型/厂商/当前状态/数量）导入，同型号自动合并累加")
    @RequiresPermission("material:add")
    @OperationLog(module = "材料库存", type = "导入", description = "批量导入材料")
    @PostMapping("/import")
    public Result<ImportResultVO> importMaterials(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success("导入完成", materialService.importMaterials(file));
    }

    @Operation(summary = "下载材料导入模板")
    @RequiresPermission("material:query")
    @GetMapping("/import-template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        byte[] content = materialService.downloadImportTemplate();
        String fileName = URLEncoder.encode("material_import_template.xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        response.getOutputStream().write(content);
    }

    // ===== 入库 / 出库 / 调整 =====

    @Operation(summary = "入库")
    @RequiresPermission("material:inbound")
    @OperationLog(module = "材料库存", type = "入库", description = "材料入库")
    @PostMapping("/{id}/stock-in")
    public Result<Void> stockIn(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal quantity = new BigDecimal(body.get("quantity").toString());
        String remark = body.getOrDefault("remark", "").toString();
        materialService.stockIn(id, quantity, remark);
        return Result.success("入库成功", null);
    }

    @Operation(summary = "出库")
    @RequiresPermission("material:outbound")
    @OperationLog(module = "材料库存", type = "出库", description = "材料出库")
    @PostMapping("/{id}/stock-out")
    public Result<Void> stockOut(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal quantity = new BigDecimal(body.get("quantity").toString());
        Long projectId = body.get("projectId") != null ? Long.valueOf(body.get("projectId").toString()) : null;
        String remark = body.getOrDefault("remark", "").toString();
        materialService.stockOut(id, quantity, projectId, remark);
        return Result.success("出库成功", null);
    }

    @Operation(summary = "库存调整")
    @RequiresPermission("material:adjust")
    @OperationLog(module = "材料库存", type = "调整", description = "库存调整")
    @PostMapping("/{id}/adjust")
    public Result<Void> adjust(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal newStock = new BigDecimal(body.get("newStock").toString());
        String remark = body.getOrDefault("remark", "").toString();
        materialService.adjustStock(id, newStock, remark);
        return Result.success("调整成功", null);
    }

    // ===== 变更流水 =====

    @Operation(summary = "材料变更流水")
    @RequiresPermission("material:query")
    @GetMapping("/{id}/transactions")
    public Result<PageResult<BizMaterialTransaction>> transactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(materialService.listTransactions(id, pageNum, pageSize));
    }

    @Operation(summary = "项目维度材料出库流水", description = "按去向项目查材料领用记录，用于项目详情页追溯材料去向")
    @RequiresPermission("project:query")
    @GetMapping("/project-transactions")
    public Result<PageResult<MaterialFlowVO>> projectTransactions(
            @RequestParam Long projectId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(materialService.listProjectOutbound(projectId, pageNum, pageSize));
    }

    // ===== 预警 =====

    @Operation(summary = "待处理预警列表")
    @RequiresPermission("material:query")
    @GetMapping("/alerts")
    public Result<List<BizMaterialAlert>> alerts() {
        return Result.success(materialService.listPendingAlerts());
    }

    @Operation(summary = "处理预警")
    @RequiresPermission("material:adjust")
    @OperationLog(module = "材料库存", type = "预警处理", description = "处理库存预警")
    @PutMapping("/alerts/{alertId}/resolve")
    public Result<Void> resolveAlert(@PathVariable Long alertId) {
        materialService.resolveAlert(alertId);
        return Result.success("预警已处理", null);
    }
}
