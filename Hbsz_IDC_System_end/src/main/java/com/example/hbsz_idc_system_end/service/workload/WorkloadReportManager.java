package com.example.hbsz_idc_system_end.service.workload;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.NotificationSendDTO;
import com.example.hbsz_idc_system_end.dto.WorkloadRecordBatchDTO;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizProjectTeam;
import com.example.hbsz_idc_system_end.entity.BizWorkloadItem;
import com.example.hbsz_idc_system_end.entity.BizWorkloadRecord;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectTeamMapper;
import com.example.hbsz_idc_system_end.mapper.BizWorkloadItemMapper;
import com.example.hbsz_idc_system_end.mapper.BizWorkloadRecordMapper;
import com.example.hbsz_idc_system_end.service.INotificationService;
import com.example.hbsz_idc_system_end.utils.UserContext;
import com.example.hbsz_idc_system_end.vo.WorkloadCellVO;
import com.example.hbsz_idc_system_end.vo.WorkloadMyReportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.example.hbsz_idc_system_end.service.workload.WorkloadQuerySupport.REVIEW_APPROVED;
import static com.example.hbsz_idc_system_end.service.workload.WorkloadQuerySupport.REVIEW_PENDING;

/**
 * 工作量填报协作类（原 WorkloadServiceImpl 填报域拆出）
 * <p>
 * 职责：每日填报批量保存（upsert + 待审核状态）与"我的快速填报"上下文。
 * 填报人员必须是项目组在组成员；已通过记录禁止直接修改（需驳回后重新填报）。
 * 事务注解在协作类方法上（经门面跨 bean 调用生效，勿在类内部自调用事务方法）。
 *
 * @author hbsz
 */
@Slf4j
@Component
public class WorkloadReportManager {

    private final BizWorkloadItemMapper itemMapper;
    private final BizWorkloadRecordMapper recordMapper;
    private final BizProjectMapper projectMapper;
    private final BizProjectTeamMapper projectTeamMapper;
    private final WorkloadQuerySupport support;
    private final INotificationService notificationService;

    public WorkloadReportManager(BizWorkloadItemMapper itemMapper,
                                 BizWorkloadRecordMapper recordMapper,
                                 BizProjectMapper projectMapper,
                                 BizProjectTeamMapper projectTeamMapper,
                                 WorkloadQuerySupport support,
                                 INotificationService notificationService) {
        this.itemMapper = itemMapper;
        this.recordMapper = recordMapper;
        this.projectMapper = projectMapper;
        this.projectTeamMapper = projectTeamMapper;
        this.support = support;
        this.notificationService = notificationService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveRecords(WorkloadRecordBatchDTO dto) {
        support.checkProjectAccess(dto.getProjectId());
        // 校验清单项归属该项目，防止跨项目越权写入
        Set<Long> itemIds = new HashSet<>();
        Set<Long> workerUserIds = new HashSet<>();
        for (WorkloadRecordBatchDTO.RecordEntry entry : dto.getRecords()) {
            if (entry.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            itemIds.add(entry.getItemId());
            workerUserIds.add(entry.getWorkerUserId());
        }
        if (itemIds.isEmpty()) {
            return;
        }
        List<BizWorkloadItem> items = itemMapper.selectBatchIds(itemIds);
        Map<Long, Long> ownerMap = new HashMap<>();
        items.forEach(i -> ownerMap.put(i.getId(), i.getProjectId()));
        for (Long itemId : itemIds) {
            if (!Objects.equals(ownerMap.get(itemId), dto.getProjectId())) {
                throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "清单项不属于当前项目: " + itemId);
            }
        }

        // 校验填报人员均为项目组在组成员（业务硬约束：工作量只允许授权成员填报）
        Map<Long, String> memberNames = support.mapActiveTeamMemberNames(dto.getProjectId());
        for (Long workerUserId : workerUserIds) {
            if (!memberNames.containsKey(workerUserId)) {
                throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED,
                        "施工人员不在项目组中（userId=" + workerUserId + "），请先在项目组管理中添加该成员");
            }
        }

        Long currentUserId = UserContext.getUserId();
        // 预查当日已有记录，构建 (itemId, workerName) → record 索引实现 upsert
        List<BizWorkloadRecord> existing = recordMapper.selectList(new LambdaQueryWrapper<BizWorkloadRecord>()
                .eq(BizWorkloadRecord::getProjectId, dto.getProjectId())
                .eq(BizWorkloadRecord::getRecordDate, dto.getRecordDate()));
        Map<String, BizWorkloadRecord> existingIndex = new HashMap<>();
        for (BizWorkloadRecord r : existing) {
            existingIndex.put(r.getItemId() + "|" + r.getWorkerName(), r);
        }

        for (WorkloadRecordBatchDTO.RecordEntry entry : dto.getRecords()) {
            if (entry.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String workerName = memberNames.get(entry.getWorkerUserId());
            String key = entry.getItemId() + "|" + workerName;
            BizWorkloadRecord record = existingIndex.get(key);
            if (record == null) {
                record = new BizWorkloadRecord();
                record.setProjectId(dto.getProjectId());
                record.setItemId(entry.getItemId());
                record.setRecordDate(dto.getRecordDate());
                record.setWorkerName(workerName);
                record.setWorkerUserId(entry.getWorkerUserId());
                record.setQuantity(entry.getQuantity());
                record.setReviewStatus(REVIEW_PENDING);
                record.setCreatedBy(currentUserId);
                recordMapper.insert(record);
            } else {
                // 已审核通过的记录禁止直接修改（数据已计入统计，需项目经理驳回后重新填报）
                if (REVIEW_APPROVED.equals(record.getReviewStatus())) {
                    throw new BusinessException(ResultCode.BUSINESS_ERROR,
                            "「" + workerName + "」在「" + record.getRecordDate() + "」的填报已审核通过，不可修改；如需调整请联系项目经理驳回后重新填报");
                }
                // 覆盖 pending/rejected 记录：更新数量并重置回待审核（重新走审核流程）。
                // 注：updateById 会忽略 null 字段，清空审核人信息必须用 UpdateWrapper 显式 SET NULL
                LambdaUpdateWrapper<BizWorkloadRecord> uw = new LambdaUpdateWrapper<>();
                uw.eq(BizWorkloadRecord::getId, record.getId())
                        .set(BizWorkloadRecord::getQuantity, entry.getQuantity())
                        .set(BizWorkloadRecord::getWorkerUserId, entry.getWorkerUserId())
                        .set(BizWorkloadRecord::getReviewStatus, REVIEW_PENDING)
                        .set(BizWorkloadRecord::getReviewerId, null)
                        .set(BizWorkloadRecord::getReviewerName, null)
                        .set(BizWorkloadRecord::getReviewTime, null)
                        .set(BizWorkloadRecord::getReviewRemark, null);
                recordMapper.update(null, uw);
            }
        }
        log.info("[工作量填报] 项目={}, 日期={}, 保存 {} 条明细（待审核）, 操作人={}",
                dto.getProjectId(), dto.getRecordDate(), dto.getRecords().size(), currentUserId);
        notifyWorkloadSubmitted(dto.getProjectId(), dto.getRecordDate(), dto.getRecords().size());
    }

    /**
     * 通知项目经理有新的工作量填报待审核。
     * <p>
     * 静默投递——通知失败不影响填报主流程（事务内异常会被捕获并仅记日志）。
     *
     * @param projectId 项目ID
     * @param recordDate 填报日期
     * @param count 本次提交条数
     */
    private void notifyWorkloadSubmitted(Long projectId, LocalDate recordDate, int count) {
        try {
            BizProject project = projectMapper.selectById(projectId);
            if (project == null || project.getManagerId() == null) {
                // 项目未关联项目经理用户：工作量审核通知无法投递，WARN 暴露数据缺口（不再静默丢失）
                log.warn("[工作量填报] 项目未关联项目经理用户，审核通知跳过: projectId={}, managerName={}",
                        projectId, project != null ? project.getManagerName() : "项目不存在");
                return;
            }
            NotificationSendDTO notif = new NotificationSendDTO();
            notif.setUserId(project.getManagerId());
            notif.setTitle("工作量填报待审核");
            notif.setContent("项目「" + project.getName() + "」有 " + count
                    + " 条工作量填报待审核（" + recordDate + "）");
            notif.setNotifyType("workload");
            notif.setBizType("project");
            notif.setBizId(projectId);
            notif.setPriority(2);
            notificationService.sendNotification(notif);
        } catch (Exception e) {
            log.warn("[工作量填报] 通知项目经理审核失败: projectId={}, error={}", projectId, e.getMessage());
        }
    }

    public WorkloadMyReportVO getMyReportContext(Long projectId, LocalDate date) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或认证已过期");
        }
        LocalDate reportDate = date != null ? date : LocalDate.now();

        WorkloadMyReportVO vo = new WorkloadMyReportVO();
        vo.setMyUserId(userId);
        vo.setMyName(UserContext.getUsername());
        vo.setReportDate(reportDate);

        // 我作为在组成员参与的项目（未归档），按组长置前 + 项目名排序
        List<BizProjectTeam> myTeams = projectTeamMapper.selectList(new LambdaQueryWrapper<BizProjectTeam>()
                .eq(BizProjectTeam::getUserId, userId)
                .eq(BizProjectTeam::getIsActive, 1));
        List<WorkloadMyReportVO.ProjectBrief> projects = new ArrayList<>();
        if (!myTeams.isEmpty()) {
            Set<Long> projectIds = new HashSet<>();
            myTeams.forEach(t -> projectIds.add(t.getProjectId()));
            Map<Long, BizProject> projectMap = new HashMap<>();
            for (BizProject p : projectMapper.selectBatchIds(projectIds)) {
                projectMap.put(p.getId(), p);
            }
            for (BizProjectTeam team : myTeams) {
                BizProject p = projectMap.get(team.getProjectId());
                // 未归档项目才可填报
                if (p == null || (p.getIsArchived() != null && p.getIsArchived() == 1)) {
                    continue;
                }
                WorkloadMyReportVO.ProjectBrief brief = new WorkloadMyReportVO.ProjectBrief();
                brief.setProjectId(p.getId());
                brief.setProjectName(p.getName());
                brief.setRoleInTeam(team.getRoleInTeam());
                projects.add(brief);
            }
            projects.sort(Comparator
                    .comparing((WorkloadMyReportVO.ProjectBrief b) -> "leader".equals(b.getRoleInTeam()) ? 0 : 1)
                    .thenComparing(WorkloadMyReportVO.ProjectBrief::getProjectName));
        }
        vo.setProjects(projects);

        // projectId 为空：仅返回项目列表（前端单项目自动选中后再带 projectId 二次请求）
        if (projectId == null) {
            vo.setTodayRecords(Map.of());
            vo.setYesterdayRecords(Map.of());
            vo.setFrequentItems(List.of());
            vo.setTotalItemCount(0);
            return vo;
        }
        support.checkProjectExists(projectId);
        // 快速填报语义：当前用户须为该项目在组成员（与 saveRecords 的成员校验对称）
        if (projects.stream().noneMatch(p -> p.getProjectId().equals(projectId))) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED,
                    "您不是该项目组成员，无法填报；请先在项目组管理中添加您为成员");
        }
        vo.setProjectId(projectId);
        vo.setProjectName(support.projectName(projectId));

        // 今日已有填报：itemId → 单元格（含审核状态，approved 卡片锁定）
        Map<Long, WorkloadCellVO> todayRecords = new LinkedHashMap<>();
        for (BizWorkloadRecord r : recordMapper.selectList(new LambdaQueryWrapper<BizWorkloadRecord>()
                .eq(BizWorkloadRecord::getProjectId, projectId)
                .eq(BizWorkloadRecord::getWorkerUserId, userId)
                .eq(BizWorkloadRecord::getRecordDate, reportDate)
                .gt(BizWorkloadRecord::getQuantity, BigDecimal.ZERO))) {
            WorkloadCellVO cell = new WorkloadCellVO();
            cell.setQuantity(r.getQuantity());
            cell.setReviewStatus(r.getReviewStatus());
            cell.setReviewRemark(r.getReviewRemark());
            todayRecords.put(r.getItemId(), cell);
        }
        vo.setTodayRecords(todayRecords);

        // 昨日数量（供"复制昨天"）
        Map<Long, BigDecimal> yesterday = new LinkedHashMap<>();
        for (BizWorkloadRecord r : recordMapper.selectList(new LambdaQueryWrapper<BizWorkloadRecord>()
                .eq(BizWorkloadRecord::getProjectId, projectId)
                .eq(BizWorkloadRecord::getWorkerUserId, userId)
                .eq(BizWorkloadRecord::getRecordDate, reportDate.minusDays(1))
                .gt(BizWorkloadRecord::getQuantity, BigDecimal.ZERO))) {
            yesterday.put(r.getItemId(), r.getQuantity());
        }
        vo.setYesterdayRecords(yesterday);

        // 最近 14 天常用项：按 itemId 聚合填报天数与最后数量
        List<BizWorkloadRecord> recent = recordMapper.selectList(new LambdaQueryWrapper<BizWorkloadRecord>()
                .eq(BizWorkloadRecord::getProjectId, projectId)
                .eq(BizWorkloadRecord::getWorkerUserId, userId)
                .ge(BizWorkloadRecord::getRecordDate, reportDate.minusDays(14))
                .lt(BizWorkloadRecord::getRecordDate, reportDate.plusDays(1))
                .gt(BizWorkloadRecord::getQuantity, BigDecimal.ZERO));
        Map<Long, List<BizWorkloadRecord>> byItem = new HashMap<>();
        for (BizWorkloadRecord r : recent) {
            byItem.computeIfAbsent(r.getItemId(), k -> new ArrayList<>()).add(r);
        }
        if (!byItem.isEmpty()) {
            Map<Long, BizWorkloadItem> itemMap = new HashMap<>();
            for (BizWorkloadItem item : itemMapper.selectBatchIds(byItem.keySet())) {
                itemMap.put(item.getId(), item);
            }
            List<WorkloadMyReportVO.FrequentItem> frequent = new ArrayList<>();
            for (Map.Entry<Long, List<BizWorkloadRecord>> entry : byItem.entrySet()) {
                BizWorkloadItem item = itemMap.get(entry.getKey());
                if (item == null) {
                    continue; // 清单项已删除，跳过
                }
                List<BizWorkloadRecord> records = entry.getValue();
                records.sort(Comparator.comparing(BizWorkloadRecord::getRecordDate).reversed());
                WorkloadMyReportVO.FrequentItem fi = new WorkloadMyReportVO.FrequentItem();
                fi.setItemId(item.getId());
                fi.setCategory(item.getCategory());
                fi.setWorkScope(item.getWorkScope());
                fi.setUnit(item.getUnit());
                fi.setRatedWorkload(item.getRatedWorkload());
                fi.setFillCount(records.size());
                fi.setLastQuantity(records.get(0).getQuantity());
                fi.setLastDate(records.get(0).getRecordDate());
                frequent.add(fi);
            }
            // 常用优先级：填报天数降序 → 最后填报日期降序
            frequent.sort(Comparator
                    .comparing(WorkloadMyReportVO.FrequentItem::getFillCount, Comparator.reverseOrder())
                    .thenComparing(WorkloadMyReportVO.FrequentItem::getLastDate, Comparator.nullsLast(Comparator.reverseOrder())));
            vo.setFrequentItems(frequent);
        } else {
            vo.setFrequentItems(List.of());
        }

        // 清单总项数（前端提示可通过搜索添加更多项）
        vo.setTotalItemCount(Math.toIntExact(itemMapper.selectCount(new LambdaQueryWrapper<BizWorkloadItem>()
                .eq(BizWorkloadItem::getProjectId, projectId))));

        log.debug("[工作量快速填报] 上下文：user={}, project={}, date={}, 常用 {} 项, 今日已填 {} 项",
                userId, projectId, reportDate, vo.getFrequentItems().size(), todayRecords.size());
        return vo;
    }
}
