package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.AssignmentQueryDTO;
import com.example.hbsz_idc_system_end.dto.AssignmentVO;
import com.example.hbsz_idc_system_end.dto.AuthRequestApproveDTO;
import com.example.hbsz_idc_system_end.dto.AuthRequestCreateDTO;
import com.example.hbsz_idc_system_end.dto.AuthRequestQueryDTO;
import com.example.hbsz_idc_system_end.dto.AuthRequestVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizProjectAssignment;
import com.example.hbsz_idc_system_end.entity.BizProjectAuthRequest;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.BizProjectAssignmentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectAuthRequestMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.service.IProjectAuthService;
import com.example.hbsz_idc_system_end.service.ProjectTransferLogHelper;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 项目授权申请与授权管理服务实现
 * <p>
 * 阶段3：项目授权规范化。负责项目授权申请的创建、审批流转，
 * 以及授权关系（biz_project_assignment）的查询、撤销、延期等管理操作。
 * <p>
 * 关键设计要点：
 * <ul>
 *   <li>所有写操作均标注 {@code @Transactional(rollbackFor = Exception.class)}，
 *       保证授权记录与审计日志写入在同一事务内，异常时整体回滚；</li>
 *   <li>所有写操作完成后均写入 {@link BizProjectTransferLog} 审计日志，
 *       action 字段使用 auth_request/approve/reject/extend 等新值；</li>
 *   <li>审批通过时自动创建 {@link BizProjectAssignment} 记录，
 *       含 permissionScope/expireAt 字段；</li>
 *   <li>撤销授权时设置 is_active=0、revoked_at、revoke_reason，写审计日志；</li>
 *   <li>延期授权仅更新 expire_at 字段。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class ProjectAuthServiceImpl implements IProjectAuthService {

    /** 默认授权范围：读写 */
    private static final String DEFAULT_SCOPE = "read_write";

    /** 全量拉取翻页硬上限（防御异常数据导致无限翻页，超限告警截断） */
    private static final int MAX_FETCH_PAGES = 50;

    private final BizProjectAuthRequestMapper authRequestMapper;
    private final BizProjectAssignmentMapper assignmentMapper;
    private final BizProjectMapper projectMapper;
    private final SysUserMapper userMapper;
    private final IPermissionService permissionService;
    private final ProjectTransferLogHelper transferLogHelper;

    public ProjectAuthServiceImpl(BizProjectAuthRequestMapper authRequestMapper,
                                  BizProjectAssignmentMapper assignmentMapper,
                                  BizProjectMapper projectMapper,
                                  SysUserMapper userMapper,
                                  IPermissionService permissionService,
                                  ProjectTransferLogHelper transferLogHelper) {
        this.authRequestMapper = authRequestMapper;
        this.assignmentMapper = assignmentMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.permissionService = permissionService;
        this.transferLogHelper = transferLogHelper;
    }

    /* ====== 公开方法 ====== */

    /**
     * 创建项目授权申请。
     * <p>
     * 校验：
     * <ul>
     *   <li>项目存在；</li>
     *   <li>目标工程师存在、启用且具备工程师角色；</li>
     *   <li>申请人有权申请（admin/PM 或项目本身负责人）。</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthRequestVO createAuthRequest(AuthRequestCreateDTO dto) {
        Long applicantId = UserContext.getUserId();
        if (applicantId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或认证已过期");
        }

        // 校验项目存在
        BizProject project = getProjectOrThrow(dto.getProjectId());
        // 校验目标工程师
        SysUser targetEngineer = validateEngineer(dto.getTargetEngineerId());
        // 校验申请人有权申请（admin/PM 或项目本身负责人）
        validateCanApplyForProject(project);

        // 校验是否已存在未处理的相同申请（幂等）
        int existsPending = authRequestMapper.selectCount(new LambdaQueryWrapper<BizProjectAuthRequest>()
                .eq(BizProjectAuthRequest::getProjectId, dto.getProjectId())
                .eq(BizProjectAuthRequest::getTargetEngineerId, dto.getTargetEngineerId())
                .eq(BizProjectAuthRequest::getStatus, "pending")).intValue();
        if (existsPending > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "该项目对该工程师已有待审批的授权申请");
        }

        BizProjectAuthRequest request = new BizProjectAuthRequest();
        request.setProjectId(dto.getProjectId());
        request.setApplicantId(applicantId);
        request.setTargetEngineerId(dto.getTargetEngineerId());
        request.setScope(StringUtils.hasText(dto.getScope()) ? dto.getScope() : DEFAULT_SCOPE);
        request.setExpireAt(dto.getExpireAt());
        request.setReason(dto.getReason());
        request.setStatus("pending");
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());

        // 并发兜底：上方 selectCount 为 check-then-act，并发请求可能同时通过检查；
        // 函数唯一索引 uk_auth_request_pending（pending 时取 project_target，其余 NULL）
        // 保证仅一条 pending，撞索引时将 DuplicateKeyException 转为与前置检查一致的业务异常
        try {
            authRequestMapper.insert(request);
        } catch (DuplicateKeyException e) {
            log.warn("[项目授权申请][创建] 并发提交触发唯一索引兜底：projectId={}, targetEngineerId={}, applicant={}",
                    dto.getProjectId(), dto.getTargetEngineerId(), applicantId);
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "该项目对该工程师已有待审批的授权申请");
        }
        log.info("[项目授权申请][创建] 申请ID={}, 项目={}, 目标工程师={}, 申请人={}",
                request.getId(), project.getName(), dto.getTargetEngineerId(), applicantId);

        // 写审计日志
        transferLogHelper.writeTransferLog(project, "auth_request", null, targetEngineer,
                applicantId, transferLogHelper.getUserName(applicantId), "创建授权申请：" + dto.getReason());

        return getAuthRequestVO(request.getId());
    }

    /**
     * 审批通过授权申请。
     * <p>
     * 业务流程：
     * <ol>
     *   <li>校验申请存在且为 pending 状态；</li>
     *   <li>更新申请状态为 approved，记录审批人与审批时间；</li>
     *   <li>创建 biz_project_assignment 记录（含 permissionScope/expireAt）；</li>
     *   <li>写审计日志。</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthRequestVO approveAuthRequest(Long id, AuthRequestApproveDTO dto) {
        if (!Boolean.TRUE.equals(dto.getApprove())) {
            // 若传入 approve=false 则走驳回流程
            return rejectAuthRequest(id, dto);
        }

        // IDOR 防护：审批是敏感操作，仅 admin 或项目经理可执行
        validateApproverRole();

        Long approverId = UserContext.getUserId();
        BizProjectAuthRequest request = getPendingRequestOrThrow(id);

        BizProject project = getProjectOrThrow(request.getProjectId());
        // 完整校验目标工程师（存在 + 启用 + engineer 角色）：申请创建后工程师可能被禁用/换岗，
        // 审批时统一兜底校验；同时覆盖 PM 直接授权复用分支（该分支不经过 createAuthRequest 的校验）
        SysUser targetEngineer = validateEngineer(request.getTargetEngineerId());

        // 校验是否已存在有效授权，避免重复授权
        int exists = assignmentMapper.countActiveAssignment(request.getProjectId(), request.getTargetEngineerId());
        if (exists > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "该工程师已被授权访问此项目");
        }

        // CAS 原子更新：仅当申请仍为 pending 时流转为 approved，受影响行数为 0 表示已被并发审批。
        // 与 TaskServiceImpl.reviewProgress / WorkloadServiceImpl.reviewRecords 的审核 CAS 范式一致，
        // 防止并发审批双双通过前置检查导致重复写 assignment
        int updated = authRequestMapper.update(null, new LambdaUpdateWrapper<BizProjectAuthRequest>()
                .eq(BizProjectAuthRequest::getId, id)
                .eq(BizProjectAuthRequest::getStatus, "pending")
                .set(BizProjectAuthRequest::getStatus, "approved")
                .set(BizProjectAuthRequest::getApproverId, approverId)
                .set(BizProjectAuthRequest::getApprovedAt, LocalDateTime.now())
                .set(BizProjectAuthRequest::getComment, dto.getComment())
                .set(BizProjectAuthRequest::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该授权申请已审核，不可重复审核");
        }

        // 创建授权记录
        BizProjectAssignment assignment = new BizProjectAssignment();
        assignment.setProjectId(request.getProjectId());
        assignment.setEngineerId(request.getTargetEngineerId());
        assignment.setAssignedBy(approverId);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setIsActive(1);
        assignment.setRemark("授权申请 #" + request.getId() + " 审批通过");
        assignment.setPermissionScope(StringUtils.hasText(request.getScope()) ? request.getScope() : DEFAULT_SCOPE);
        assignment.setExpireAt(request.getExpireAt());
        // 并发兜底：前置 countActiveAssignment 为 check-then-act，并发审批窗口可能
        // 双双通过；函数唯一索引 uk_project_engineer_active（仅活跃唯一，V20260821）
        // 在 DB 层拦截，撞索引时转为与前置检查一致的业务异常
        try {
            assignmentMapper.insert(assignment);
        } catch (DuplicateKeyException e) {
            log.warn("[项目授权申请][审批通过] 并发写 assignment 触发唯一索引兜底：projectId={}, engineerId={}",
                    request.getProjectId(), request.getTargetEngineerId());
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "该工程师已被授权访问此项目");
        }

        log.info("[项目授权申请][审批通过] 申请ID={}, 项目={}, 目标工程师={}, 审批人={}",
                id, project.getName(), targetEngineer.getName(), approverId);

        // 写审计日志
        transferLogHelper.writeTransferLog(project, "approve", null, targetEngineer,
                approverId, transferLogHelper.getUserName(approverId),
                "审批通过授权申请 #" + id + (StringUtils.hasText(dto.getComment()) ? "，意见：" + dto.getComment() : ""));

        return getAuthRequestVO(id);
    }

    /**
     * 驳回授权申请。
     * <p>
     * 仅更新申请状态为 rejected，不创建授权记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthRequestVO rejectAuthRequest(Long id, AuthRequestApproveDTO dto) {
        // IDOR 防护：驳回是敏感操作，仅 admin 或项目经理可执行
        validateApproverRole();

        Long approverId = UserContext.getUserId();
        BizProjectAuthRequest request = getPendingRequestOrThrow(id);

        BizProject project = getProjectOrThrow(request.getProjectId());

        // CAS 原子更新：仅当申请仍为 pending 时流转为 rejected，防止并发重复驳回/审批交叉
        int updated = authRequestMapper.update(null, new LambdaUpdateWrapper<BizProjectAuthRequest>()
                .eq(BizProjectAuthRequest::getId, id)
                .eq(BizProjectAuthRequest::getStatus, "pending")
                .set(BizProjectAuthRequest::getStatus, "rejected")
                .set(BizProjectAuthRequest::getApproverId, approverId)
                .set(BizProjectAuthRequest::getApprovedAt, LocalDateTime.now())
                .set(BizProjectAuthRequest::getComment, dto.getComment())
                .set(BizProjectAuthRequest::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该授权申请已审核，不可重复审核");
        }

        log.info("[项目授权申请][驳回] 申请ID={}, 项目={}, 审批人={}",
                id, project.getName(), approverId);

        // 写审计日志
        transferLogHelper.writeTransferLog(project, "reject", null, null,
                approverId, transferLogHelper.getUserName(approverId),
                "驳回授权申请 #" + id + (StringUtils.hasText(dto.getComment()) ? "，意见：" + dto.getComment() : ""));

        return getAuthRequestVO(id);
    }

    /**
     * 查询项目对目标工程师是否存在待审批的授权申请。
     * <p>
     * 授权双入口收敛：PM 直接授权（assignEngineer）委托本链路时，
     * 若工程师已提交过待审批申请则复用该申请直接审批，避免重复创建申请。
     */
    @Override
    public Long findPendingRequestId(Long projectId, Long targetEngineerId) {
        BizProjectAuthRequest request = authRequestMapper.selectOne(new LambdaQueryWrapper<BizProjectAuthRequest>()
                .eq(BizProjectAuthRequest::getProjectId, projectId)
                .eq(BizProjectAuthRequest::getTargetEngineerId, targetEngineerId)
                .eq(BizProjectAuthRequest::getStatus, "pending")
                .last("LIMIT 1"));
        return request != null ? request.getId() : null;
    }

    /**
     * 分页查询项目授权申请列表。
     */
    @Override
    public PageResult<AuthRequestVO> listAuthRequests(AuthRequestQueryDTO query) {
        // IDOR 防护：非 admin/PM 用户仅能查看自己提交的申请
        Set<String> roles = permissionService.getCurrentUserRoles();
        boolean isPrivileged = roles != null && (roles.contains(CommonConstant.ROLE_CODE_ADMIN)
                || roles.contains(CommonConstant.ROLE_CODE_PROJECT_MANAGER));
        if (!isPrivileged) {
            // 强制限定为当前用户的申请
            query.setApplicantId(UserContext.getUserId());
        }
        Page<AuthRequestVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<AuthRequestVO> result = authRequestMapper.selectAuthRequestPage(page, query);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getSize(), result.getCurrent());
    }

    /**
     * 查询当前用户申请的授权列表。
     */
    @Override
    public List<AuthRequestVO> getMyAuthRequests() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Collections.emptyList();
        }
        AuthRequestQueryDTO query = new AuthRequestQueryDTO();
        query.setApplicantId(userId);
        return fetchAllAuthRequestPages(query);
    }

    /**
     * 查询待当前用户审批的申请列表。
     * <p>
     * 当前用户为 admin 或 PM 时，返回所有 pending 状态的申请；
     * 其他角色返回空列表。
     */
    @Override
    public List<AuthRequestVO> getPendingAuthRequests() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Collections.emptyList();
        }
        Set<String> roles = permissionService.getCurrentUserRoles();
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        // 仅 admin / PM 可见待审批列表
        if (!roles.contains(CommonConstant.ROLE_CODE_ADMIN)
                && !roles.contains(CommonConstant.ROLE_CODE_PROJECT_MANAGER)) {
            return Collections.emptyList();
        }

        AuthRequestQueryDTO query = new AuthRequestQueryDTO();
        query.setStatus("pending");
        return fetchAllAuthRequestPages(query);
    }

    /**
     * 翻页拉取授权申请全量列表（review F3）。
     * <p>
     * 语义为"取全量"的场景（我的申请 / 待我审批）此前固定 pageSize=100，
     * 记录超过 100 条时被静默截断。改为按 total 循环翻页直到取满；
     * 硬上限 {@value #MAX_FETCH_PAGES} 页防御异常数据导致的无限循环，超限告警截断。
     */
    private List<AuthRequestVO> fetchAllAuthRequestPages(AuthRequestQueryDTO query) {
        List<AuthRequestVO> all = new java.util.ArrayList<>();
        final int pageSize = 100;
        int pageNum = 1;
        while (true) {
            query.setPageNum(pageNum);
            query.setPageSize(pageSize);
            IPage<AuthRequestVO> result = authRequestMapper.selectAuthRequestPage(
                    new Page<>(query.getPageNum(), query.getPageSize()), query);
            all.addAll(result.getRecords());
            if (result.getRecords().isEmpty() || all.size() >= result.getTotal()) {
                return all;
            }
            if (pageNum >= MAX_FETCH_PAGES) {
                log.warn("[授权申请] 全量拉取达到 {} 页上限（约 {} 条），结果截断返回",
                        MAX_FETCH_PAGES, all.size());
                return all;
            }
            pageNum++;
        }
    }

    /**
     * 分页查询项目授权关系列表。
     */
    @Override
    public PageResult<AssignmentVO> listAssignments(AssignmentQueryDTO query) {
        // IDOR 防护：非 admin/PM 用户仅能查看与自己相关的授权关系
        Set<String> roles = permissionService.getCurrentUserRoles();
        boolean isPrivileged = roles != null && (roles.contains(CommonConstant.ROLE_CODE_ADMIN)
                || roles.contains(CommonConstant.ROLE_CODE_PROJECT_MANAGER));
        if (!isPrivileged) {
            // 强制限定为当前用户作为工程师被授权的记录
            query.setEngineerId(UserContext.getUserId());
        }
        Page<AssignmentVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<AssignmentVO> result = assignmentMapper.selectAssignmentPage(page, query);
        // 计算 isExpired 与 daysToExpire
        LocalDateTime now = LocalDateTime.now();
        for (AssignmentVO vo : result.getRecords()) {
            if (vo.getExpireAt() != null) {
                vo.setIsExpired(vo.getExpireAt().isBefore(now));
                vo.setDaysToExpire(ChronoUnit.DAYS.between(now, vo.getExpireAt()));
            }
        }
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getSize(), result.getCurrent());
    }

    /**
     * 撤销授权。
     * <p>
     * 设置 is_active=0、revoked_at、revoke_reason，并写审计日志。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeAssignment(Long id, String reason) {
        // IDOR 防护：撤销授权是敏感操作，校验当前用户对该授权所属项目的所有权
        validateAssignmentOwnership(id);

        Long operatorId = UserContext.getUserId();
        BizProjectAssignment assignment = assignmentMapper.selectById(id);
        if (assignment == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "授权记录不存在");
        }
        if (assignment.getIsActive() == null || assignment.getIsActive() == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该授权已被撤销，无需重复操作");
        }

        BizProject project = getProjectOrThrow(assignment.getProjectId());
        SysUser engineer = userMapper.selectById(assignment.getEngineerId());

        // 软删除：置 is_active=0，并写入 revoke_reason 与 revoked_at
        LambdaUpdateWrapper<BizProjectAssignment> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BizProjectAssignment::getId, id)
                .set(BizProjectAssignment::getIsActive, 0)
                .set(BizProjectAssignment::getRevokeReason, reason)
                .set(BizProjectAssignment::getRevokedAt, LocalDateTime.now());
        assignmentMapper.update(null, updateWrapper);

        log.info("[项目授权][撤销] 授权ID={}, 项目={}, 工程师={}, 操作人={}",
                id, project.getName(),
                engineer != null ? engineer.getName() : assignment.getEngineerId(), operatorId);

        // 写审计日志
        transferLogHelper.writeTransferLog(project, "revoke",
                engineer, null, operatorId, transferLogHelper.getUserName(operatorId),
                "撤销授权 #" + id + (StringUtils.hasText(reason) ? "，原因：" + reason : ""));
    }

    /**
     * 按项目 + 工程师撤销所有有效授权（撤销双入口收敛的软删单点）。
     * <p>
     * PM 撤销入口（ProjectAuthorizationServiceImpl#revokeEngineer）委托本方法，
     * assignment 软删（is_active=0 + revoke_reason/revoked_at）与审计日志统一在此完成。
     * CAS 语义：仅撤销 is_active=1 的记录，受影响行数为 0 表示无有效授权。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeByProjectAndEngineer(Long projectId, Long engineerId, String reason) {
        Long operatorId = UserContext.getUserId();

        BizProject project = getProjectOrThrow(projectId);
        // 撤销场景不校验启用状态（工程师被禁用后追回授权是合法场景），仅校验存在（用于审计记录）
        SysUser engineer = userMapper.selectById(engineerId);
        if (engineer == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "工程师不存在");
        }

        // CAS 软删：仅撤销有效授权（is_active=1），0 行表示无有效授权（含并发已撤销场景）
        int updated = softDeleteActiveAssignments(projectId, engineerId, reason);
        if (updated == 0) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "该项目下未找到该工程师的有效授权");
        }

        log.info("[项目授权][撤销] 项目={}, 工程师={}, 撤销 {} 条, 操作人={}",
                project.getName(), engineer.getName(), updated, operatorId);

        // 写审计日志
        transferLogHelper.writeTransferLog(project, "revoke",
                engineer, null, operatorId, transferLogHelper.getUserName(operatorId),
                "撤销项目授权" + (StringUtils.hasText(reason) ? "，原因：" + reason : ""));
    }

    /**
     * 直接授权（代提申请 + 自动审批）。
     * <p>
     * assignment 写路径收敛：transferProject / assignEngineer 等授权入口统一委托本方法，
     * 每次授权在 biz_project_auth_request 均有留痕。已存在有效授权时幂等跳过。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthRequestVO grantDirectAuthorization(Long projectId, Long targetEngineerId, String reason) {
        // 幂等：已存在有效授权则跳过（授权目标已达成，避免审批环节 DATA_ALREADY_EXISTS 报错中断调用方流程）
        if (assignmentMapper.countActiveAssignment(projectId, targetEngineerId) > 0) {
            log.info("[直接授权] 已存在有效授权，幂等跳过：projectId={}, engineerId={}", projectId, targetEngineerId);
            return null;
        }

        Long requestId = findPendingRequestId(projectId, targetEngineerId);
        if (requestId == null) {
            AuthRequestCreateDTO createDTO = new AuthRequestCreateDTO();
            createDTO.setProjectId(projectId);
            createDTO.setTargetEngineerId(targetEngineerId);
            createDTO.setReason(reason);
            // scope/expireAt 走默认值，由 createAuthRequest 兜底
            requestId = createAuthRequest(createDTO).getId();
        } else {
            log.info("[直接授权] 复用已有待审批申请：requestId={}", requestId);
        }

        AuthRequestApproveDTO approveDTO = new AuthRequestApproveDTO();
        approveDTO.setApprove(true);
        approveDTO.setComment(StringUtils.hasText(reason)
                ? "直接授权（自动审批）：" + reason
                : "直接授权（自动审批）");
        return approveAuthRequest(requestId, approveDTO);
    }

    /**
     * 项目组场景直写授权（无申请留痕）。
     * <p>
     * 项目组成员可为非工程师用户，无法走申请-审批链路（审批强制 engineer 角色），
     * 仅收敛 INSERT 写路径与查重（与唯一索引对齐，避免 DuplicateKeyException）。
     * <p>
     * 事务：与类内其他写方法保持一致（review F2），当前单条 INSERT 在并发撞唯一索引时
     * 依赖 DuplicateKeyException 幂等跳过，标注事务保证后续扩展多写时的原子性。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createDirectAssignment(Long projectId, Long userId, Long operatorId, String scope, String remark) {
        String effectiveScope = StringUtils.hasText(scope) ? scope : DEFAULT_SCOPE;
        // 查重：同 (project, engineer, is_active=1, scope) 已存在则跳过（场景：项目转移可能已自动创建授权记录）
        int exists = assignmentMapper.selectCount(new LambdaQueryWrapper<BizProjectAssignment>()
                .eq(BizProjectAssignment::getProjectId, projectId)
                .eq(BizProjectAssignment::getEngineerId, userId)
                .eq(BizProjectAssignment::getIsActive, 1)
                .eq(BizProjectAssignment::getPermissionScope, effectiveScope)).intValue();
        if (exists > 0) {
            log.info("[直接授权][项目组] 已存在有效授权记录，跳过 assignment 创建: projectId={}, userId={}",
                    projectId, userId);
            return;
        }

        BizProjectAssignment assignment = new BizProjectAssignment();
        assignment.setProjectId(projectId);
        assignment.setEngineerId(userId);
        assignment.setAssignedBy(operatorId);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setIsActive(1);
        assignment.setRemark(remark);
        assignment.setPermissionScope(effectiveScope);
        // 并发兜底：函数唯一索引 uk_project_engineer_active（仅活跃唯一，V20260821）
        // 拦截上方查重的并发窗口，撞索引时转为幂等跳过（授权目标已达成）
        try {
            assignmentMapper.insert(assignment);
        } catch (DuplicateKeyException e) {
            log.info("[直接授权][项目组] 并发写触发唯一索引兜底，幂等跳过：projectId={}, userId={}",
                    projectId, userId);
        }
    }

    /**
     * 按项目 + 工程师容错软删有效授权（无审计，调用方自带业务审计），返回受影响行数。
     */
    @Override
    public int softDeleteActiveAssignments(Long projectId, Long engineerId, String reason) {
        LambdaUpdateWrapper<BizProjectAssignment> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BizProjectAssignment::getProjectId, projectId)
                .eq(BizProjectAssignment::getEngineerId, engineerId)
                .eq(BizProjectAssignment::getIsActive, 1)
                .set(BizProjectAssignment::getIsActive, 0)
                .set(BizProjectAssignment::getRevokeReason, reason)
                .set(BizProjectAssignment::getRevokedAt, LocalDateTime.now());
        return assignmentMapper.update(null, wrapper);
    }

    /**
     * 按项目批量软删所有有效授权（项目转移场景，审计由调用方统一记录）。
     */
    @Override
    public int softDeleteAllAssignmentsByProject(Long projectId, String reason) {
        return assignmentMapper.deactivateAllByProject(projectId, reason);
    }

    /**
     * 延期授权。
     * <p>
     * 更新 expire_at 字段。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void extendAssignment(Long id, LocalDateTime newExpireAt) {
        // IDOR 防护：延期授权是敏感操作，校验当前用户对该授权所属项目的所有权
        validateAssignmentOwnership(id);

        Long operatorId = UserContext.getUserId();
        BizProjectAssignment assignment = assignmentMapper.selectById(id);
        if (assignment == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "授权记录不存在");
        }
        if (assignment.getIsActive() == null || assignment.getIsActive() == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该授权已被撤销，无法延期");
        }
        if (newExpireAt == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "新的过期时间不能为空");
        }

        BizProject project = getProjectOrThrow(assignment.getProjectId());
        SysUser engineer = userMapper.selectById(assignment.getEngineerId());

        // 更新 expire_at
        LambdaUpdateWrapper<BizProjectAssignment> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BizProjectAssignment::getId, id)
                .set(BizProjectAssignment::getExpireAt, newExpireAt);
        assignmentMapper.update(null, updateWrapper);

        log.info("[项目授权][延期] 授权ID={}, 项目={}, 工程师={}, 新过期时间={}, 操作人={}",
                id, project.getName(),
                engineer != null ? engineer.getName() : assignment.getEngineerId(),
                newExpireAt, operatorId);

        // 写审计日志
        transferLogHelper.writeTransferLog(project, "extend",
                engineer, null, operatorId, transferLogHelper.getUserName(operatorId),
                "延期授权 #" + id + " 至 " + newExpireAt);
    }

    /* ====== 私有辅助方法 ====== */

    /**
     * 校验项目存在并返回
     */
    private BizProject getProjectOrThrow(Long projectId) {
        BizProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "项目不存在");
        }
        return project;
    }

    /**
     * 校验目标工程师：存在、启用、具备 engineer 角色
     *
     * @param engineerId 工程师用户ID
     * @return 校验通过的工程师实体
     */
    private SysUser validateEngineer(Long engineerId) {
        if (engineerId == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "目标工程师ID不能为空");
        }
        SysUser engineer = userMapper.selectById(engineerId);
        if (engineer == null) {
            throw new BusinessException(ResultCode.ENGINEER_NOT_ACTIVE, "目标工程师不存在");
        }
        if (engineer.getStatus() == null || engineer.getStatus() != 1) {
            throw new BusinessException(ResultCode.ENGINEER_NOT_ACTIVE, "目标工程师已禁用");
        }
        List<String> roleCodes = userMapper.selectRoleCodesByUserId(engineerId);
        if (roleCodes == null || !roleCodes.contains(CommonConstant.ROLE_CODE_ENGINEER)) {
            throw new BusinessException(ResultCode.ENGINEER_NOT_ENGINEER_ROLE, "目标用户不具备工程师角色");
        }
        return engineer;
    }

    /**
     * 校验申请人是否有权申请（admin/PM 或项目本身负责人）
     */
    private void validateCanApplyForProject(BizProject project) {
        Set<String> roles = permissionService.getCurrentUserRoles();
        // 管理员 / PM 直接放行
        if (roles != null && (roles.contains(CommonConstant.ROLE_CODE_ADMIN)
                || roles.contains(CommonConstant.ROLE_CODE_PROJECT_MANAGER))) {
            return;
        }
        // 项目本身负责人放行
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(project.getManagerId())) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "您无权为该项目申请授权");
    }

    /**
     * 校验当前用户是否为 admin 或项目经理角色
     * 用于授权审批类操作的权限校验
     */
    private void validateApproverRole() {
        Set<String> roles = permissionService.getCurrentUserRoles();
        if (roles == null || roles.isEmpty()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无审批权限");
        }
        if (!roles.contains(CommonConstant.ROLE_CODE_ADMIN)
                && !roles.contains(CommonConstant.ROLE_CODE_PROJECT_MANAGER)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员或项目经理可执行审批操作");
        }
    }

    /**
     * 校验当前用户对授权记录所属项目的所有权
     * 用于撤销/延期授权等敏感操作
     */
    private void validateAssignmentOwnership(Long assignmentId) {
        BizProjectAssignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "授权记录不存在");
        }
        // admin 跳过项目所有权校验，但仍需保证非 admin 用户只能操作自己项目
        Set<String> roles = permissionService.getCurrentUserRoles();
        if (roles != null && roles.contains(CommonConstant.ROLE_CODE_ADMIN)) {
            return;
        }
        BizProject project = projectMapper.selectById(assignment.getProjectId());
        if (project == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "项目不存在");
        }
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null || !currentUserId.equals(project.getManagerId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "您不是该项目的负责人，无权操作此授权");
        }
    }

    /**
     * 校验申请存在且为 pending 状态
     */
    private BizProjectAuthRequest getPendingRequestOrThrow(Long id) {
        BizProjectAuthRequest request = authRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "授权申请不存在");
        }
        if (!"pending".equals(request.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该授权申请已审核，不可重复审核");
        }
        return request;
    }

    /**
     * 根据 ID 查询申请 VO（含冗余字段）
     * <p>
     * 【修复M8】原实现使用 pageSize=1 的分页查询再内存过滤匹配 id，无法真正按 id 过滤，
     * 当目标记录不在首页时会丢失并触发二次 DB 查询，效率低且可能丢数据。
     * 改为直接 selectById 后转换 VO，单次查询且无丢数据风险。
     */
    private AuthRequestVO getAuthRequestVO(Long id) {
        BizProjectAuthRequest req = authRequestMapper.selectById(id);
        return req == null ? null : convertToVO(req);
    }

    /**
     * 实体转 VO（基础字段，无冗余信息）
     * <p>
     * 修复 #12 性能说明：本方法产生 4 次 DB 查询（3 次 getUserName + 1 次 selectById），
     * 仅用于单条记录返回场景（create/approve/reject 后的返回值）。
     * 若未来需要用于列表场景，需改为批量预查询用户名和项目名后内存映射，
     * 避免循环调用产生 N+1 查询。
     */
    private AuthRequestVO convertToVO(BizProjectAuthRequest req) {
        AuthRequestVO vo = new AuthRequestVO();
        vo.setId(req.getId());
        vo.setProjectId(req.getProjectId());
        vo.setApplicantId(req.getApplicantId());
        vo.setApplicantName(transferLogHelper.getUserName(req.getApplicantId()));
        vo.setTargetEngineerId(req.getTargetEngineerId());
        vo.setTargetEngineerName(transferLogHelper.getUserName(req.getTargetEngineerId()));
        vo.setScope(req.getScope());
        vo.setExpireAt(req.getExpireAt());
        vo.setReason(req.getReason());
        vo.setStatus(req.getStatus());
        vo.setApproverId(req.getApproverId());
        vo.setApproverName(req.getApproverId() != null ? transferLogHelper.getUserName(req.getApproverId()) : null);
        vo.setApprovedAt(req.getApprovedAt());
        vo.setComment(req.getComment());
        vo.setCreatedAt(req.getCreatedAt());
        vo.setUpdatedAt(req.getUpdatedAt());
        BizProject project = projectMapper.selectById(req.getProjectId());
        if (project != null) {
            vo.setProjectName(project.getName());
        }
        return vo;
    }

}
