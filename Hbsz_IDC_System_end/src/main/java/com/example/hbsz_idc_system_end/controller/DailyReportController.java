package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.DailyReportVO;
import com.example.hbsz_idc_system_end.dto.ExportRecordVO;
import com.example.hbsz_idc_system_end.entity.BizExportRecord;
import com.example.hbsz_idc_system_end.service.IDailyReportService;
import com.example.hbsz_idc_system_end.utils.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 甘特图进度日报导出控制器
 * <p>
 * 业务模块：甘特图进度日报数据预览、Excel 导出、导出历史管理与重新下载。
 * 基础请求路径：{@code /daily-report}。
 * 导出格式固定为 Excel（.xlsx），以甘特图任务进度为核心数据源。
 *
 * @author hbsz
 */
@Tag(name = "甘特图日报导出", description = "甘特图进度日报预览、Excel导出、历史记录管理")
@RestController
@RequestMapping("/daily-report")
public class DailyReportController {

    private final IDailyReportService dailyReportService;
    // 引入 ObjectMapper 用于规范化序列化 JSON 响应，避免手动拼接字符串导致转义/编码异常
    private final ObjectMapper objectMapper;

    /** 导出文件存储根目录 */
    @Value("${hbsz.export.path:./exports}")
    private String exportPath;

    public DailyReportController(IDailyReportService dailyReportService, ObjectMapper objectMapper) {
        this.dailyReportService = dailyReportService;
        this.objectMapper = objectMapper;
    }

    /**
     * 甘特图进度日报数据预览
     * <p>HTTP：GET /daily-report/preview
     * 返回项目甘特图进度汇总与任务级进度明细，供前端渲染。
     * 支持通过 projectId 筛选单个项目，未传则查询全部未归档项目。
     * 权限：{@code statistics:query}。
     *
     * @param startDate 统计开始日期（yyyy-MM-dd）
     * @param endDate   统计结束日期（yyyy-MM-dd）
     * @param projectId 项目ID（可选，null 表示全部项目）
     * @return 甘特图进度日报数据视图对象
     */
    @Operation(summary = "甘特图日报预览", description = "获取甘特图进度日报预览数据，支持按项目筛选")
    @RequiresPermission("statistics:query")
    @GetMapping("/preview")
    public Result<DailyReportVO> preview(@RequestParam @NotBlank String startDate,
                                          @RequestParam @NotBlank String endDate,
                                          @RequestParam(required = false) Long projectId) {
        return Result.success(dailyReportService.getDailyReport(startDate, endDate, projectId));
    }

    /**
     * 导出甘特图进度日报 Excel 文件
     * <p>HTTP：POST /daily-report/export
     * 生成"每个项目一个 Sheet"的施工日报 Excel 文件，落盘存储并返回记录信息。
     * 支持通过 projectId 筛选单个项目，未传则导出全部未归档项目。
     * 权限：{@code statistics:export}。
     *
     * @param startDate 统计开始日期（yyyy-MM-dd）
     * @param endDate   统计结束日期（yyyy-MM-dd）
     * @param projectId 项目ID（可选，null 表示全部项目）
     * @return 导出记录信息（含文件名、大小等）
     */
    @Operation(summary = "导出甘特图日报", description = "生成甘特图进度日报 Excel 文件（每项目一个Sheet），支持按项目筛选")
    @RequiresPermission("statistics:export")
    @PostMapping("/export")
    public Result<ExportRecordVO> export(@RequestParam @NotBlank String startDate,
                                          @RequestParam @NotBlank String endDate,
                                          @RequestParam(required = false) Long projectId) {
        Long userId = UserContext.getUserId();
        String userName = UserContext.getUsername();
        ExportRecordVO vo = dailyReportService.exportDailyReport(startDate, endDate, projectId, userId, userName);
        if ("failed".equals(vo.getStatus())) {
            return Result.error(5000, "导出失败，请稍后重试");
        }
        return Result.success("导出成功", vo);
    }

    /**
     * 导出历史记录列表（分页）
     * <p>HTTP：GET /daily-report/history
     * 权限：{@code statistics:query}。
     *
     * @param page 页码（从 1 开始，默认 1）
     * @param size 每页条数（默认 10）
     * @return 分页数据（含 records 列表与 total 总数）
     */
    @Operation(summary = "导出历史", description = "分页查询当前用户的导出历史记录")
    @RequiresPermission("statistics:query")
    @GetMapping("/history")
    public Result<Map<String, Object>> history(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContext.getUserId();
        List<ExportRecordVO> records = dailyReportService.getExportHistory(userId, page, size);
        long total = dailyReportService.getExportHistoryTotal(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    /**
     * 下载导出文件
     * <p>HTTP：GET /daily-report/download/{id}
     * 权限：{@code statistics:query}。
     *
     * @param id       导出记录 ID
     * @param response HTTP 响应对象
     * @throws IOException IO 异常
     */
    @Operation(summary = "下载导出文件", description = "根据记录ID重新下载导出文件")
    @RequiresPermission("statistics:query")
    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Long userId = UserContext.getUserId();
        BizExportRecord record = dailyReportService.getRecordForDownload(id, userId);
        if (record == null) {
            response.setContentType("application/json;charset=UTF-8");
            // 使用 ObjectMapper 序列化替代手动 JSON 字符串拼接，
            // 避免特殊字符未转义导致的 JSON 注入或解析失败
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("code", 404);
            errorBody.put("message", "导出记录不存在或无权下载");
            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            return;
        }

        File file = new File(exportPath, record.getFilePath());
        if (!file.exists()) {
            response.setContentType("application/json;charset=UTF-8");
            // 使用 ObjectMapper 序列化替代手动 JSON 字符串拼接
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("code", 404);
            errorBody.put("message", "文件已被删除");
            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            return;
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        // 注意：二进制流下载不应调用 setCharacterEncoding，否则会在 Content-Type 上追加 ";charset=UTF-8"
        // filename 同时提供 URL 编码（兼容旧浏览器）与 filename*（RFC 5987，支持中文直显）
        String encodedFileName = URLEncoder.encode(record.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentLengthLong(file.length());

        Files.copy(file.toPath(), response.getOutputStream());
    }

    /**
     * 删除导出记录
     * <p>HTTP：DELETE /daily-report/history/{id}
     * 权限：{@code statistics:export}。
     *
     * @param id 导出记录 ID
     * @return 操作结果
     */
    @Operation(summary = "删除导出记录", description = "删除导出记录及物理文件")
    @RequiresPermission("statistics:export")
    @DeleteMapping("/history/{id}")
    public Result<Void> deleteRecord(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        dailyReportService.deleteExportRecord(id, userId);
        return Result.success("删除成功", null);
    }
}
