package com.example.hbsz_idc_system_end.service.workload;

import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.ExportRecordVO;
import com.example.hbsz_idc_system_end.entity.BizExportRecord;
import com.example.hbsz_idc_system_end.mapper.BizExportRecordMapper;
import com.example.hbsz_idc_system_end.vo.WorkloadCellVO;
import com.example.hbsz_idc_system_end.vo.WorkloadItemVO;
import com.example.hbsz_idc_system_end.vo.WorkloadMatrixVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.example.hbsz_idc_system_end.service.workload.WorkloadQuerySupport.REVIEW_APPROVED;

/**
 * 工作量 Excel 导出协作类（原 WorkloadServiceImpl 导出域拆出）
 * <p>
 * 职责：生成模板版式 Excel（作业类型纵向合并、动态人员列、尾行"今日绩效"合计），
 * 仅导出已审核通过的数据；导出记录入库。注意：保留"显式 checkProjectAccess +
 * 委托 getMatrix（内部再查一次）"的既有双查顺序，勿优化合并以免行为漂移。
 *
 * @author hbsz
 */
@Slf4j
@Component
public class WorkloadExcelExporter {

    /** Excel 固定列数（序号~今日工作量），人员列在其后动态扩展 */
    private static final int FIXED_COL_COUNT = 11;

    private final BizExportRecordMapper exportRecordMapper;
    private final WorkloadStatisticsManager statisticsManager;
    private final WorkloadQuerySupport support;

    /** 导出文件存储根目录（与日报导出共用） */
    @Value("${hbsz.export.path:./exports}")
    private String exportPath;

    public WorkloadExcelExporter(BizExportRecordMapper exportRecordMapper,
                                 WorkloadStatisticsManager statisticsManager,
                                 WorkloadQuerySupport support) {
        this.exportRecordMapper = exportRecordMapper;
        this.statisticsManager = statisticsManager;
        this.support = support;
    }

    public ExportRecordVO exportExcel(Long projectId, LocalDate date, Long userId, String userName) {
        support.checkProjectAccess(projectId);
        WorkloadMatrixVO data = statisticsManager.getMatrix(projectId, date);

        // 导出为正式统计报表，仅包含已审核通过的数据：
        // 人员列 = 当日有 approved 单元格的人员；矩阵单元格 = approved 子集
        List<String> exportWorkers = new ArrayList<>();
        Map<Long, Map<String, WorkloadCellVO>> exportMatrix = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<String, WorkloadCellVO>> e : data.getMatrix().entrySet()) {
            for (Map.Entry<String, WorkloadCellVO> c : e.getValue().entrySet()) {
                if (REVIEW_APPROVED.equals(c.getValue().getReviewStatus())) {
                    exportMatrix.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>()).put(c.getKey(), c.getValue());
                    if (!exportWorkers.contains(c.getKey())) {
                        exportWorkers.add(c.getKey());
                    }
                }
            }
        }
        exportWorkers.sort(Comparator.naturalOrder());

        String datePart = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = "工作量统计_" + safeFileName(data.getProjectName()) + "_" + datePart + ".xlsx";
        String subDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        File dir = new File(exportPath, subDir);
        if (!dir.exists() && !dir.mkdirs()) {
            // mkdirs 静默返回 false 的场景必须显式抛错（read-only 容器常见），否则后续写入报 FileNotFoundException 难以定位
            throw new IllegalStateException("无法创建导出目录: " + dir.getAbsolutePath() + "，请检查容器是否有写入权限");
        }

        BizExportRecord record = new BizExportRecord();
        record.setUserId(userId);
        record.setUserName(userName);
        record.setReportType("workload");
        record.setStartDate(date);
        record.setEndDate(date);
        record.setFormat("xlsx");
        record.setFileName(fileName);
        try {
            File file = new File(dir, fileName);
            generateExcel(data, exportWorkers, exportMatrix, file);
            record.setFilePath(subDir + "/" + fileName);
            record.setFileSize(file.length());
            record.setStatus("success");
            log.info("[工作量统计导出] 文件生成成功: {}, 大小: {} 字节", file.getAbsolutePath(), file.length());
        } catch (Exception e) {
            log.error("[工作量统计导出] 文件生成失败", e);
            record.setStatus("failed");
            record.setRemark("生成失败：" + e.getMessage());
            record.setFilePath("");
            record.setFileSize(0L);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "工作量统计 Excel 生成失败: " + e.getMessage());
        }
        exportRecordMapper.insert(record);
        return toExportVO(record);
    }

    /**
     * 生成模板样式 Excel：
     * Row0 标题"工作量统计"（合并全列）；Row1 表头（11 固定列 + 动态人员列）；
     * 数据行按 sortOrder 排序，作业类型列纵向合并；尾行"今日绩效"按人员合计。
     * <p>
     * 入参 workers/matrix 为已审核通过（approved）的导出子集。
     */
    private void generateExcel(WorkloadMatrixVO data, List<String> workers,
                               Map<Long, Map<String, WorkloadCellVO>> matrix, File file) throws IOException {
        List<WorkloadItemVO> items = data.getItems();
        int totalCols = FIXED_COL_COUNT + workers.size();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            CellStyle titleStyle = buildStyle(workbook, 14, true, null, HorizontalAlignment.CENTER);
            CellStyle headerStyle = buildStyle(workbook, 10, true, IndexedColors.GREY_25_PERCENT, HorizontalAlignment.CENTER);
            CellStyle categoryStyle = buildStyle(workbook, 10, true, null, HorizontalAlignment.CENTER);
            CellStyle dataStyle = buildStyle(workbook, 10, false, null, HorizontalAlignment.LEFT);
            CellStyle numStyle = buildStyle(workbook, 10, false, null, HorizontalAlignment.RIGHT);
            CellStyle perfStyle = buildStyle(workbook, 10, true, IndexedColors.LEMON_CHIFFON, HorizontalAlignment.RIGHT);

            Sheet sheet = workbook.createSheet("工作量统计");
            // 标题行
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("工作量统计 - " + data.getProjectName() + "（" + data.getStatisticsDate() + "）");
            titleCell.setCellStyle(titleStyle);
            if (totalCols > 1) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, totalCols - 1));
            }

            // 表头行
            Row headerRow = sheet.createRow(1);
            String[] headers = {"序号", "作业类型", "作业范围", "额定工作量", "单位", "备注",
                    "工作总量", "已完成", "剩余工作量", "绩效分", "今日工作量"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            for (int i = 0; i < workers.size(); i++) {
                Cell cell = headerRow.createCell(FIXED_COL_COUNT + i);
                cell.setCellValue(workers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // 数据行（作业类型合并）
            int rowIdx = 2;
            int seq = 0;
            int categoryStart = -1;
            String currentCategory = null;
            for (WorkloadItemVO item : items) {
                Row row = sheet.createRow(rowIdx);
                seq++;
                setCell(row, 0, seq, numStyle);
                setCell(row, 1, item.getCategory(), categoryStyle);
                setCell(row, 2, item.getWorkScope(), dataStyle);
                setCell(row, 3, item.getRatedWorkload(), numStyle);
                setCell(row, 4, item.getUnit(), dataStyle);
                setCell(row, 5, item.getRemark(), dataStyle);
                setCell(row, 6, item.getTotalQuantity(), numStyle);
                setCell(row, 7, item.getCompletedQuantity(), numStyle);
                setCell(row, 8, item.getRemainingQuantity(), numStyle);
                setCell(row, 9, item.getPerformanceScore(), numStyle);
                setCell(row, 10, item.getTodayQuantity(), numStyle);
                Map<String, WorkloadCellVO> cells = matrix.get(item.getId());
                if (cells != null) {
                    for (int i = 0; i < workers.size(); i++) {
                        WorkloadCellVO cell = cells.get(workers.get(i));
                        if (cell != null && cell.getQuantity() != null) {
                            setCell(row, FIXED_COL_COUNT + i, cell.getQuantity(), numStyle);
                        }
                    }
                }
                // 作业类型列纵向合并：类目变化时合并上一段
                if (!Objects.equals(currentCategory, item.getCategory())) {
                    if (categoryStart >= 0 && rowIdx - 1 > categoryStart) {
                        sheet.addMergedRegion(new CellRangeAddress(categoryStart, rowIdx - 1, 1, 1));
                    }
                    categoryStart = rowIdx;
                    currentCategory = item.getCategory();
                }
                rowIdx++;
            }
            if (categoryStart >= 0 && rowIdx - 1 > categoryStart) {
                sheet.addMergedRegion(new CellRangeAddress(categoryStart, rowIdx - 1, 1, 1));
            }

            // 尾行：今日绩效（每人 Σ 数量÷额定×权重）
            if (!workers.isEmpty()) {
                Row perfRow = sheet.createRow(rowIdx);
                Cell label = perfRow.createCell(0);
                label.setCellValue("今日绩效");
                label.setCellStyle(perfStyle);
                sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, FIXED_COL_COUNT - 1));
                for (int i = 0; i < workers.size(); i++) {
                    BigDecimal perf = data.getWorkerPerformance().get(workers.get(i));
                    setCell(perfRow, FIXED_COL_COUNT + i, perf, perfStyle);
                }
            }

            // 列宽（前 11 固定列）
            int[] widths = {6, 22, 32, 12, 8, 18, 12, 12, 12, 10, 12};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            for (int i = 0; i < workers.size(); i++) {
                sheet.setColumnWidth(FIXED_COL_COUNT + i, 10 * 256);
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
            workbook.dispose();
        }
    }

    /** 构建通用单元格样式 */
    private CellStyle buildStyle(Workbook workbook, int fontSize, boolean bold,
                                 IndexedColors fillColor, HorizontalAlignment align) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) fontSize);
        font.setBold(bold);
        style.setFont(font);
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        if (fillColor != null) {
            style.setFillForegroundColor(fillColor.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }

    /** 写入单元格值（数值统一按 BigDecimal 展示，去掉无意义小数尾零由 Excel 数字格式处理） */
    private void setCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (value != null) {
            cell.setCellValue(String.valueOf(value));
        }
        cell.setCellStyle(style);
    }

    /** 文件名/Sheet 名安全化：去除路径分隔符与非法字符 */
    private String safeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "项目";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /** 导出记录 → VO */
    private ExportRecordVO toExportVO(BizExportRecord record) {
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

    /** 文件大小人类可读格式（B/KB/MB） */
    private String formatFileSize(Long size) {
        if (size == null || size <= 0) {
            return "0 B";
        }
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        }
        return String.format("%.1f MB", size / 1024.0 / 1024.0);
    }
}
