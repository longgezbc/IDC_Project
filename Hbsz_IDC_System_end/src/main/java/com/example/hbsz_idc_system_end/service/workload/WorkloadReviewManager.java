package com.example.hbsz_idc_system_end.service.workload;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.NotificationSendDTO;
import com.example.hbsz_idc_system_end.dto.WorkloadReviewDTO;
import com.example.hbsz_idc_system_end.entity.BizWorkloadRecord;
import com.example.hbsz_idc_system_end.mapper.BizWorkloadRecordMapper;
import com.example.hbsz_idc_system_end.service.INotificationService;
import com.example.hbsz_idc_system_end.service.ProjectAccessGuard;
import com.example.hbsz_idc_system_end.utils.UserContext;
import com.example.hbsz_idc_system_end.vo.WorkloadRecordVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.example.hbsz_idc_system_end.service.workload.WorkloadQuerySupport.REVIEW_APPROVED;
import static com.example.hbsz_idc_system_end.service.workload.WorkloadQuerySupport.REVIEW_PENDING;
import static com.example.hbsz_idc_system_end.service.workload.WorkloadQuerySupport.REVIEW_REJECTED;

/**
 * 工作量审核协作类（原 WorkloadServiceImpl 审核域拆出）
 * <p>
 * 职责：审核列表查询 + 批量 CAS 通过/驳回。
 * 前置校验顺序（特征测试锁定，禁止调整）：记录存在 → 同属一项目 →
 * 项目访问权限（IDOR） → 审核归属（admin 或项目负责人，委托 ProjectAccessGuard）。
 * 事务注解在协作类方法上（经门面跨 bean 调用生效，勿在类内部自调用事务方法）。
 *
 * @author hbsz
 */
@Slf4j
@Component
public class WorkloadReviewManager {

    private final BizWorkloadRecordMapper recordMapper;
    private final WorkloadQuerySupport support;
    private final ProjectAccessGuard accessGuard;
    private final INotificationService notificationService;

    public WorkloadReviewManager(BizWorkloadRecordMapper recordMapper,
                                 WorkloadQuerySupport support,
                                 ProjectAccessGuard accessGuard,
                                 INotificationService notificationService) {
        this.recordMapper = recordMapper;
        this.support = support;
        this.accessGuard = accessGuard;
        this.notificationService = notificationService;
    }

    public List<WorkloadRecordVO> listReviewRecords(Long projectId, LocalDate recordDate, String reviewStatus) {
        support.checkProjectAccess(projectId);
        if (StringUtils.hasText(reviewStatus)
                && !REVIEW_PENDING.equals(reviewStatus)
                && !REVIEW_APPROVED.equals(reviewStatus)
                && !REVIEW_REJECTED.equals(reviewStatus)) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "非法的审核状态: " + reviewStatus);
        }
        List<Map<String, Object>> rows = recordMapper.selectReviewRecords(projectId, recordDate,
                StringUtils.hasText(reviewStatus) ? reviewStatus : null);
        List<WorkloadRecordVO> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            WorkloadRecordVO vo = new WorkloadRecordVO();
            vo.setId(support.toLong(row.get("id")));
            vo.setProjectId(support.toLong(row.get("projectId")));
            vo.setItemId(support.toLong(row.get("itemId")));
            vo.setCategory(row.get("category") != null ? String.valueOf(row.get("category")) : null);
            vo.setWorkScope(row.get("workScope") != null ? String.valueOf(row.get("workScope")) : null);
            vo.setUnit(row.get("unit") != null ? String.valueOf(row.get("unit")) : null);
            vo.setRecordDate(row.get("recordDate") != null ? LocalDate.parse(String.valueOf(row.get("recordDate"))) : null);
            vo.setWorkerName(row.get("workerName") != null ? String.valueOf(row.get("workerName")) : null);
            vo.setWorkerUserId(support.toLong(row.get("workerUserId")));
            vo.setQuantity(support.toBigDecimal(row.get("quantity")));
            vo.setReviewStatus(row.get("reviewStatus") != null ? String.valueOf(row.get("reviewStatus")) : null);
            vo.setReviewRemark(row.get("reviewRemark") != null ? String.valueOf(row.get("reviewRemark")) : null);
            vo.setReviewerName(row.get("reviewerName") != null ? String.valueOf(row.get("reviewerName")) : null);
            Object reviewTime = row.get("reviewTime");
            vo.setReviewTime(reviewTime instanceof LocalDateTime ldt ? ldt : null);
            Object createdAt = row.get("createdAt");
            vo.setCreatedAt(createdAt instanceof LocalDateTime ldt ? ldt : null);
            result.add(vo);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public int reviewRecords(WorkloadReviewDTO dto) {
        Long reviewerId = UserContext.getUserId();
        String reviewerName = UserContext.getUsername();
        LocalDateTime now = LocalDateTime.now();

        List<BizWorkloadRecord> records = recordMapper.selectBatchIds(dto.getIds());
        if (records.size() != dto.getIds().size()) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "部分填报记录不存在");
        }
        // 记录必须同属一个项目（前端按项目维度提交，跨项目混入视为非法请求）
        Long projectId = records.get(0).getProjectId();
        for (BizWorkloadRecord r : records) {
            if (!Objects.equals(r.getProjectId(), projectId)) {
                throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "不能跨项目批量审核");
            }
        }
        // IDOR 防护 + 归属校验：审核是高危操作，仅 admin 或项目负责人可执行
        support.checkProjectAccess(projectId);
        // 归属校验委托共享 ProjectAccessGuard 单点（与授权/团队/项目服务共用同一实现）
        accessGuard.validateOwnership(projectId);

        // CAS 式原子更新：带 WHERE review_status='pending' 条件，并发/重复审核时
        // 受影响行数为 0（另一线程已处理），据此向调用方返回实际成功数
        String targetStatus = Boolean.TRUE.equals(dto.getApprove()) ? REVIEW_APPROVED : REVIEW_REJECTED;
        LambdaUpdateWrapper<BizWorkloadRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(BizWorkloadRecord::getId, dto.getIds())
                .eq(BizWorkloadRecord::getReviewStatus, REVIEW_PENDING)
                .set(BizWorkloadRecord::getReviewStatus, targetStatus)
                .set(BizWorkloadRecord::getReviewerId, reviewerId)
                .set(BizWorkloadRecord::getReviewerName, reviewerName)
                .set(BizWorkloadRecord::getReviewTime, now)
                .set(StringUtils.hasText(dto.getRemark()), BizWorkloadRecord::getReviewRemark, dto.getRemark());
        int rows = recordMapper.update(null, wrapper);
        log.info("[工作量审核] 项目={}, ids={} 条, 结果={}, 实际更新 {} 条, 审核人={}",
                projectId, dto.getIds().size(), targetStatus, rows, reviewerName);
        // 审核结果回执：实际更新数 > 0 才通知（CAS 冲突的重复请求不重复通知）
        if (rows > 0) {
            notifyWorkloadReviewed(records, dto.getApprove(), reviewerName, dto.getRemark());
        }
        return rows;
    }

    /**
     * 通知填报人审核结果（按填报人分组汇总，避免逐条轰炸）。
     * <p>
     * 批量审核通常覆盖多人，按 workerUserId 聚合为"每人一条"通知；
     * 无 workerUserId 的历史记录跳过。静默投递。
     *
     * @param records 本次审核涉及的记录
     * @param approve 是否通过
     * @param reviewerName 审核人姓名
     * @param reviewRemark 审核意见
     */
    private void notifyWorkloadReviewed(List<BizWorkloadRecord> records, Boolean approve,
                                        String reviewerName, String reviewRemark) {
        try {
            boolean approved = Boolean.TRUE.equals(approve);
            Map<Long, List<BizWorkloadRecord>> byWorker = new HashMap<>();
            for (BizWorkloadRecord r : records) {
                if (r.getWorkerUserId() != null) {
                    byWorker.computeIfAbsent(r.getWorkerUserId(), k -> new ArrayList<>()).add(r);
                }
            }
            for (Map.Entry<Long, List<BizWorkloadRecord>> entry : byWorker.entrySet()) {
                NotificationSendDTO notif = new NotificationSendDTO();
                notif.setUserId(entry.getKey());
                notif.setTitle(approved ? "工作量审核通过" : "工作量审核驳回");
                notif.setContent("您有 " + entry.getValue().size() + " 条工作量填报已"
                        + (approved ? "通过" : "驳回") + "，审核人：" + reviewerName
                        + (StringUtils.hasText(reviewRemark) ? "，意见：" + reviewRemark : ""));
                notif.setNotifyType("workload");
                notif.setBizType("project");
                notif.setBizId(entry.getValue().get(0).getProjectId());
                notif.setPriority(approved ? 1 : 2);
                notificationService.sendNotification(notif);
            }
        } catch (Exception e) {
            log.warn("[工作量审核] 通知填报人审核结果失败: error={}", e.getMessage());
        }
    }
}
