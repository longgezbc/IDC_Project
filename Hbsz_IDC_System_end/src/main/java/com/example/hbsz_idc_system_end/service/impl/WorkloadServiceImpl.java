package com.example.hbsz_idc_system_end.service.impl;

import com.example.hbsz_idc_system_end.dto.ExportRecordVO;
import com.example.hbsz_idc_system_end.dto.WorkloadItemSaveDTO;
import com.example.hbsz_idc_system_end.dto.WorkloadRecordBatchDTO;
import com.example.hbsz_idc_system_end.dto.WorkloadReviewDTO;
import com.example.hbsz_idc_system_end.service.IWorkloadService;
import com.example.hbsz_idc_system_end.service.workload.WorkloadExcelExporter;
import com.example.hbsz_idc_system_end.service.workload.WorkloadItemManager;
import com.example.hbsz_idc_system_end.service.workload.WorkloadReportManager;
import com.example.hbsz_idc_system_end.service.workload.WorkloadReviewManager;
import com.example.hbsz_idc_system_end.service.workload.WorkloadStatisticsManager;
import com.example.hbsz_idc_system_end.vo.WorkloadItemVO;
import com.example.hbsz_idc_system_end.vo.WorkloadMatrixVO;
import com.example.hbsz_idc_system_end.vo.WorkloadMyReportVO;
import com.example.hbsz_idc_system_end.vo.WorkloadRecordVO;
import com.example.hbsz_idc_system_end.vo.WorkloadWorkerPerformanceVO;
import com.example.hbsz_idc_system_end.vo.WorkloadWorkerVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 工作量统计服务实现（纯委托门面）
 * <p>
 * 核心职责：作业清单维护（含模板导入）→ 每日填报（项目组成员限定 + 待审核状态）→
 * 项目经理审核（批量 CAS 通过/驳回）→ 实时聚合统计（仅已审核记录）→ 模板样式 Excel 导出。
 * <p>
 * 拆分说明（2026-09）：本类原为 1100+ 行巨石实现，已按功能域拆分为
 * service.workload 包下 6 个协作类，本类退化为纯委托门面（保持 IWorkloadService
 * 接口与 Controller 零改动）：
 * <ul>
 *   <li>{@link WorkloadQuerySupport} 共享查询/校验支撑 + 审核状态常量唯一定义点；</li>
 *   <li>{@link WorkloadItemManager} 清单维护（含模板导入与启动加载）；</li>
 *   <li>{@link WorkloadStatisticsManager} 统计矩阵/人员/绩效；</li>
 *   <li>{@link WorkloadReportManager} 每日填报 + 快速填报上下文；</li>
 *   <li>{@link WorkloadReviewManager} 审核列表 + 批量 CAS 审核；</li>
 *   <li>{@link WorkloadExcelExporter} 模板样式 Excel 导出。</li>
 * </ul>
 * 事务边界在协作类方法上（@Transactional 经本门面跨 bean 调用生效），
 * 勿在协作类内部自调用事务方法（自调用不走代理，事务会失效）。
 * <p>
 * 关键业务设计要点（继承自原实现）：
 * <ul>
 *   <li>填报人员必须是项目组在组成员（biz_project_team is_active=1）；</li>
 *   <li>已通过记录禁止直接修改（需驳回后重新填报），重编辑 pending/rejected 记录重置回 pending；</li>
 *   <li>统计全部实时聚合（仅 approved），清单表不冗余存量；</li>
 *   <li>批量审核为 CAS 式原子 UPDATE（WHERE review_status='pending'）；</li>
 *   <li>Excel 导出仅含已审核通过数据，还原模板版式。</li>
 * </ul>
 *
 * @author hbsz
 */
@Service
public class WorkloadServiceImpl implements IWorkloadService {

    private final WorkloadItemManager itemManager;
    private final WorkloadReportManager reportManager;
    private final WorkloadReviewManager reviewManager;
    private final WorkloadStatisticsManager statisticsManager;
    private final WorkloadExcelExporter excelExporter;

    public WorkloadServiceImpl(WorkloadItemManager itemManager,
                               WorkloadReportManager reportManager,
                               WorkloadReviewManager reviewManager,
                               WorkloadStatisticsManager statisticsManager,
                               WorkloadExcelExporter excelExporter) {
        this.itemManager = itemManager;
        this.reportManager = reportManager;
        this.reviewManager = reviewManager;
        this.statisticsManager = statisticsManager;
        this.excelExporter = excelExporter;
    }

    // ====================================================================
    // 清单维护 → WorkloadItemManager
    // ====================================================================

    @Override
    public List<WorkloadItemVO> listItems(Long projectId, LocalDate statisticsDate) {
        return itemManager.listItems(projectId, statisticsDate);
    }

    @Override
    public WorkloadItemVO createItem(WorkloadItemSaveDTO dto) {
        return itemManager.createItem(dto);
    }

    @Override
    public WorkloadItemVO updateItem(Long id, WorkloadItemSaveDTO dto) {
        return itemManager.updateItem(id, dto);
    }

    @Override
    public void deleteItem(Long id) {
        itemManager.deleteItem(id);
    }

    @Override
    public int importTemplate(Long projectId) {
        return itemManager.importTemplate(projectId);
    }

    // ====================================================================
    // 统计矩阵 → WorkloadStatisticsManager
    // ====================================================================

    @Override
    public List<WorkloadWorkerVO> listWorkers(Long projectId) {
        return statisticsManager.listWorkers(projectId);
    }

    @Override
    public WorkloadMatrixVO getMatrix(Long projectId, LocalDate date) {
        return statisticsManager.getMatrix(projectId, date);
    }

    @Override
    public List<WorkloadWorkerPerformanceVO> getWorkerPerformance(Long projectId, LocalDate startDate, LocalDate endDate) {
        return statisticsManager.getWorkerPerformance(projectId, startDate, endDate);
    }

    // ====================================================================
    // 填报与快速填报上下文 → WorkloadReportManager
    // ====================================================================

    @Override
    public void saveRecords(WorkloadRecordBatchDTO dto) {
        reportManager.saveRecords(dto);
    }

    @Override
    public WorkloadMyReportVO getMyReportContext(Long projectId, LocalDate date) {
        return reportManager.getMyReportContext(projectId, date);
    }

    // ====================================================================
    // 填报审核 → WorkloadReviewManager
    // ====================================================================

    @Override
    public List<WorkloadRecordVO> listReviewRecords(Long projectId, LocalDate recordDate, String reviewStatus) {
        return reviewManager.listReviewRecords(projectId, recordDate, reviewStatus);
    }

    @Override
    public int reviewRecords(WorkloadReviewDTO dto) {
        return reviewManager.reviewRecords(dto);
    }

    // ====================================================================
    // Excel 导出 → WorkloadExcelExporter
    // ====================================================================

    @Override
    public ExportRecordVO exportExcel(Long projectId, LocalDate date, Long userId, String userName) {
        return excelExporter.exportExcel(projectId, date, userId, userName);
    }
}
