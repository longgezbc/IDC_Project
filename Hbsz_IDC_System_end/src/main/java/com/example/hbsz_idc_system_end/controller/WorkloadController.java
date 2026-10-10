package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.ExportRecordVO;
import com.example.hbsz_idc_system_end.dto.WorkloadItemSaveDTO;
import com.example.hbsz_idc_system_end.dto.WorkloadRecordBatchDTO;
import com.example.hbsz_idc_system_end.dto.WorkloadReviewDTO;
import com.example.hbsz_idc_system_end.service.IWorkloadService;
import com.example.hbsz_idc_system_end.utils.UserContext;
import com.example.hbsz_idc_system_end.vo.WorkloadItemVO;
import com.example.hbsz_idc_system_end.vo.WorkloadMatrixVO;
import com.example.hbsz_idc_system_end.vo.WorkloadMyReportVO;
import com.example.hbsz_idc_system_end.vo.WorkloadRecordVO;
import com.example.hbsz_idc_system_end.vo.WorkloadWorkerPerformanceVO;
import com.example.hbsz_idc_system_end.vo.WorkloadWorkerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 工作量统计控制器。
 * <p>
 * 业务模块：工作量统计（依据《工作量统计模板.xlsx》设计）。
 * 提供项目工作量清单维护（含标准模板一键导入）、项目组授权成员每日填报（待审核）、
 * 项目经理审核工作流（批量通过/驳回）、统计矩阵（清单行×人员列）与人员绩效汇总，
 * 以及模板样式 Excel 导出（仅已审核数据）。
 * <p>
 * 权限：查看 workload:query；填报 workload:report；清单维护 workload:edit；
 * 导出 workload:export；审核 workload:review（项目负责人/管理员）。
 *
 * @author hbsz
 */
@Tag(name = "工作量统计", description = "工作量清单、每日填报、统计矩阵与绩效导出")
@RestController
@RequestMapping("/workload")
public class WorkloadController {

    private final IWorkloadService workloadService;

    public WorkloadController(IWorkloadService workloadService) {
        this.workloadService = workloadService;
    }

    /**
     * 项目工作量清单（含实时聚合统计）。
     * <p>HTTP：GET /workload/items。
     *
     * @param projectId      项目ID
     * @param statisticsDate 当日统计日期（默认今天，决定"今日工作量"列归属日）
     * @return 清单列表（每项含已完成/剩余/今日量）
     */
    @Operation(summary = "工作量清单", description = "查询项目工作量清单（含已完成/剩余/今日量实时聚合）")
    @RequiresPermission("workload:query")
    @GetMapping("/items")
    public Result<List<WorkloadItemVO>> listItems(
            @Parameter(description = "项目ID") @RequestParam Long projectId,
            @Parameter(description = "统计日期，默认今天（今日完成量归属日）")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statisticsDate) {
        return Result.success(workloadService.listItems(projectId,
                statisticsDate != null ? statisticsDate : LocalDate.now()));
    }

    /**
     * 新增清单项。
     * <p>HTTP：POST /workload/items。
     *
     * @param dto 清单项数据
     * @return 新增后的清单项
     */
    @Operation(summary = "新增清单项", description = "为项目新增工作量清单项（作业类型/作业范围/额定/单位等）")
    @RequiresPermission("workload:edit")
    @PostMapping("/items")
    public Result<WorkloadItemVO> createItem(@Valid @RequestBody WorkloadItemSaveDTO dto) {
        return Result.success("清单项新增成功", workloadService.createItem(dto));
    }

    /**
     * 更新清单项。
     * <p>HTTP：PUT /workload/items/{id}。
     *
     * @param id  清单项ID
     * @param dto 清单项数据
     * @return 更新后的清单项
     */
    @Operation(summary = "更新清单项", description = "更新工作量清单项信息")
    @RequiresPermission("workload:edit")
    @PutMapping("/items/{id}")
    public Result<WorkloadItemVO> updateItem(@PathVariable Long id, @Valid @RequestBody WorkloadItemSaveDTO dto) {
        return Result.success("清单项更新成功", workloadService.updateItem(id, dto));
    }

    /**
     * 删除清单项（级联删除其填报明细）。
     * <p>HTTP：DELETE /workload/items/{id}。
     *
     * @param id 清单项ID
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "删除清单项", description = "删除清单项及其全部填报明细")
    @RequiresPermission("workload:edit")
    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        workloadService.deleteItem(id);
        return Result.success("清单项删除成功", null);
    }

    /**
     * 导入标准模板清单（106 项 / 15 类作业目录）。
     * <p>HTTP：POST /workload/items/import-template。
     *
     * @param projectId 项目ID
     * @return 导入的清单项数量
     */
    @Operation(summary = "导入标准模板清单", description = "从内置标准模板（106项/15类）导入工作量清单；项目已有清单时拒绝")
    @RequiresPermission("workload:edit")
    @PostMapping("/items/import-template")
    public Result<Integer> importTemplate(@Parameter(description = "项目ID") @RequestParam Long projectId) {
        int count = workloadService.importTemplate(projectId);
        return Result.success("已导入 " + count + " 项标准清单", count);
    }

    /**
     * 项目可填报人员列表（项目组在组成员，组长置前）。
     * <p>HTTP：GET /workload/workers。
     * 填报人员只能从项目组授权成员中选择（业务硬约束）。
     *
     * @param projectId 项目ID
     * @return 成员列表（userId/姓名/组内角色）
     */
    @Operation(summary = "可填报人员列表", description = "项目组在组成员列表（组长置前），工作量填报人员只能从中选择")
    @RequiresPermission("workload:query")
    @GetMapping("/workers")
    public Result<List<WorkloadWorkerVO>> listWorkers(
            @Parameter(description = "项目ID") @RequestParam Long projectId) {
        return Result.success(workloadService.listWorkers(projectId));
    }

    /**
     * 我的快速填报上下文（"快速填报"卡片流一次性数据源）。
     * <p>HTTP：GET /workload/my/report-context。
     * 返回：我参与的项目组、今日已有填报（含审核状态）、昨日数量（供"复制昨天"）、
     * 最近 14 天常用清单项（按填报频次降序）。projectId 为空时仅返回项目列表。
     * 保存路径复用 POST /workload/records（审核链路/成员校验单点不变）。
     *
     * @param projectId 项目ID（可空 = 仅查项目列表，前端自动选择后二次请求）
     * @param date      填报日期（默认今天）
     * @return 快速填报上下文
     */
    @Operation(summary = "我的快速填报上下文", description = "常用清单项卡片 + 今日已有填报 + 昨日数量，供快速填报卡片流一次性加载")
    @RequiresPermission("workload:report")
    @GetMapping("/my/report-context")
    public Result<WorkloadMyReportVO> getMyReportContext(
            @Parameter(description = "项目ID，不传则仅返回我参与的项目列表") @RequestParam(required = false) Long projectId,
            @Parameter(description = "填报日期，默认今天") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(workloadService.getMyReportContext(projectId, date));
    }

    /**
     * 某日统计矩阵：清单行 × 人员列 + 每人当日绩效。
     * <p>HTTP：GET /workload/matrix。
     * 矩阵单元格含审核状态（approved 锁定 / pending 待审 / rejected 驳回）；
     * 统计列与绩效仅计算已审核通过的记录。
     *
     * @param projectId 项目ID
     * @param date      统计日期（默认今天）
     * @return 统计矩阵
     */
    @Operation(summary = "统计矩阵", description = "某日统计矩阵：清单行（含聚合）× 人员列 + 每人当日绩效；单元格含审核状态")
    @RequiresPermission("workload:query")
    @GetMapping("/matrix")
    public Result<WorkloadMatrixVO> getMatrix(
            @Parameter(description = "项目ID") @RequestParam Long projectId,
            @Parameter(description = "统计日期，默认今天") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(workloadService.getMatrix(projectId, date != null ? date : LocalDate.now()));
    }

    /**
     * 批量保存某日填报明细（填报人员必须是项目组在组成员）。
     * <p>HTTP：POST /workload/records。
     * 保存后进入待审核状态；同项+同日+同人重复提交覆盖并重置为待审核；
     * 已审核通过的记录不可直接修改。
     *
     * @param dto 批量填报数据
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "批量保存填报", description = "批量保存某日工作量填报（项目组成员×清单项×数量），保存后进入待审核状态")
    @RequiresPermission("workload:report")
    @PostMapping("/records")
    public Result<Void> saveRecords(@Valid @RequestBody WorkloadRecordBatchDTO dto) {
        workloadService.saveRecords(dto);
        return Result.success("工作量填报已提交，待项目经理审核", null);
    }

    /**
     * 填报明细审核列表。
     * <p>HTTP：GET /workload/records/pending。
     * 按项目（必填）+ 填报日期（可选）+ 审核状态（可选，默认待审核）筛选。
     *
     * @param projectId    项目ID
     * @param recordDate   填报日期（可选，默认全部日期）
     * @param reviewStatus 审核状态（可选：pending/approved/rejected，默认 pending）
     * @return 填报明细列表（含作业上下文与审核信息）
     */
    @Operation(summary = "填报审核列表", description = "按项目/日期/状态查询工作量填报明细，供项目经理审核")
    @RequiresPermission("workload:review")
    @GetMapping("/records/pending")
    public Result<List<WorkloadRecordVO>> listPendingRecords(
            @Parameter(description = "项目ID") @RequestParam Long projectId,
            @Parameter(description = "填报日期，不传查全部日期") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordDate,
            @Parameter(description = "审核状态：pending/approved/rejected，默认 pending")
            @RequestParam(required = false, defaultValue = "pending") String reviewStatus) {
        return Result.success(workloadService.listReviewRecords(projectId, recordDate, reviewStatus));
    }

    /**
     * 批量审核填报明细（通过/驳回）。
     * <p>HTTP：POST /workload/records/review。
     * 仅 admin 或项目负责人可执行；CAS 语义保证同一条记录仅被审核一次；
     * 审核通过后记录计入已完成量与绩效。
     *
     * @param dto 审核数据（记录ID列表 + 审核结果 + 意见）
     * @return 实际审核成功的记录数（并发冲突时可能小于提交数）
     */
    @Operation(summary = "批量审核填报", description = "批量通过/驳回工作量填报（仅项目负责人或管理员），通过后计入统计")
    @RequiresPermission("workload:review")
    @PostMapping("/records/review")
    public Result<Integer> reviewRecords(@Valid @RequestBody WorkloadReviewDTO dto) {
        int rows = workloadService.reviewRecords(dto);
        String action = Boolean.TRUE.equals(dto.getApprove()) ? "通过" : "驳回";
        return Result.success("已" + action + " " + rows + " 条填报", rows);
    }

    /**
     * 人员绩效区间汇总（按绩效分降序）。
     * <p>HTTP：GET /workload/performance。
     *
     * @param projectId 项目ID
     * @param startDate 区间开始日期（含）
     * @param endDate   区间结束日期（含）
     * @return 人员绩效列表
     */
    @Operation(summary = "人员绩效汇总", description = "按人员汇总日期区间内完成量与绩效分（Σ 数量÷额定×权重）")
    @RequiresPermission("workload:query")
    @GetMapping("/performance")
    public Result<List<WorkloadWorkerPerformanceVO>> getWorkerPerformance(
            @Parameter(description = "项目ID") @RequestParam Long projectId,
            @Parameter(description = "区间开始日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "区间结束日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(workloadService.getWorkerPerformance(projectId, startDate, endDate));
    }

    /**
     * 导出某日工作量统计 Excel（模板样式）。
     * <p>HTTP：POST /workload/export。
     *
     * @param projectId 项目ID
     * @param date      统计日期（默认今天）
     * @return 导出记录 VO（含下载ID与文件名）
     */
    @Operation(summary = "导出工作量统计", description = "生成模板样式 Excel（作业类型合并+人员列+今日绩效行），返回导出记录")
    @RequiresPermission("workload:export")
    @PostMapping("/export")
    public Result<ExportRecordVO> export(
            @Parameter(description = "项目ID") @RequestParam Long projectId,
            @Parameter(description = "统计日期，默认今天") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success("导出成功", workloadService.exportExcel(projectId,
                date != null ? date : LocalDate.now(), UserContext.getUserId(), UserContext.getUsername()));
    }

}
