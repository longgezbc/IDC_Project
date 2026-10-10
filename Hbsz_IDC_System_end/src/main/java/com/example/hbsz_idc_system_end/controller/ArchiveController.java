package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.ArchiveStatisticsVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.ProjectQueryDTO;
import com.example.hbsz_idc_system_end.dto.ProjectVO;
import com.example.hbsz_idc_system_end.service.IArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 归档管理控制器。
 * <p>
 * 业务模块：项目归档管理。
 * 基础请求路径：{@code /projects}（归档相关子路径 {@code /archived}）。
 * 面向已归档项目的查询与统计，供归档中心页面展示历史项目及汇总数据。
 *
 * @author hbsz
 */
@Tag(name = "归档管理", description = "归档项目查询、统计等接口")
@RestController
@RequestMapping("/projects")
public class ArchiveController {

    private final IArchiveService archiveService;

    public ArchiveController(IArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    /**
     * 归档项目列表。
     * <p>HTTP：GET /projects/archived。分页查询已归档项目，支持按查询条件筛选。
     * 权限：{@code archive:query}。
     *
     * @param query 项目查询条件（含分页参数、筛选条件）
     * @return 已归档项目的分页结果
     */
    @Operation(summary = "归档项目列表", description = "分页查询已归档项目列表")
    @RequiresPermission("archive:query")
    @GetMapping("/archived")
    public Result<PageResult<ProjectVO>> archivedList(ProjectQueryDTO query) {
        return Result.success(archiveService.queryArchivedPage(query));
    }

    /**
     * 归档统计。
     * <p>HTTP：GET /projects/archived/statistics。获取归档项目的统计汇总数据。
     * 权限：{@code archive:query}。
     *
     * @return 归档统计信息
     */
    @Operation(summary = "归档统计", description = "获取归档项目统计数据")
    @RequiresPermission("archive:query")
    @GetMapping("/archived/statistics")
    public Result<ArchiveStatisticsVO> statistics() {
        return Result.success(archiveService.getStatistics());
    }

}
