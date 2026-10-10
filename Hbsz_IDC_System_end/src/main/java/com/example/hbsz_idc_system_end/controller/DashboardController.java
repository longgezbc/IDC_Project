package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.DashboardSummaryVO;
import com.example.hbsz_idc_system_end.dto.MyTaskItemVO;
import com.example.hbsz_idc_system_end.dto.PageQuery;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.ProgressLogVO;
import com.example.hbsz_idc_system_end.dto.RiskBoardVO;
import com.example.hbsz_idc_system_end.service.IDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台控制器
 * <p>
 * 业务模块：当前登录用户的工作台数据聚合。
 * 基础请求路径：{@code /dashboard}。
 * 面向登录用户自身，提供汇总统计、我的任务列表、我的填报列表。
 * 不需要额外权限码——只要登录即可访问。
 *
 * @author hbsz
 */
@Tag(name = "工作台", description = "工作台汇总、我的任务、我的填报等接口")
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final IDashboardService dashboardService;

    public DashboardController(IDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 工作台汇总统计。
     * <p>HTTP：GET /dashboard/summary。
     * 返回当前用户的任务统计、填报统计、待审核数、未读通知数等。
     *
     * @return 工作台汇总 VO
     */
    @Operation(summary = "工作台汇总", description = "按当前用户聚合的统计数据（任务/填报/审核/通知）")
    @GetMapping("/summary")
    public Result<DashboardSummaryVO> summary() {
        return Result.success(dashboardService.getSummary());
    }

    /**
     * 我的任务列表（分页）。
     * <p>HTTP：GET /dashboard/my-tasks。查询分配给当前用户的任务。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   任务状态筛选（可空）
     * @return 任务分页列表
     */
    @Operation(summary = "我的任务", description = "分页查询分配给当前用户的任务")
    @GetMapping("/my-tasks")
    public Result<PageResult<MyTaskItemVO>> myTasks(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status) {
        PageQuery query = new PageQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return Result.success(dashboardService.myTasks(query, status));
    }

    /**
     * 我的填报列表（分页）。
     * <p>HTTP：GET /dashboard/my-submissions。查询当前用户提交的进度填报。
     *
     * @param pageNum      页码
     * @param pageSize     每页条数
     * @param reviewStatus 审核状态筛选（pending/approved/rejected，可空）
     * @return 进度日志分页列表
     */
    @Operation(summary = "我的填报", description = "分页查询当前用户提交的进度填报")
    @GetMapping("/my-submissions")
    public Result<PageResult<ProgressLogVO>> mySubmissions(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String reviewStatus) {
        PageQuery query = new PageQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return Result.success(dashboardService.mySubmissions(query, reviewStatus));
    }

    /**
     * PM 风险预警看板。
     * <p>HTTP：GET /dashboard/risk-board。
     * 聚合当前用户所负责项目的风险信息：逾期任务、即将到期任务（3 天内）、
     * 待审核填报数、各项目风险等级。仅 PM / admin 角色可访问。
     *
     * @return 风险预警看板 VO
     */
    @Operation(summary = "风险预警看板", description = "PM 视角的项目风险聚合：逾期/临期/待审核")
    // H2修复：风险看板属于 PM 视角的敏感数据聚合，缺少权限校验会导致普通用户越权查看项目风险信息，添加权限注解
    @RequiresPermission("project:query")
    @GetMapping("/risk-board")
    public Result<RiskBoardVO> riskBoard() {
        return Result.success(dashboardService.getRiskBoard());
    }

}
