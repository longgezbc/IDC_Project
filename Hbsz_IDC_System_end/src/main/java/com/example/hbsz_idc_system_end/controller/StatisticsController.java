package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.StatisticsVO;
import com.example.hbsz_idc_system_end.service.IStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 统计分析控制器。
 * <p>
 * 业务模块：数据统计与分析。
 * 基础请求路径：{@code /statistics}。
 * 提供工作台概览、项目状态/类型/优先级分布、月度趋势、金额统计及报表导出等分析接口。
 *
 * @author hbsz
 */
@Tag(name = "统计分析", description = "项目统计、分布、趋势等分析接口")
@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    private final IStatisticsService statisticsService;

    public StatisticsController(IStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 综合统计概览。
     * <p>HTTP：GET /statistics/overview。获取工作台首页的综合统计数据。
     * 权限：{@code statistics:query}。
     *
     * @return 综合统计概览数据
     */
    @Operation(summary = "综合统计概览", description = "获取工作台首页综合统计数据")
    @RequiresPermission("statistics:query")
    @GetMapping("/overview")
    public Result<StatisticsVO> overview() {
        return Result.success(statisticsService.getOverview());
    }

    /**
     * 项目状态分布。
     * <p>HTTP：GET /statistics/project-status。按状态统计项目数量。
     * 权限：{@code statistics:query}。
     *
     * @param dateRange 时间范围过滤（默认 all，全部）
     * @return 各状态项目数量分布列表
     */
    @Operation(summary = "项目状态分布", description = "按状态统计项目数量")
    @RequiresPermission("statistics:query")
    @GetMapping("/project-status")
    public Result<List<Map<String, Object>>> statusDistribution(@RequestParam(required = false, defaultValue = "all") String dateRange) {
        // L1修复：dateRange 暂未透传到 Service 层，留待后续实现
        return Result.success(statisticsService.getStatusDistribution());
    }

    /**
     * 项目类型分布。
     * <p>HTTP：GET /statistics/project-type。按类型统计项目数量。
     * 权限：{@code statistics:query}。
     *
     * @param dateRange 时间范围过滤（默认 all，全部）
     * @return 各类型项目数量分布列表
     */
    @Operation(summary = "项目类型分布", description = "按类型统计项目数量")
    @RequiresPermission("statistics:query")
    @GetMapping("/project-type")
    public Result<List<Map<String, Object>>> typeDistribution(@RequestParam(required = false, defaultValue = "all") String dateRange) {
        // L1修复：dateRange 暂未透传到 Service 层，留待后续实现
        return Result.success(statisticsService.getTypeDistribution());
    }

    /**
     * 项目优先级分布。
     * <p>HTTP：GET /statistics/project-priority。按优先级统计项目数量。
     * 权限：{@code statistics:query}。
     *
     * @param dateRange 时间范围过滤（默认 all，全部）
     * @return 各优先级项目数量分布列表
     */
    @Operation(summary = "项目优先级分布", description = "按优先级统计项目数量")
    @RequiresPermission("statistics:query")
    @GetMapping("/project-priority")
    public Result<List<Map<String, Object>>> priorityDistribution(@RequestParam(required = false, defaultValue = "all") String dateRange) {
        // L1修复：dateRange 暂未透传到 Service 层，留待后续实现
        return Result.success(statisticsService.getPriorityDistribution());
    }

    /**
     * 项目趋势统计。
     * <p>HTTP：GET /statistics/project-trend。按月统计指定年度的项目数量趋势。
     * 权限：{@code statistics:query}。
     *
     * @param year 统计年份，为空时由服务端取默认年份
     * @return 按月的项目数量趋势列表
     */
    @Operation(summary = "项目趋势统计", description = "按月统计项目数量趋势")
    @RequiresPermission("statistics:query")
    @GetMapping("/project-trend")
    public Result<List<Map<String, Object>>> monthlyTrend(@RequestParam(required = false) Integer year) {
        return Result.success(statisticsService.getMonthlyTrend(year));
    }

    /**
     * 金额统计。
     * <p>HTTP：GET /statistics/amount。统计合同总金额等金额类指标。
     * 权限：{@code statistics:query}。
     *
     * @param dateRange 时间范围过滤（默认 all，全部）
     * @return 金额统计数据
     */
    @Operation(summary = "金额统计", description = "统计合同总金额")
    @RequiresPermission("statistics:query")
    @GetMapping("/amount")
    public Result<Map<String, Object>> amountStatistics(@RequestParam(required = false, defaultValue = "all") String dateRange) {
        // L1修复：dateRange 暂未透传到 Service 层，留待后续实现
        return Result.success(statisticsService.getAmountStatistics());
    }

    /**
     * 导出报表。
     * <p>HTTP：GET /statistics/export。将统计报表导出为 Excel（xlsx）文件，
     * 通过响应流直接下载，文件名按 UTF-8 编码并携带当前日期。
     * 权限：{@code statistics:export}。
     *
     * @param response  HTTP 响应对象，用于写出 Excel 二进制流及下载头
     * @param dateRange 时间范围过滤（默认 all，全部）
     * @param type      报表类型（可选）
     * @throws java.io.IOException 写出响应流发生 IO 异常时抛出
     */
    @Operation(summary = "导出报表", description = "导出统计报表为Excel")
    @RequiresPermission("statistics:export")
    @GetMapping("/export")
    public void exportReport(jakarta.servlet.http.HttpServletResponse response,
                             @RequestParam(required = false, defaultValue = "all") String dateRange,
                             @RequestParam(required = false) String type) throws java.io.IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String fileName = java.net.URLEncoder.encode("统计报表_" + java.time.LocalDate.now() + ".xlsx", "UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        statisticsService.exportReport(response.getOutputStream());
    }

}
