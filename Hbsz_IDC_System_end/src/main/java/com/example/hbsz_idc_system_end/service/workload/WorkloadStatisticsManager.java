package com.example.hbsz_idc_system_end.service.workload;

import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.entity.BizWorkloadItem;
import com.example.hbsz_idc_system_end.mapper.BizWorkloadRecordMapper;
import com.example.hbsz_idc_system_end.vo.WorkloadCellVO;
import com.example.hbsz_idc_system_end.vo.WorkloadMatrixVO;
import com.example.hbsz_idc_system_end.vo.WorkloadWorkerPerformanceVO;
import com.example.hbsz_idc_system_end.vo.WorkloadWorkerVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.hbsz_idc_system_end.service.workload.WorkloadQuerySupport.REVIEW_APPROVED;
import static com.example.hbsz_idc_system_end.service.workload.WorkloadQuerySupport.REVIEW_PENDING;

/**
 * 工作量统计矩阵协作类（原 WorkloadServiceImpl 统计矩阵域拆出）
 * <p>
 * 职责：可填报人员、某日统计矩阵（itemId × 人员）、人员绩效汇总（区间）。
 * 统计指标全部由 biz_workload_record 按 item/worker 分组实时聚合（仅 approved）。
 *
 * @author hbsz
 */
@Component
public class WorkloadStatisticsManager {

    private final BizWorkloadRecordMapper recordMapper;
    private final WorkloadQuerySupport support;

    public WorkloadStatisticsManager(BizWorkloadRecordMapper recordMapper, WorkloadQuerySupport support) {
        this.recordMapper = recordMapper;
        this.support = support;
    }

    public List<WorkloadWorkerVO> listWorkers(Long projectId) {
        support.checkProjectAccess(projectId);
        return support.listActiveTeamWorkers(projectId);
    }

    public WorkloadMatrixVO getMatrix(Long projectId, LocalDate date) {
        support.checkProjectAccess(projectId);
        WorkloadMatrixVO vo = new WorkloadMatrixVO();
        vo.setProjectId(projectId);
        vo.setStatisticsDate(date);
        vo.setProjectName(support.projectName(projectId));

        // 清单行（含已完成/剩余/今日量，仅统计已审核记录）
        List<BizWorkloadItem> items = support.listItemsByProject(projectId);
        Map<Long, Map<String, Object>> aggregates = support.aggregateByItem(projectId, date);
        vo.setItems(items.stream().map(item -> support.toItemVO(item, aggregates.get(item.getId()))).toList());

        // 当日矩阵：itemId → (workerName → 单元格{数量, 审核状态})，含全部状态供前端编辑回显
        List<Map<String, Object>> dailyRows = recordMapper.selectDailyMatrix(projectId, date);
        Map<Long, Map<String, WorkloadCellVO>> matrix = new LinkedHashMap<>();
        Map<String, WorkloadWorkerVO> workerMap = new LinkedHashMap<>();
        long pendingCount = 0;
        for (Map<String, Object> row : dailyRows) {
            Long itemId = support.toLong(row.get("itemId"));
            String workerName = String.valueOf(row.get("workerName"));
            BigDecimal quantity = support.toBigDecimal(row.get("quantity"));
            if (itemId == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            String reviewStatus = row.get("reviewStatus") != null ? String.valueOf(row.get("reviewStatus")) : REVIEW_APPROVED;
            WorkloadCellVO cell = new WorkloadCellVO();
            cell.setQuantity(quantity);
            cell.setReviewStatus(reviewStatus);
            cell.setReviewRemark(row.get("reviewRemark") != null ? String.valueOf(row.get("reviewRemark")) : null);
            matrix.computeIfAbsent(itemId, k -> new HashMap<>()).put(workerName, cell);
            if (REVIEW_PENDING.equals(reviewStatus)) {
                pendingCount++;
            }
            if (!workerMap.containsKey(workerName)) {
                WorkloadWorkerVO worker = new WorkloadWorkerVO();
                worker.setName(workerName);
                worker.setUserId(support.toLong(row.get("workerUserId")));
                workerMap.put(workerName, worker);
            }
        }
        vo.setMatrix(matrix);
        vo.setPendingCount(pendingCount);
        vo.setWorkers(support.sortWorkers(new ArrayList<>(workerMap.values())));

        // 每人当日绩效：Σ(数量 ÷ 额定 × 权重)，仅统计已审核通过的单元格
        Map<Long, BizWorkloadItem> itemMap = new HashMap<>();
        items.forEach(i -> itemMap.put(i.getId(), i));
        Map<String, BigDecimal> performance = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<String, WorkloadCellVO>> entry : matrix.entrySet()) {
            BizWorkloadItem item = itemMap.get(entry.getKey());
            if (item == null) {
                continue;
            }
            BigDecimal rated = support.ratedOf(item);
            BigDecimal weight = item.getPerformanceScore() != null ? item.getPerformanceScore() : BigDecimal.ONE;
            for (Map.Entry<String, WorkloadCellVO> cell : entry.getValue().entrySet()) {
                if (!REVIEW_APPROVED.equals(cell.getValue().getReviewStatus())) {
                    continue;
                }
                BigDecimal score = cell.getValue().getQuantity()
                        .divide(rated, 6, RoundingMode.HALF_UP).multiply(weight);
                performance.merge(cell.getKey(), score, BigDecimal::add);
            }
        }
        // 绩效保留 2 位小数
        Map<String, BigDecimal> rounded = new LinkedHashMap<>();
        performance.forEach((k, v) -> rounded.put(k, v.setScale(2, RoundingMode.HALF_UP)));
        vo.setWorkerPerformance(rounded);
        return vo;
    }

    public List<WorkloadWorkerPerformanceVO> getWorkerPerformance(Long projectId, LocalDate startDate, LocalDate endDate) {
        support.checkProjectAccess(projectId);
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "开始日期不能晚于结束日期");
        }
        List<Map<String, Object>> rows = recordMapper.selectWorkerPerformance(projectId, startDate, endDate);
        List<WorkloadWorkerPerformanceVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            WorkloadWorkerPerformanceVO vo = new WorkloadWorkerPerformanceVO();
            vo.setWorkerName(String.valueOf(row.get("workerName")));
            vo.setTotalQuantity(support.toBigDecimal(row.get("totalQuantity")));
            vo.setPerformanceScore(support.scale(support.toBigDecimal(row.get("performanceScore")), 2));
            Long days = support.toLong(row.get("recordDays"));
            vo.setRecordDays(days != null ? days : 0L);
            BigDecimal perf = vo.getPerformanceScore() != null ? vo.getPerformanceScore() : BigDecimal.ZERO;
            long d = vo.getRecordDays() != null ? vo.getRecordDays() : 0L;
            vo.setAvgDailyPerformance(d > 0
                    ? perf.divide(BigDecimal.valueOf(d), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            result.add(vo);
        }
        // 绩效分降序（模板按列展示，列表场景按绩效排名更直观）
        result.sort(Comparator.comparing(WorkloadWorkerPerformanceVO::getPerformanceScore).reversed());
        return result;
    }
}
