package com.example.hbsz_idc_system_end.service.impl;

import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.DailyReportVO;
import com.example.hbsz_idc_system_end.dto.ExportRecordVO;
import com.example.hbsz_idc_system_end.entity.BizExportRecord;
import com.example.hbsz_idc_system_end.mapper.BizExportRecordMapper;
import com.example.hbsz_idc_system_end.mapper.DailyReportMapper;
import com.example.hbsz_idc_system_end.service.IDailyReportService;
import com.example.hbsz_idc_system_end.service.IProjectAuthorizationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 甘特图进度日报服务实现
 * <p>
 * 核心职责：采集甘特图任务进度数据 → 组装日报 VO → 生成 Excel 文件 → 落盘并记录历史。
 * <p>
 * Excel 文件采用"每个项目一个 Sheet"的结构，每个 Sheet 包含：
 * <ol>
 *   <li>项目信息头 — 项目编号/名称/经理/进度/状态/计划周期/任务统计</li>
 *   <li>任务明细表 — 每个任务的计划/实际日期、进度百分比、延期天数等甘特图核心指标</li>
 * </ol>
 * 支持按 projectId 筛选，导出单个项目或全部项目的施工日报。
 *
 * @author hbsz
 */
@Slf4j
@Service
public class DailyReportServiceImpl implements IDailyReportService {

    private final DailyReportMapper dailyReportMapper;
    private final BizExportRecordMapper exportRecordMapper;
    private final IProjectAuthorizationService projectAuthorizationService;

    /** 导出文件存储根目录 */
    @Value("${hbsz.export.path:./exports}")
    private String exportPath;

    /** 任务明细表表头列数（与 TASK_HEADERS 数组长度一致） */
    private static final int TASK_COL_COUNT = 13;
    private static final String[] TASK_HEADERS = {
            "序号", "任务名称", "负责人", "任务类型",
            "计划开始", "计划结束", "实际开始", "实际结束",
            "进度(%)", "状态", "是否延期", "延期天数",
            "备注"
    };

    public DailyReportServiceImpl(DailyReportMapper dailyReportMapper,
                                   BizExportRecordMapper exportRecordMapper,
                                   IProjectAuthorizationService projectAuthorizationService) {
        this.dailyReportMapper = dailyReportMapper;
        this.exportRecordMapper = exportRecordMapper;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    /**
     * 采集甘特图进度日报数据并组装 VO
     *
     * @param startDate 报告开始日期（用于标题展示）
     * @param endDate   报告结束日期（用于标题展示）
     * @param projectId 项目ID（可选，null 表示全部项目）
     * @return 甘特图进度日报数据视图对象
     */
    @Override
    public DailyReportVO getDailyReport(String startDate, String endDate, Long projectId) {
        // IDOR 防护：当指定 projectId 时，校验当前用户对该项目的访问权限
        if (projectId != null && !projectAuthorizationService.canAccessProject(projectId)) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权查看该项目的日报");
        }

        LocalDate start = parseDate(startDate, "开始日期");
        LocalDate end = parseDate(endDate, "结束日期");
        validateDateRange(start, end);

        LocalDate today = LocalDate.now();
        DailyReportVO vo = new DailyReportVO();
        vo.setProjectId(projectId);

        // 构建报告标题
        String baseTitle = "项目甘特图进度日报（" + startDate + " 至 " + endDate + "）";

        // 查询项目甘特图进度汇总
        List<Map<String, Object>> projectSummary = dailyReportMapper.selectProjectGanttSummary(today, projectId);
        vo.setProjectSummary(projectSummary);

        // 若指定了项目，设置项目名称用于标题
        if (projectId != null && !projectSummary.isEmpty()) {
            String pname = String.valueOf(projectSummary.get(0).get("projectName"));
            vo.setProjectName(pname);
            vo.setReportTitle(pname + " 施工日报（" + startDate + " 至 " + endDate + "）");
        } else {
            vo.setReportTitle(baseTitle);
        }

        // 查询任务级甘特图进度明细
        List<Map<String, Object>> taskProgress = dailyReportMapper.selectGanttTaskProgress(today, projectId);
        vo.setTaskProgress(taskProgress);

        // 汇总全局概览指标
        vo.setSummary(buildSummary(projectSummary, taskProgress));

        return vo;
    }

    /**
     * 导出甘特图进度日报 Excel 文件并记录历史
     *
     * @param startDate 统计开始日期
     * @param endDate   统计结束日期
     * @param projectId 项目ID（可选，null 导出全部项目）
     * @param userId    导出人ID
     * @param userName  导出人姓名
     * @return 导出记录信息
     */
    @Override
    public ExportRecordVO exportDailyReport(String startDate, String endDate, Long projectId,
                                             Long userId, String userName) {
        // IDOR 防护：当指定 projectId 时，校验当前用户对该项目的访问权限
        if (projectId != null && !projectAuthorizationService.canAccessProject(projectId)) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权导出该项目的日报");
        }

        // 采集日报数据
        DailyReportVO reportData = getDailyReport(startDate, endDate, projectId);

        // 生成文件名：全部项目 → 甘特图日报_yyyyMMdd-yyyyMMdd.xlsx
        //           单项目   → 甘特日报_{项目名}_yyyyMMdd-yyyyMMdd.xlsx
        String datePart = startDate.replace("-", "") + "-" + endDate.replace("-", "");
        String fileName;
        if (projectId != null && reportData.getProjectName() != null) {
            String safeName = sanitizeFileName(reportData.getProjectName());
            fileName = "甘特日报_" + safeName + "_" + datePart + ".xlsx";
        } else {
            fileName = "甘特图日报_" + datePart + ".xlsx";
        }

        // 按日期分子目录存储
        String subDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        File dir = new File(exportPath, subDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建导出目录: " + dir.getAbsolutePath() + "，请检查容器是否有写入权限");
        }

        File file = new File(dir, fileName);
        BizExportRecord record = new BizExportRecord();
        record.setUserId(userId);
        record.setUserName(userName);
        record.setReportType("daily");
        record.setStartDate(parseDate(startDate, "开始日期"));
        record.setEndDate(parseDate(endDate, "结束日期"));
        record.setFormat("xlsx");
        record.setFileName(fileName);

        try {
            generateExcel(reportData, file);
            record.setFilePath(subDir + "/" + fileName);
            record.setFileSize(file.length());
            record.setStatus("success");
            log.info("[施工日报导出] 文件生成成功: {}, 大小: {} 字节", file.getAbsolutePath(), file.length());
        } catch (Exception e) {
            log.error("[施工日报导出] 文件生成失败", e);
            record.setStatus("failed");
            record.setRemark("生成失败：" + e.getMessage());
            record.setFilePath("");
            record.setFileSize(0L);
        }

        exportRecordMapper.insert(record);
        return convertToVO(record);
    }

    /**
     * 分页查询当前用户的导出历史
     */
    @Override
    public List<ExportRecordVO> getExportHistory(Long userId, int page, int size) {
        // 【修复M13】校验 page >= 1，避免 page<=0 时 offset 为负数导致 LIMIT 语句 SQL 报错。
        if (page < 1) {
            page = 1;
        }
        int offset = (page - 1) * size;
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizExportRecord> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(BizExportRecord::getUserId, userId)
               .orderByDesc(BizExportRecord::getCreatedAt)
               .last("LIMIT " + offset + ", " + size);
        List<BizExportRecord> records = exportRecordMapper.selectList(wrapper);
        return records.stream().map(this::convertToVO).toList();
    }

    /**
     * 导出历史记录总数
     */
    @Override
    public long getExportHistoryTotal(Long userId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizExportRecord> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(BizExportRecord::getUserId, userId);
        return exportRecordMapper.selectCount(wrapper);
    }

    /**
     * 获取导出记录用于下载（含权限校验）
     */
    @Override
    public BizExportRecord getRecordForDownload(Long id, Long userId) {
        BizExportRecord record = exportRecordMapper.selectById(id);
        if (record == null || record.getDeleted() == 1) {
            return null;
        }
        if (!record.getUserId().equals(userId)) {
            return null;
        }
        return record;
    }

    /**
     * 删除导出记录（同时删除物理文件）
     */
    @Override
    public void deleteExportRecord(Long id, Long userId) {
        BizExportRecord record = getRecordForDownload(id, userId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "导出记录不存在或无权操作");
        }
        if ("success".equals(record.getStatus()) && record.getFilePath() != null && !record.getFilePath().isEmpty()) {
            File file = new File(exportPath, record.getFilePath());
            if (file.exists()) {
                file.delete();
            }
        }
        exportRecordMapper.deleteById(id);
    }

    // ==================== 私有方法 ====================

    /**
     * 汇总全局概览指标
     */
    private Map<String, Object> buildSummary(List<Map<String, Object>> projects,
                                              List<Map<String, Object>> tasks) {
        Map<String, Object> summary = new LinkedHashMap<>();
        int totalProjects = projects.size();
        int totalTasks = tasks.size();
        int completedTasks = 0, inProgressTasks = 0, pendingTasks = 0;
        int delayedTasks = 0, milestoneCount = 0, keyTaskCount = 0;

        for (Map<String, Object> t : tasks) {
            String status = String.valueOf(t.getOrDefault("status", ""));
            if ("已完成".equals(status)) completedTasks++;
            else if ("进行中".equals(status)) inProgressTasks++;
            else if ("未开始".equals(status)) pendingTasks++;
            if (toInt(t.get("isDelayed")) == 1) delayedTasks++;
            if (toInt(t.get("isMilestone")) == 1) milestoneCount++;
            if (toInt(t.get("isKeyTask")) == 1) keyTaskCount++;
        }

        summary.put("reportDate", LocalDate.now().toString());
        summary.put("totalProjects", totalProjects);
        summary.put("totalTasks", totalTasks);
        summary.put("completedTasks", completedTasks);
        summary.put("inProgressTasks", inProgressTasks);
        summary.put("pendingTasks", pendingTasks);
        summary.put("delayedTasks", delayedTasks);
        summary.put("milestoneCount", milestoneCount);
        summary.put("keyTaskCount", keyTaskCount);
        return summary;
    }

    /**
     * 使用 Apache POI 生成"每项目一个 Sheet"的施工日报 Excel
     * <p>
     * 每个Sheet结构：
     * <pre>
     * Row 0: 【项目名称】施工日报（合并单元格标题）
     * Row 1: 项目编号 | val | 项目名称 | val | 项目经理 | val | 项目进度 | val | 项目状态 | val | 计划开始 | val | 计划结束
     * Row 2: 计划结束val | 任务总数 | val | 已完成 | val | 进行中 | val | 未开始 | val | 延期数 | val |  |  |
     * Row 3: （空行分隔）
     * Row 4: 序号 | 任务名称 | 负责人 | 任务类型 | 计划开始 | 计划结束 | 实际开始 | 实际结束 | 进度(%) | 状态 | 是否延期 | 延期天数 | 备注（表头）
     * Row 5+: 任务明细数据
     * </pre>
     *
     * @param data 日报数据
     * @param file 目标文件
     */
    private void generateExcel(DailyReportVO data, File file) throws IOException {
        // 按项目分组任务
        Map<Long, List<Map<String, Object>>> tasksByProject = new LinkedHashMap<>();
        for (Map<String, Object> t : data.getTaskProgress()) {
            Long pid = toLong(t.get("projectId"));
            tasksByProject.computeIfAbsent(pid, k -> new ArrayList<>()).add(t);
        }

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            // ========== 创建样式 ==========
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle labelStyle = createLabelStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle delayedStyle = createDelayedStyle(workbook);

            // ========== 为每个项目创建 Sheet ==========
            // 注：Excel Sheet 名称必须唯一，DB 中可能存在同名项目（如多个"IDC机房建设调试项目"），
            // 因此使用 usedSheetNames 集合去重，重名时追加 "(2)"/"(3)" 等后缀。
            int sheetIdx = 0;
            Set<String> usedSheetNames = new HashSet<>();
            for (Map<String, Object> project : data.getProjectSummary()) {
                Long pid = toLong(project.get("projectId"));
                List<Map<String, Object>> tasks = tasksByProject.getOrDefault(pid, Collections.emptyList());
                String projectName = String.valueOf(project.get("projectName"));

                String sheetName = sanitizeSheetName(projectName, sheetIdx + 1, usedSheetNames);
                usedSheetNames.add(sheetName);
                Sheet sheet = workbook.createSheet(sheetName);

                writeProjectSheet(sheet, project, tasks, titleStyle, headerStyle, labelStyle, dataStyle, delayedStyle);
                sheetIdx++;
            }

            // 若没有任何项目，创建一个空 Sheet 避免空文件
            if (sheetIdx == 0) {
                Sheet sheet = workbook.createSheet("无数据");
                Row row = sheet.createRow(0);
                row.createCell(0).setCellValue("当前筛选条件下无项目数据");
            }

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
            workbook.dispose();
        }
    }

    /**
     * 写入单个项目的 Sheet 内容
     */
    private void writeProjectSheet(Sheet sheet, Map<String, Object> project, List<Map<String, Object>> tasks,
                                    CellStyle titleStyle, CellStyle headerStyle,
                                    CellStyle labelStyle, CellStyle dataStyle, CellStyle delayedStyle) {
        String projectName = String.valueOf(project.get("projectName"));

        // ===== Row 0: 标题（合并 A1:M1）=====
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("【" + projectName + "】施工日报");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, TASK_COL_COUNT - 1));

        // ===== Row 1-2: 项目信息（label-value 交错排列）=====
        Object projectCode = project.get("projectCode");
        Object managerName = project.get("managerName");
        Object progress = project.get("progress");
        Object status = translateStatus(project.get("status"));
        Object startDate = formatValue(project.get("startDate"));
        Object endDate = formatValue(project.get("endDate"));
        Object taskTotal = project.get("taskTotal");
        Object taskCompleted = project.get("taskCompleted");
        Object taskInProgress = project.get("taskInProgress");
        Object taskPending = project.get("taskPending");
        Object taskDelayed = project.get("taskDelayed");

        // Row 1: 项目编号 | val | 项目名称 | val | 项目经理 | val | 项目进度 | val | 项目状态 | val | 计划开始 | val | 计划结束
        Row infoRow1 = sheet.createRow(1);
        setInfoCell(infoRow1, 0, "项目编号", labelStyle);
        setInfoCell(infoRow1, 1, projectCode, dataStyle);
        setInfoCell(infoRow1, 2, "项目名称", labelStyle);
        setInfoCell(infoRow1, 3, projectName, dataStyle);
        setInfoCell(infoRow1, 4, "项目经理", labelStyle);
        setInfoCell(infoRow1, 5, managerName, dataStyle);
        setInfoCell(infoRow1, 6, "项目进度", labelStyle);
        setInfoCell(infoRow1, 7, progress == null ? "" : progress + "%", dataStyle);
        setInfoCell(infoRow1, 8, "项目状态", labelStyle);
        setInfoCell(infoRow1, 9, status, dataStyle);
        setInfoCell(infoRow1, 10, "计划开始", labelStyle);
        setInfoCell(infoRow1, 11, startDate, dataStyle);
        setInfoCell(infoRow1, 12, "", dataStyle);

        // Row 2: 计划结束 | val | 任务总数 | val | 已完成 | val | 进行中 | val | 未开始 | val | 延期数 | val | 
        Row infoRow2 = sheet.createRow(2);
        setInfoCell(infoRow2, 0, "计划结束", labelStyle);
        setInfoCell(infoRow2, 1, endDate, dataStyle);
        setInfoCell(infoRow2, 2, "任务总数", labelStyle);
        setInfoCell(infoRow2, 3, taskTotal, dataStyle);
        setInfoCell(infoRow2, 4, "已完成", labelStyle);
        setInfoCell(infoRow2, 5, taskCompleted, dataStyle);
        setInfoCell(infoRow2, 6, "进行中", labelStyle);
        setInfoCell(infoRow2, 7, taskInProgress, dataStyle);
        setInfoCell(infoRow2, 8, "未开始", labelStyle);
        setInfoCell(infoRow2, 9, taskPending, dataStyle);
        setInfoCell(infoRow2, 10, "延期数", labelStyle);
        setInfoCell(infoRow2, 11, taskDelayed, dataStyle);
        setInfoCell(infoRow2, 12, "", dataStyle);

        // ===== Row 3: 空行分隔 =====
        sheet.createRow(3);

        // ===== Row 4: 任务明细表头 =====
        Row headerRow = sheet.createRow(4);
        for (int i = 0; i < TASK_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(TASK_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        // ===== Row 5+: 任务明细数据 =====
        int rowIdx = 5;
        int taskIdx = 1;
        for (Map<String, Object> t : tasks) {
            Row row = sheet.createRow(rowIdx++);
            boolean isDelayed = toInt(t.get("isDelayed")) == 1;
            CellStyle rowStyle = isDelayed ? delayedStyle : dataStyle;

            createCell(row, 0, taskIdx++, rowStyle);
            createCell(row, 1, t.get("taskName"), rowStyle);
            createCell(row, 2, t.get("assigneeName"), rowStyle);
            createCell(row, 3, translateTaskType(t.get("isMilestone"), t.get("isKeyTask")), rowStyle);
            createCell(row, 4, formatValue(t.get("startDate")), rowStyle);
            createCell(row, 5, formatValue(t.get("endDate")), rowStyle);
            createCell(row, 6, formatValue(t.get("actualStartDate")), rowStyle);
            createCell(row, 7, formatValue(t.get("actualEndDate")), rowStyle);
            createCell(row, 8, t.get("progress"), rowStyle);
            createCell(row, 9, translateStatus(t.get("status")), rowStyle);
            createCell(row, 10, isDelayed ? "是" : "否", rowStyle);
            createCell(row, 11, t.get("delayDays"), rowStyle);
            createCell(row, 12, t.get("remark"), rowStyle);
        }

        // 若无任务，写入提示行
        if (tasks.isEmpty()) {
            Row emptyRow = sheet.createRow(rowIdx);
            Cell emptyCell = emptyRow.createCell(0);
            emptyCell.setCellValue("该项目暂无任务数据");
            emptyCell.setCellStyle(dataStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, TASK_COL_COUNT - 1));
        }

        // ===== 设置列宽 =====
        int[] colWidths = {1800, 5000, 2500, 2500, 3000, 3000, 3000, 3000, 2000, 2500, 2000, 2000, 5000};
        for (int i = 0; i < colWidths.length; i++) {
            sheet.setColumnWidth(i, colWidths[i]);
        }
    }

    // ==================== 样式创建方法 ====================

    /** 标题样式：大号加粗居中 */
    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 14);
        font.setBold(true);
        font.setFontName("微软雅黑");
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /** 表头样式：灰底加粗居中 */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        font.setFontName("微软雅黑");
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(style);
        return style;
    }

    /** 标签样式：加粗左对齐（用于项目信息字段名） */
    private CellStyle createLabelStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("微软雅黑");
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        addBorders(style);
        return style;
    }

    /** 数据样式：居中 */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(style);
        return style;
    }

    /** 延期任务样式：红底居中（高亮延期任务） */
    private CellStyle createDelayedStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        addBorders(style);
        return style;
    }

    /** 添加四边边框 */
    private void addBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    // ==================== 单元格写入辅助 ====================

    /** 写入信息行单元格（label-value 对） */
    private void setInfoCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
        } else {
            cell.setCellValue(String.valueOf(value));
        }
        cell.setCellStyle(style);
    }

    /** 创建数据单元格并设置样式 */
    private void createCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
        cell.setCellStyle(style);
    }

    // ==================== 工具方法 ====================

    /**
     * 将值转为字符串，处理 LocalDateTime / LocalDate / java.sql.Date / Date / Timestamp 等类型
     * <p>
     * 注意类型判断顺序：java.sql.Date 继承自 java.util.Date，必须在 java.util.Date 之前判断。
     */
    private Object formatValue(Object value) {
        if (value == null) return "";
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        if (value instanceof java.time.LocalDate ld) {
            return ld.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        if (value instanceof java.sql.Date sd) {
            return sd.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        if (value instanceof java.util.Date d) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
        }
        return value;
    }

    /** 将任务状态码翻译为中文（DB 已存中文，直接透传） */
    private String translateStatus(Object status) {
        if (status == null) return "";
        return String.valueOf(status);
    }

    /** 根据里程碑/关键任务标记翻译任务类型 */
    private String translateTaskType(Object isMilestone, Object isKeyTask) {
        if (toInt(isMilestone) == 1) return "里程碑";
        if (toInt(isKeyTask) == 1) return "关键任务";
        return "普通任务";
    }

    /** 安全地将对象转为 int */
    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 安全地将对象转为 Long */
    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 将实体转为 VO */
    private ExportRecordVO convertToVO(BizExportRecord record) {
        ExportRecordVO vo = new ExportRecordVO();
        vo.setId(record.getId());
        vo.setUserName(record.getUserName());
        vo.setReportType(record.getReportType());
        vo.setStartDate(record.getStartDate());
        vo.setEndDate(record.getEndDate());
        vo.setFormat(record.getFormat());
        vo.setFileName(record.getFileName());
        vo.setFileSize(record.getFileSize());
        vo.setFileSizeText(formatFileSize(record.getFileSize()));
        vo.setStatus(record.getStatus());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    /** 格式化文件大小为人类可读文本 */
    private String formatFileSize(Long bytes) {
        if (bytes == null || bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    /** 解析日期字符串 */
    private LocalDate parseDate(String dateStr, String field) {
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), field + "格式错误，应为 yyyy-MM-dd");
        }
    }

    /** 校验日期区间合法性 */
    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "开始日期不能晚于结束日期");
        }
        if (start.plusDays(365).isBefore(end)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "查询区间不能超过 365 天");
        }
    }

    /**
     * 清理项目名称作为 Excel Sheet 名称，并保证在工作簿内唯一
     * <p>
     * Excel Sheet 名称限制：最长 31 字符，不能包含 \ / ? * [ ] :，
     * 且同一工作簿内不能重名。当出现重名时追加 "(2)"/"(3)" 等后缀。
     *
     * @param name           原始项目名称
     * @param index          项目序号（用于空名称回退）
     * @param usedSheetNames 已使用的 Sheet 名称集合
     * @return 合法且唯一的 Sheet 名称
     */
    private String sanitizeSheetName(String name, int index, Set<String> usedSheetNames) {
        if (name == null || name.isEmpty()) {
            name = "项目" + index;
        }
        String safe = name.replaceAll("[\\\\/?*\\[\\]:]", "_");
        // 若重名，追加 (2)/(3) 后缀，需为后缀预留字符空间
        String base = safe;
        int maxBaseLen = 31 - "(999)".length();
        if (base.length() > maxBaseLen) {
            base = base.substring(0, maxBaseLen);
        }
        String candidate = base;
        int suffix = 2;
        while (usedSheetNames.contains(candidate)) {
            candidate = base + "(" + suffix + ")";
            suffix++;
        }
        return candidate;
    }

    /**
     * 清理项目名称作为文件名
     * <p>
     * 文件名不能包含 \ / : * ? " < > |
     */
    private String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) return "项目";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
