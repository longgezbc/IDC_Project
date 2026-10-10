package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.*;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizTask;
import com.example.hbsz_idc_system_end.entity.BizTaskProgressLog;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizTaskMapper;
import com.example.hbsz_idc_system_end.mapper.BizTaskProgressLogMapper;
import com.example.hbsz_idc_system_end.dto.NotificationSendDTO;
import com.example.hbsz_idc_system_end.service.INotificationService;
import com.example.hbsz_idc_system_end.service.IProjectAuthorizationService;
import com.example.hbsz_idc_system_end.service.ITaskService;
import com.example.hbsz_idc_system_end.utils.UserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务进度服务实现
 * <p>
 * 负责项目任务的全生命周期管理，包括任务的增删改查、进度填报、
 * 项目整体进度汇总以及关键节点提醒等能力。
 * <p>
 * 关键设计要点：
 * <ul>
 *   <li>写操作（新增/更新/删除/进度填报/进度审核）均标注 {@code @Transactional}，
 *       保证任务写入与项目进度同步在同一事务内，异常时整体回滚；</li>
 *   <li>进度填报采用审核流程：工程师提交后日志 reviewStatus=pending，
 *       项目经理审核通过才会回写 task.progress 并同步项目整体进度；</li>
 *   <li>任何影响任务进度的写操作完成后，均调用 {@link #syncProjectProgress(Long)}
 *       以原子方式回写项目整体进度，避免并发覆盖；</li>
 *   <li>批量查询场景（关键节点提醒）通过一次性批量查询项目名称规避 N+1 问题。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class TaskServiceImpl implements ITaskService {

    private final BizTaskMapper taskMapper;
    private final BizTaskProgressLogMapper progressLogMapper;
    private final BizProjectMapper projectMapper;
    private final ObjectMapper objectMapper;
    private final INotificationService notificationService;
    private final IProjectAuthorizationService projectAuthorizationService;

    public TaskServiceImpl(BizTaskMapper taskMapper, BizTaskProgressLogMapper progressLogMapper,
                           BizProjectMapper projectMapper, ObjectMapper objectMapper,
                           INotificationService notificationService,
                           IProjectAuthorizationService projectAuthorizationService) {
        this.taskMapper = taskMapper;
        this.progressLogMapper = progressLogMapper;
        this.projectMapper = projectMapper;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    /**
     * 查询项目任务列表
     * <p>
     * 阶段2：任务级数据隔离升级：
     * <ul>
     *   <li>调用 {@link IProjectAuthorizationService#canAccessProject} 校验项目访问权限；</li>
     *   <li>调用 {@link IProjectAuthorizationService#getCurrentUserVisibleTaskIds} 获取可见任务ID列表：
     *     <ul>
     *       <li>null：表示不限制（admin / PM / 高级工程师），查询项目所有任务；</li>
     *       <li>空列表：返回空数据（无权访问该项目）；</li>
     *       <li>非空列表：用 in(BizTask::getId, taskIds) 过滤，仅返回可见任务。</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @Override
    public List<TaskVO> listByProject(Long projectId) {
        // 校验项目访问权限
        if (!projectAuthorizationService.canAccessProject(projectId)) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权访问该项目");
        }
        // 获取可见任务ID列表
        List<Long> visibleTaskIds = projectAuthorizationService.getCurrentUserVisibleTaskIds(projectId);
        if (visibleTaskIds != null && visibleTaskIds.isEmpty()) {
            // 无可见任务，直接返回空列表
            return Collections.emptyList();
        }

        LambdaQueryWrapper<BizTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizTask::getProjectId, projectId);
        // 可见任务ID列表为 null 时不限制（admin / PM / 高级工程师）
        if (visibleTaskIds != null) {
            wrapper.in(BizTask::getId, visibleTaskIds);
        }
        wrapper.orderByAsc(BizTask::getSort).orderByAsc(BizTask::getStartDate);
        List<BizTask> tasks = taskMapper.selectList(wrapper);
        return tasks.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 查询任务详情
     */
    @Override
    public TaskVO getDetail(Long id) {
        BizTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND, "任务不存在");
        }
        // IDOR 防护：校验当前用户对该任务所属项目的访问权限
        if (!projectAuthorizationService.canAccessProject(task.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权访问该任务");
        }
        return convertToVO(task);
    }

    /**
     * 新增任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO create(Long projectId, TaskSaveDTO dto) {
        BizProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "项目不存在");
        }
        // IDOR 防护：校验当前用户对该项目的访问权限
        if (!projectAuthorizationService.canAccessProject(projectId)) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权为该项目创建任务");
        }

        BizTask task = new BizTask();
        task.setProjectId(projectId);
        task.setName(dto.getName());
        task.setParentId(dto.getParentId());
        task.setStartDate(dto.getStartDate());
        task.setEndDate(dto.getEndDate());
        // 数据校验：开始日期不能晚于结束日期，防止非法时间区间导致甘特图/查询异常
        if (dto.getStartDate() != null && dto.getEndDate() != null
                && dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        task.setProgress(dto.getProgress() != null ? dto.getProgress() : 0);
        task.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "未开始");
        task.setAssigneeName(dto.getAssignee());
        // 设置任务负责人用户ID：用于「我的任务」列表、到期通知、归档通知等场景。
        // 若前端未传 assigneeId，则 assigneeId 为 null，相关功能将无法匹配到具体用户。
        task.setAssigneeId(dto.getAssigneeId());
        task.setIsMilestone(Boolean.TRUE.equals(dto.getIsMilestone()) ? 1 : 0);
        task.setIsKeyTask(Boolean.TRUE.equals(dto.getIsKey()) ? 1 : 0);
        task.setSort(dto.getSort() != null ? dto.getSort() : 0);
        task.setDescription(dto.getDescription());
        task.setRemark(dto.getRemark());
        task.setCreatedBy(UserContext.getUserId());

        taskMapper.insert(task);
        log.info("新增任务成功: 项目{} - {}", projectId, task.getName());
        syncProjectProgress(projectId);
        // 通知被分配人：新任务分配（补全信息流断点，工人无需轮询任务列表）
        notifyTaskAssigned(task, project.getName(), "新任务分配");
        return convertToVO(task);
    }

    /**
     * 更新任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO update(Long id, TaskSaveDTO dto) {
        BizTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND, "任务不存在");
        }
        // IDOR 防护：校验当前用户对该任务所属项目的访问权限
        if (!projectAuthorizationService.canAccessProject(task.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权修改该任务");
        }
        // 记录变更前负责人，用于判断是否需要发送转派通知
        Long previousAssigneeId = task.getAssigneeId();

        if (StringUtils.hasText(dto.getName())) task.setName(dto.getName());
        if (dto.getParentId() != null) task.setParentId(dto.getParentId());
        if (dto.getStartDate() != null) task.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) task.setEndDate(dto.getEndDate());
        // 数据校验：开始日期不能晚于结束日期，防止非法时间区间导致甘特图/查询异常
        // 注意：更新时 dto 可能只传其中一个日期，需结合实体已设置的日期做交叉校验
        if (dto.getStartDate() != null && dto.getEndDate() != null
                && dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (dto.getProgress() != null) task.setProgress(dto.getProgress());
        if (StringUtils.hasText(dto.getStatus())) task.setStatus(dto.getStatus());
        if (dto.getAssignee() != null) task.setAssigneeName(dto.getAssignee());
        // 更新任务负责人用户ID：前端切换负责人时需传入新的 assigneeId
        if (dto.getAssigneeId() != null) task.setAssigneeId(dto.getAssigneeId());
        if (dto.getIsMilestone() != null) task.setIsMilestone(Boolean.TRUE.equals(dto.getIsMilestone()) ? 1 : 0);
        if (dto.getIsKey() != null) task.setIsKeyTask(Boolean.TRUE.equals(dto.getIsKey()) ? 1 : 0);
        if (dto.getSort() != null) task.setSort(dto.getSort());
        if (dto.getDescription() != null) task.setDescription(dto.getDescription());
        if (dto.getRemark() != null) task.setRemark(dto.getRemark());

        taskMapper.updateById(task);
        log.info("更新任务成功: {}", id);
        syncProjectProgress(task.getProjectId());
        // 负责人发生变更时通知新负责人（转派场景）
        if (dto.getAssigneeId() != null && !dto.getAssigneeId().equals(previousAssigneeId)) {
            BizProject project = projectMapper.selectById(task.getProjectId());
            notifyTaskAssigned(task, project != null ? project.getName() : null, "任务转派给你");
        }
        return convertToVO(task);
    }

    /**
     * 删除任务
     * <p>
     * 删除前校验是否存在子任务，存在则拒绝删除。
     * DB ON DELETE CASCADE 会自动清理 biz_task_progress_log 关联记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND, "任务不存在");
        }
        // IDOR 防护：删除是高危操作，校验当前用户对该任务所属项目的访问权限
        if (!projectAuthorizationService.canAccessProject(task.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权删除该任务");
        }
        // 校验是否存在子任务
        LambdaQueryWrapper<BizTask> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(BizTask::getParentId, id);
        long childCount = taskMapper.selectCount(childWrapper);
        if (childCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该任务下存在子任务，无法删除");
        }
        taskMapper.deleteById(id);
        log.info("删除任务成功: {}", id);
        syncProjectProgress(task.getProjectId());
    }

    /**
     * 提交进度填报（工程师发起，进入项目经理审核流程）
     * <p>
     * 核心业务规则：
     * <ul>
     *   <li>不直接更新 task.progress，而是创建一条 pending 状态的进度日志；</li>
     *   <li>保存施工图片URL列表（JSON 数组）；</li>
     *   <li>记录 previousProgress 以备审核通过后比对变更；</li>
     *   <li>审核通过后由 {@link #reviewProgress(Long, ProgressReviewDTO)} 负责回写 task.progress。</li>
     * </ul>
     *
     * @param id  任务ID
     * @param dto 进度填报参数（进度百分比、施工图片等）
     * @return 进度日志 VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProgressLogVO reportProgress(Long id, ProgressReportDTO dto) {
        BizTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND, "任务不存在");
        }
        // IDOR 防护：进度填报是写操作，校验当前用户对该任务所属项目的访问权限
        if (!projectAuthorizationService.canAccessProject(task.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权为该任务填报进度");
        }


        Integer previousProgress = task.getProgress() != null ? task.getProgress() : 0;

        BizTaskProgressLog progressLog = new BizTaskProgressLog();
        progressLog.setTaskId(id);
        progressLog.setProjectId(task.getProjectId());
        progressLog.setProgress(dto.getProgress());
        progressLog.setPreviousProgress(previousProgress);
        progressLog.setRemark(dto.getRemark());
        progressLog.setReporterId(UserContext.getUserId());
        progressLog.setReporterName(UserContext.getUsername());
        progressLog.setReportTime(LocalDateTime.now());
        // 审核流程字段
        progressLog.setReviewStatus("pending");
        progressLog.setImages(serializeImages(dto.getImages()));
        progressLog.setActualStartDate(dto.getActualStartDate());
        progressLog.setActualEndDate(dto.getActualEndDate());

        progressLogMapper.insert(progressLog);
        log.info("[任务服务] 工程师提交进度填报待审核: taskId={}, logId={}, progress={}%",
                id, progressLog.getId(), dto.getProgress());

        // 通知项目经理审核（静默投递，失败不影响主流程）
        notifyProgressSubmitted(task, progressLog, dto.getProgress());

        return convertLogToVO(progressLog, task.getName(), null);
    }

    /**
     * 审核进度填报（项目经理审核工程师提交的进度日志）
     * <p>
     * 业务规则：
     * <ul>
     *   <li>仅 pending 状态的日志可被审核，避免重复审核；</li>
     *   <li>审核通过：将日志标记为 approved，并把进度值回写到 task.progress，
     *       同时按进度联动任务状态（100%→已完成，>0→进行中）并同步项目整体进度；</li>
     *   <li>审核驳回：仅将日志标记为 rejected，不影响任务实际进度与项目整体进度。</li>
     * </ul>
     *
     * @param logId 进度日志ID
     * @param dto   审核参数（是否通过、审核意见）
     * @return 审核后的进度日志 VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProgressLogVO reviewProgress(Long logId, ProgressReviewDTO dto) {
        Long reviewerId = UserContext.getUserId();
        String reviewerName = UserContext.getUsername();
        LocalDateTime requestTime = LocalDateTime.now();
        Boolean approve = dto.getApprove();
        log.info("[进度审核][开始] logId={}, approve={}, reviewerId={}, reviewerName={}, remark={}, requestTime={}",
                logId, approve, reviewerId, reviewerName, dto.getRemark(), requestTime);

        BizTaskProgressLog progressLog = progressLogMapper.selectById(logId);
        if (progressLog == null) {
            log.warn("[进度审核][失败] 进度日志不存在: logId={}, reviewerId={}, reviewerName={}, requestTime={}",
                    logId, reviewerId, reviewerName, requestTime);
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "进度日志不存在");
        }
        // IDOR 防护：审核是高危操作，校验当前用户对该进度日志所属项目的访问权限
        if (!projectAuthorizationService.canAccessProject(progressLog.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权审核该进度日志");
        }
        log.info("[进度审核][加载] logId={}, taskId={}, projectId={}, reporterId={}, reporterName={}, " +
                        "原reviewStatus={}, 原previousProgress={}, 原progress={}, reportTime={}",
                logId, progressLog.getTaskId(), progressLog.getProjectId(),
                progressLog.getReporterId(), progressLog.getReporterName(),
                progressLog.getReviewStatus(), progressLog.getPreviousProgress(),
                progressLog.getProgress(), progressLog.getReportTime());

        if (!"pending".equals(progressLog.getReviewStatus())) {
            log.warn("[进度审核][拒绝] 重复审核: logId={}, 当前reviewStatus={}, reviewerId={}, reviewerName={}, requestTime={}",
                    logId, progressLog.getReviewStatus(), reviewerId, reviewerName, requestTime);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该进度填报已审核，不可重复审核");
        }

        // 标记审核结果
        String reviewStatus = Boolean.TRUE.equals(approve) ? "approved" : "rejected";
        // 修复并发竞态：原实现为 selectById 检查 pending → updateById 写入审核结果，
        // 两个 PM 并发审核同一条日志时，两个线程都通过 pending 检查，导致任务进度被回写两次、
        // 通知被发送两次。
        // 改为 CAS 式原子 UPDATE：带 WHERE review_status='pending' 条件，
        // 利用 MySQL 行锁保证只有一个线程能成功更新（affectedRows=1），
        // 另一线程 affectedRows=0 据此抛出"已审核"异常，避免执行后续业务逻辑。
        int casRows = progressLogMapper.updateReviewStatusCAS(
                logId, reviewStatus, reviewerId, reviewerName, requestTime, dto.getRemark());
        if (casRows != 1) {
            log.warn("[进度审核][CAS失败] 重复审核: logId={}, casRows={}, reviewerId={}, reviewerName={}, requestTime={}",
                    logId, casRows, reviewerId, reviewerName, requestTime);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该进度填报已审核，不可重复审核");
        }
        // CAS 成功后同步实体字段，供后续业务逻辑（通知、日志、VO 转换）使用
        progressLog.setReviewStatus(reviewStatus);
        progressLog.setReviewerId(reviewerId);
        progressLog.setReviewerName(reviewerName);
        progressLog.setReviewTime(requestTime);
        progressLog.setReviewRemark(dto.getRemark());

        // 仅审核通过才回写任务进度
        if ("approved".equals(reviewStatus)) {
            BizTask task = taskMapper.selectById(progressLog.getTaskId());
            if (task == null) {
                log.error("[进度审核][异常] 任务不存在: logId={}, taskId={}, reviewerId={}, reviewerName={}, requestTime={}",
                        logId, progressLog.getTaskId(), reviewerId, reviewerName, requestTime);
                throw new BusinessException(ResultCode.TASK_NOT_FOUND, "任务不存在");
            }
            Integer oldTaskProgress = task.getProgress();
            String oldTaskStatus = task.getStatus();
            Integer newProgress = progressLog.getProgress();
            task.setProgress(newProgress);
            if (newProgress != null) {
                if (newProgress == 100) {
                    task.setStatus("已完成");
                } else if (newProgress > 0) {
                    task.setStatus("进行中");
                }
            }
            // 同步备注（仅当填报备注非空时覆盖，避免空覆盖已有备注）
            if (StringUtils.hasText(progressLog.getRemark())) {
                task.setRemark(progressLog.getRemark());
            }
            // 同步实际开工/结束时间到任务
            if (progressLog.getActualStartDate() != null) {
                task.setActualStartDate(progressLog.getActualStartDate());
            }
            if (progressLog.getActualEndDate() != null) {
                task.setActualEndDate(progressLog.getActualEndDate());
            }
            int taskRows = taskMapper.updateById(task);
            log.info("[进度审核][更新任务] logId={}, taskId={}, oldProgress={}%, newProgress={}%, oldStatus={}, newStatus={}, affectedRows={}, reviewerId={}, reviewerName={}, requestTime={}",
                    logId, task.getId(), oldTaskProgress, newProgress, oldTaskStatus, task.getStatus(),
                    taskRows, reviewerId, reviewerName, requestTime);
            if (taskRows != 1) {
                log.error("[进度审核][异常] 任务更新影响行数异常: expected=1, actual={}, logId={}, taskId={}, reviewerId={}, reviewerName={}, requestTime={}",
                        taskRows, logId, task.getId(), reviewerId, reviewerName, requestTime);
            }

            // 同步项目整体进度（任务平均进度）
            BizProject projectBeforeSync = projectMapper.selectById(task.getProjectId());
            Integer oldProjectProgress = projectBeforeSync != null ? projectBeforeSync.getProgress() : null;
            syncProjectProgress(task.getProjectId());
            BizProject projectAfterSync = projectMapper.selectById(task.getProjectId());
            // 同步实际开工/结束时间到项目（仅当项目对应字段为空时填充，避免覆盖更早的记录）
            if (projectAfterSync != null) {
                boolean projectChanged = false;
                if (projectAfterSync.getActualStartDate() == null && progressLog.getActualStartDate() != null) {
                    projectAfterSync.setActualStartDate(progressLog.getActualStartDate());
                    projectChanged = true;
                }
                if (progressLog.getActualEndDate() != null) {
                    projectAfterSync.setActualEndDate(progressLog.getActualEndDate());
                    projectChanged = true;
                }
                if (projectChanged) {
                    projectMapper.updateById(projectAfterSync);
                }
            }
            Integer newProjectProgress = projectAfterSync != null ? projectAfterSync.getProgress() : null;
            log.info("[进度审核][项目进度同步] logId={}, projectId={}, oldProjectProgress={}%, newProjectProgress={}%, reviewerId={}, reviewerName={}, requestTime={}",
                    logId, task.getProjectId(), oldProjectProgress, newProjectProgress,
                    reviewerId, reviewerName, requestTime);
        } else {
            log.info("[进度审核][驳回] logId={}, taskId={}, reviewerId={}, reviewerName={}, reviewRemark={}, requestTime={}",
                    logId, progressLog.getTaskId(), reviewerId, reviewerName, dto.getRemark(), requestTime);
        }

        // 审核状态已通过 CAS 原子更新写入数据库（updateReviewStatusCAS），
        // 无需再次调用 updateById，避免重复 SQL 执行
        log.info("[进度审核][CAS已更新日志] logId={}, newReviewStatus={}, reviewerId={}, reviewerName={}, reviewTime={}, requestTime={}",
                logId, reviewStatus, reviewerId, reviewerName, progressLog.getReviewTime(), requestTime);
        ProgressLogVO vo = convertLogToVO(progressLog, null, null);
        log.info("[进度审核][完成] logId={}, finalReviewStatus={}, taskId={}, projectId={}, progress={}%, " +
                        "reviewerId={}, reviewerName={}, reviewTime={}, voId={}, requestTime={}",
                logId, vo.getReviewStatus(), vo.getTaskId(), vo.getProjectId(), vo.getProgress(),
                vo.getReviewerId(), vo.getReviewerName(), vo.getReviewTime(), vo.getId(), requestTime);

        // 通知填报人审核结果（静默投递，失败不影响主流程）
        notifyProgressReviewed(progressLog, approve, reviewerName, dto.getRemark());

        return vo;
    }

    /**
     * 查询待审核进度日志列表
     * <p>
     * 默认按填报时间倒序返回 review_status='pending' 的日志。
     * 为减少 N+1 查询，先批量查询日志涉及的 task 和 project 名称。
     *
     * @param projectId 项目ID，为 null 时返回全部待审核
     * @return 待审核进度日志列表
     */
    @Override
    public List<ProgressLogVO> listPendingProgress(Long projectId) {
        // IDOR 防护：校验当前用户对该项目的访问权限
        if (projectId != null && !projectAuthorizationService.canAccessProject(projectId)) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权查看该项目的待审核日志");
        }
        LambdaQueryWrapper<BizTaskProgressLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizTaskProgressLog::getReviewStatus, "pending");
        if (projectId != null) {
            wrapper.eq(BizTaskProgressLog::getProjectId, projectId);
        }
        wrapper.orderByDesc(BizTaskProgressLog::getReportTime);
        List<BizTaskProgressLog> logs = progressLogMapper.selectList(wrapper);

        if (logs.isEmpty()) {
            return Collections.emptyList();
        }
        return enrichLogVOs(logs);
    }

    /**
     * 分页查询待审核进度日志
     * <p>
     * 与 {@link #listPendingProgress(Long)} 查询条件一致（review_status='pending'，可选项目筛选），
     * 在此基础上按 pageNum/pageSize 分页。排序在 reportTime 倒序之外追加 id 倒序兜底：
     * reportTime 相同的记录若不追加唯一字段排序，翻页时可能因数据库返回顺序不稳定而重复/漏记录。
     *
     * @param projectId 项目ID，为 null 时返回全部待审核
     * @param query    分页参数（页码从 1 开始，每页条数由调用方校正为 1~100）
     * @return 待审核进度日志分页结果（total/pages 与筛选条件一致；pageNum 超出 pages 时 records 为空）
     */
    @Override
    public PageResult<ProgressLogVO> pagePendingProgress(Long projectId, PageQuery query) {
        // IDOR 防护：校验当前用户对该项目的访问权限（与 listPendingProgress 保持一致）
        if (projectId != null && !projectAuthorizationService.canAccessProject(projectId)) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权查看该项目的待审核日志");
        }
        LambdaQueryWrapper<BizTaskProgressLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizTaskProgressLog::getReviewStatus, "pending");
        if (projectId != null) {
            wrapper.eq(BizTaskProgressLog::getProjectId, projectId);
        }
        wrapper.orderByDesc(BizTaskProgressLog::getReportTime)
                .orderByDesc(BizTaskProgressLog::getId);

        Page<BizTaskProgressLog> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<BizTaskProgressLog> result = progressLogMapper.selectPage(page, wrapper);

        List<ProgressLogVO> voList = result.getRecords().isEmpty()
                ? Collections.emptyList()
                : enrichLogVOs(result.getRecords());
        return new PageResult<>(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    /**
     * 查询指定任务的进度日志列表
     * <p>
     * 阶段2：任务级数据隔离升级 - 工程师角色仅能查看自己填报的进度日志，
     * 其他角色（admin / PM / 高级工程师等）可查看任务下全部进度日志。
     *
     * @param taskId 任务ID
     * @return 进度日志列表（按填报时间倒序）
     */
    @Override
    public List<ProgressLogVO> listProgressLogs(Long taskId) {
        BizTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND, "任务不存在");
        }
        // IDOR 防护：校验当前用户对该任务所属项目的访问权限
        if (!projectAuthorizationService.canAccessProject(task.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权查看该任务的进度日志");
        }
        LambdaQueryWrapper<BizTaskProgressLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizTaskProgressLog::getTaskId, taskId);
        // 工程师：仅查看自己填报的进度日志
        Long currentUserId = UserContext.getUserId();
        List<Long> visibleTaskIds = projectAuthorizationService.getCurrentUserVisibleTaskIds(task.getProjectId());
        // 【修复H3】当 visibleTaskIds 不为 null 但为空列表时，说明当前用户（工程师）无权访问该任务的任何进度日志，
        // 必须直接返回空列表，避免因 !visibleTaskIds.isEmpty() 为 false 而跳过 reporterId 过滤，
        // 导致工程师越权查看他人进度日志的安全漏洞。
        if (visibleTaskIds != null) {
            if (visibleTaskIds.isEmpty()) {
                // 工程师无权访问该任务，直接返回空列表
                return Collections.emptyList();
            }
            // 工程师角色，仅返回自己填报的日志
            wrapper.eq(BizTaskProgressLog::getReporterId, currentUserId);
        }
        wrapper.orderByDesc(BizTaskProgressLog::getReportTime);
        List<BizTaskProgressLog> logs = progressLogMapper.selectList(wrapper);

        if (logs.isEmpty()) {
            return Collections.emptyList();
        }
        return enrichLogVOs(logs);
    }

    /**
     * 查询任务所属项目ID（用于任务级数据隔离判断）
     *
     * @param taskId 任务ID
     * @return 项目ID，任务不存在时返回 null
     */
    private Long getTaskProjectId(Long taskId) {
        BizTask task = taskMapper.selectById(taskId);
        return task != null ? task.getProjectId() : null;
    }

    /**
     * 批量补全进度日志 VO 涉及的任务名与项目名，规避 N+1 查询
     */
    private List<ProgressLogVO> enrichLogVOs(List<BizTaskProgressLog> logs) {
        // 批量查询任务名
        java.util.Set<Long> taskIds = logs.stream()
                .map(BizTaskProgressLog::getTaskId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        java.util.Map<Long, String> taskNameMap = new java.util.HashMap<>();
        if (!taskIds.isEmpty()) {
            List<BizTask> tasks = taskMapper.selectBatchIds(taskIds);
            for (BizTask t : tasks) {
                taskNameMap.put(t.getId(), t.getName());
            }
        }

        // 批量查询项目名
        java.util.Set<Long> projectIds = logs.stream()
                .map(BizTaskProgressLog::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        java.util.Map<Long, String> projectNameMap = new java.util.HashMap<>();
        if (!projectIds.isEmpty()) {
            List<BizProject> projects = projectMapper.selectBatchIds(projectIds);
            for (BizProject p : projects) {
                projectNameMap.put(p.getId(), p.getName());
            }
        }

        return logs.stream()
                .map(l -> convertLogToVO(l,
                        taskNameMap.get(l.getTaskId()),
                        projectNameMap.get(l.getProjectId())))
                .collect(Collectors.toList());
    }

    /**
     * 项目整体进度
     * <p>
     * 汇总项目基本信息与其下全部任务，并返回整体进度。
     * <p>
     * 整体进度直接读取 {@code biz_project.progress} 字段：
     * 该字段由 {@link #syncProjectProgress(Long)} 在任务 CRUD 后通过
     * {@code AVG(progress) GROUP BY project_id} 自动维护，与原 Java 端循环计算
     * 结果一致，但避免了将全部任务拉入内存后重复计算的开销。
     *
     * @param projectId 项目ID
     * @return 项目进度视图对象（含任务列表与整体进度）
     */
    @Override
    public ProjectProgressVO getProjectProgress(Long projectId) {
        BizProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "项目不存在");
        }

        ProjectProgressVO vo = new ProjectProgressVO();
        vo.setProjectId(String.valueOf(projectId));
        vo.setProjectName(project.getName());
        vo.setStartDate(project.getStartDate());
        vo.setEndDate(project.getEndDate());

        List<TaskVO> tasks = listByProject(projectId);
        vo.setTasks(tasks);

        // 直接读取 syncProjectProgress 维护的进度字段，避免 Java 端重复循环计算
        vo.setOverallProgress(project.getProgress() != null ? project.getProgress() : 0);
        return vo;
    }

    /**
     * 项目关键节点提醒
     */
    @Override
    public List<TaskVO> getUpcomingKeyTasks(Long projectId) {
        // IDOR 防护：校验当前用户对该项目的访问权限
        if (!projectAuthorizationService.canAccessProject(projectId)) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权查看该项目的关键节点");
        }
        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);
        List<BizTask> tasks = taskMapper.selectUpcomingKeyTasks(projectId, today, nextWeek);
        return tasks.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 全部关键节点提醒
     * <p>
     * 查询未来 7 天内到期的全部关键任务（跨项目）。
     * <p>
     * 性能与限制：
     * <ul>
     *   <li>结果数量上限 {@code MAX_RETURN_SIZE = 100}，超出则截断，避免数据量过大拖慢前端渲染；</li>
     *   <li>先收集去重后的项目ID，再通过 {@code selectBatchIds} 一次性批量查询项目名称，
     *       规避逐条查询导致的 N+1 问题。</li>
     * </ul>
     *
     * @return 关键节点任务列表（assignee 字段已拼接"项目名 - 负责人"）
     */
    @Override
    public List<TaskVO> getAllUpcomingKeyTasks() {
        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);
        List<BizTask> tasks = taskMapper.selectAllUpcomingKeyTasks(today, nextWeek);

        // 限制返回数量，避免数据量过大影响前端渲染
        final int MAX_RETURN_SIZE = 100;
        if (tasks.size() > MAX_RETURN_SIZE) {
            log.warn("[任务服务] 关键节点提醒数量超过上限({}), 截断为 {}", tasks.size(), MAX_RETURN_SIZE);
            tasks = tasks.subList(0, MAX_RETURN_SIZE);
        }

        // 批量查询项目名称（解决 N+1 查询问题）
        java.util.Set<Long> projectIds = tasks.stream()
                .map(BizTask::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        java.util.Map<Long, String> projectNameMap = new java.util.HashMap<>();
        if (!projectIds.isEmpty()) {
            List<BizProject> projects = projectMapper.selectBatchIds(projectIds);
            for (BizProject project : projects) {
                projectNameMap.put(project.getId(), project.getName());
            }
        }

        return tasks.stream().map(t -> {
            TaskVO vo = convertToVO(t);
            // 从批量查询的 map 中获取项目名称
            String projectName = projectNameMap.get(t.getProjectId());
            if (projectName != null) {
                vo.setAssignee(projectName + " - " + (t.getAssigneeName() != null ? t.getAssigneeName() : ""));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 实体转VO
     */
    private TaskVO convertToVO(BizTask task) {
        TaskVO vo = new TaskVO();
        vo.setId(String.valueOf(task.getId()));
        vo.setProjectId(String.valueOf(task.getProjectId()));
        vo.setName(task.getName());
        vo.setStartDate(task.getStartDate());
        vo.setEndDate(task.getEndDate());
        vo.setActualStartDate(task.getActualStartDate());
        vo.setActualEndDate(task.getActualEndDate());
        vo.setProgress(task.getProgress());
        vo.setStatus(task.getStatus());
        vo.setAssignee(task.getAssigneeName());
        vo.setAssigneeId(task.getAssigneeId());
        vo.setIsMilestone(task.getIsMilestone() != null && task.getIsMilestone() == 1);
        vo.setIsKey(task.getIsKeyTask() != null && task.getIsKeyTask() == 1);
        vo.setParentId(task.getParentId() != null ? String.valueOf(task.getParentId()) : null);
        vo.setDescription(task.getDescription());
        vo.setRemark(task.getRemark());
        return vo;
    }

    /**
     * 进度日志实体转VO
     * <p>
     * images 字段在数据库中以 JSON 数组字符串存储，转 VO 时解析为 List<String>。
     * taskName / projectName 允许为空，由调用方按需补全。
     *
     * @param log         日志实体
     * @param taskName    任务名（可为 null）
     * @param projectName 项目名（可为 null）
     */
    private ProgressLogVO convertLogToVO(BizTaskProgressLog log, String taskName, String projectName) {
        ProgressLogVO vo = new ProgressLogVO();
        vo.setId(String.valueOf(log.getId()));
        vo.setTaskId(String.valueOf(log.getTaskId()));
        vo.setTaskName(taskName);
        vo.setProjectId(String.valueOf(log.getProjectId()));
        vo.setProjectName(projectName);
        vo.setProgress(log.getProgress());
        vo.setPreviousProgress(log.getPreviousProgress());
        vo.setRemark(log.getRemark());
        vo.setImages(deserializeImages(log.getImages()));
        vo.setActualStartDate(log.getActualStartDate());
        vo.setActualEndDate(log.getActualEndDate());
        vo.setReviewStatus(log.getReviewStatus());
        vo.setReviewerId(log.getReviewerId() != null ? String.valueOf(log.getReviewerId()) : null);
        vo.setReviewerName(log.getReviewerName());
        vo.setReviewTime(log.getReviewTime());
        vo.setReviewRemark(log.getReviewRemark());
        vo.setReporterId(log.getReporterId() != null ? String.valueOf(log.getReporterId()) : null);
        vo.setReporterName(log.getReporterName());
        vo.setReportTime(log.getReportTime());
        return vo;
    }

    /**
     * 将图片URL列表序列化为 JSON 字符串存储
     */
    private String serializeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException e) {
            log.warn("[任务服务] 图片URL列表序列化失败，将丢弃 images 字段: {}", images, e);
            return null;
        }
    }

    /**
     * 将数据库中以 JSON 字符串存储的 images 字段反序列化为 List<String>
     */
    private List<String> deserializeImages(String images) {
        if (!StringUtils.hasText(images)) {
            return Collections.emptyList();
        }
        try {
            List<String> list = objectMapper.readValue(images, new TypeReference<List<String>>() {});
            return list != null ? list : Collections.emptyList();
        } catch (JsonProcessingException e) {
            log.warn("[任务服务] 图片URL列表反序列化失败，返回空列表: {}", images, e);
            return Collections.emptyList();
        }
    }

    /**
     * 同步更新项目整体进度（任务平均进度）
     * 在任务新增/更新/删除/进度填报后调用，确保 biz_project.progress 与任务进度保持一致
     * <p>
     * 优化说明：
     * 原实现为先 selectList 再 updateById，存在两个问题：
     * 1. 并发覆盖：两个用户同时填报同一项目不同任务时，后提交者会用基于旧数据的平均值覆盖前者的更新
     * 2. 多一次查询：每次同步都需 selectList + updateById 两次 DB 往返
     * 改为单条 UPDATE 子查询后，整个操作在 MySQL 行锁下原子完成，既消除并发覆盖，又减少一次 DB 往返。
     */
    private void syncProjectProgress(Long projectId) {
        if (projectId == null) return;
        taskMapper.updateProjectProgressByTaskAvg(projectId);
        log.info("[任务服务] 同步项目进度: projectId={}", projectId);
    }

    /**
     * 通知任务被分配人（新分配/转派）。
     * <p>
     * 任务创建或负责人变更时，向新负责人投递 task 类型通知。
     * 分配人自己是负责人时不重复通知；静默投递——失败不影响主流程。
     *
     * @param task        任务实体
     * @param projectName 项目名称（可为 null）
     * @param sceneTitle  通知标题（区分新分配/转派）
     */
    private void notifyTaskAssigned(BizTask task, String projectName, String sceneTitle) {
        try {
            if (task.getAssigneeId() == null || task.getAssigneeId().equals(UserContext.getUserId())) {
                return;
            }
            NotificationSendDTO notif = new NotificationSendDTO();
            notif.setUserId(task.getAssigneeId());
            notif.setTitle(sceneTitle);
            notif.setContent((StringUtils.hasText(projectName) ? "项目「" + projectName + "」" : "")
                    + "任务「" + task.getName() + "」已分配给你，分配人：" + UserContext.getUsername()
                    + (task.getEndDate() != null ? "，截止：" + task.getEndDate() : ""));
            notif.setNotifyType("task");
            notif.setBizType("task");
            notif.setBizId(task.getId());
            notif.setPriority(2);
            notificationService.sendNotification(notif);
        } catch (Exception e) {
            log.warn("[任务服务] 通知被分配人失败: taskId={}, error={}", task.getId(), e.getMessage());
        }
    }

    /**
     * 通知项目经理有新的进度填报待审核。
     * <p>
     * 查询任务所属项目的项目经理（managerId），投递 progress 类型通知。
     * 静默投递——通知失败不影响进度填报主流程。
     *
     * @param task        任务实体
     * @param progressLog 进度日志
     * @param progress    本次填报进度值
     */
    private void notifyProgressSubmitted(BizTask task, BizTaskProgressLog progressLog, Integer progress) {
        try {
            BizProject project = projectMapper.selectById(task.getProjectId());
            if (project == null || project.getManagerId() == null) {
                // 项目未关联项目经理用户：进度审核通知无法投递，WARN 暴露数据缺口（不再静默丢失）
                log.warn("[任务服务] 项目未关联项目经理用户，进度审核通知跳过: projectId={}, taskId={}, managerName={}",
                        task.getProjectId(), task.getId(),
                        project != null ? project.getManagerName() : "项目不存在");
                return;
            }
            NotificationSendDTO notif = new NotificationSendDTO();
            notif.setUserId(project.getManagerId());
            notif.setTitle("进度填报待审核");
            notif.setContent("任务「" + task.getName() + "」有新的进度填报待审核，进度："
                    + (progress != null ? progress : 0) + "%，填报人：" + progressLog.getReporterName());
            notif.setNotifyType("progress");
            notif.setBizType("task");
            notif.setBizId(task.getId());
            notif.setPriority(2);
            notificationService.sendNotification(notif);
        } catch (Exception e) {
            log.warn("[任务服务] 通知项目经理审核失败: taskId={}, error={}", task.getId(), e.getMessage());
        }
    }

    /**
     * 通知填报人审核结果。
     * <p>
     * 向进度日志的填报人（reporterId）投递 progress 类型通知，
     * 告知审核通过或驳回的结果。静默投递。
     *
     * @param progressLog 进度日志
     * @param approve     是否通过
     * @param reviewerName 审核人姓名
     * @param reviewRemark 审核意见
     */
    private void notifyProgressReviewed(BizTaskProgressLog progressLog, Boolean approve,
                                        String reviewerName, String reviewRemark) {
        try {
            if (progressLog.getReporterId() == null) {
                return;
            }
            NotificationSendDTO notif = new NotificationSendDTO();
            notif.setUserId(progressLog.getReporterId());
            notif.setTitle(Boolean.TRUE.equals(approve) ? "进度审核通过" : "进度审核驳回");
            notif.setContent("任务进度填报已" + (Boolean.TRUE.equals(approve) ? "通过" : "驳回")
                    + "，审核人：" + reviewerName
                    + (StringUtils.hasText(reviewRemark) ? "，意见：" + reviewRemark : ""));
            notif.setNotifyType("progress");
            notif.setBizType("task");
            notif.setBizId(progressLog.getTaskId());
            notif.setPriority(Boolean.TRUE.equals(approve) ? 1 : 2);
            notificationService.sendNotification(notif);
        } catch (Exception e) {
            log.warn("[任务服务] 通知填报人审核结果失败: logId={}, error={}",
                    progressLog.getId(), e.getMessage());
        }
    }

}

