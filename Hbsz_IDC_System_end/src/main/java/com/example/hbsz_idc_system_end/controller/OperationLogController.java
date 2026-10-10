package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.OperationLogQueryDTO;
import com.example.hbsz_idc_system_end.dto.OperationLogVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.service.IOperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 操作日志查询控制器。
 * <p>
 * 业务模块：系统审计（操作日志检索）。
 * 基础请求路径：{@code /system/logs}。
 * 日志数据由 OperationLogAspect 在写操作时自动落库，本控制器仅提供只读检索，
 * 点亮"只写不查"的沉没审计数据。查询接口不标注 @OperationLog（读操作不审计，
 * 避免日志表自膨胀）。
 * 权限：{@code system:log:query}（审计数据敏感，默认仅管理员）。
 *
 * @author hbsz
 */
@Tag(name = "操作日志", description = "操作日志分页检索（审计）")
@RestController
@RequestMapping("/system/logs")
public class OperationLogController {

    private final IOperationLogService operationLogService;

    public OperationLogController(IOperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 分页查询操作日志。
     * <p>HTTP：GET /system/logs。
     * 权限：{@code system:log:query}。
     *
     * @param query 查询条件（操作人/模块/业务类型/状态/时间范围 + 分页）
     * @return 操作日志分页结果（按操作时间倒序）
     */
    @Operation(summary = "分页查询操作日志", description = "按操作人/模块/业务类型/状态/时间范围筛选，时间倒序")
    @RequiresPermission("system:log:query")
    @GetMapping
    public Result<PageResult<OperationLogVO>> queryPage(OperationLogQueryDTO query) {
        return Result.success(operationLogService.queryPage(query));
    }

    /**
     * 查询筛选下拉选项。
     * <p>HTTP：GET /system/logs/filters。返回去重后的模块与业务类型列表，供前端筛选区下拉框使用。
     * 权限：{@code system:log:query}。
     *
     * @return { modules: [...], businessTypes: [...] }
     */
    @Operation(summary = "查询筛选选项", description = "去重后的操作模块与业务类型列表（下拉选择用）")
    @RequiresPermission("system:log:query")
    @GetMapping("/filters")
    public Result<Map<String, List<String>>> listFilterOptions() {
        return Result.success(operationLogService.listFilterOptions());
    }
}
