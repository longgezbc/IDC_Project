package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.annotation.Idempotent;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.AuthRequestVO;
import com.example.hbsz_idc_system_end.dto.NotificationSendDTO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.ProjectAssignmentVO;
import com.example.hbsz_idc_system_end.dto.ProjectTransferDTO;
import com.example.hbsz_idc_system_end.dto.TransferLogQueryDTO;
import com.example.hbsz_idc_system_end.dto.TransferLogVO;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizProjectTransferLog;
import com.example.hbsz_idc_system_end.entity.BizTask;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.BizProjectAssignmentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectTransferLogMapper;
import com.example.hbsz_idc_system_end.mapper.BizTaskMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.INotificationService;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.service.IProjectAuthService;
import com.example.hbsz_idc_system_end.service.IProjectAuthorizationService;
import com.example.hbsz_idc_system_end.service.IProjectTeamService;
import com.example.hbsz_idc_system_end.service.ProjectAccessGuard;
import com.example.hbsz_idc_system_end.service.ProjectTransferLogHelper;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目授权与转移服务实现
 * <p>
 * 核心职责：
 * <ul>
 *   <li>权限校验：转移/授权/撤销均限定 PM 或 admin 操作；</li>
 *   <li>目标工程师校验：必须存在、启用且具备 engineer 角色；</li>
 *   <li>事务保证：授权变更 + 审计日志写入在同一事务内，任一失败回滚；</li>
 *   <li>静默通知：授权/转移成功后通过通知服务告知目标工程师（失败不影响主流程）；</li>
 *   <li>数据隔离：getCurrentUserVisibleProjectIds 为项目列表提供工程师可见项目过滤。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class ProjectAuthorizationServiceImpl implements IProjectAuthorizationService {

    private final BizProjectAssignmentMapper assignmentMapper;
    private final BizProjectTransferLogMapper transferLogMapper;
    private final BizProjectMapper projectMapper;
    private final SysUserMapper userMapper;
    private final BizTaskMapper taskMapper;
    private final IPermissionService permissionService;
    private final INotificationService notificationService;
    private final IProjectAuthService projectAuthService;
    private final IProjectTeamService projectTeamService;
    private final ProjectTransferLogHelper transferLogHelper;
    private final ProjectAccessGuard accessGuard;

    // 依赖方向说明：本服务 → IProjectTeamService（负责人移交单点）为单向依赖，
    // 所有权/访问校验统一委托 ProjectAccessGuard（与团队服务共享单点），无服务间循环。
    public ProjectAuthorizationServiceImpl(BizProjectAssignmentMapper assignmentMapper,
                                           BizProjectTransferLogMapper transferLogMapper,
                                           BizProjectMapper projectMapper,
                                           SysUserMapper userMapper,
                                           BizTaskMapper taskMapper,
                                           IPermissionService permissionService,
                                           INotificationService notificationService,
                                           IProjectAuthService projectAuthService,
                                           IProjectTeamService projectTeamService,
                                           ProjectTransferLogHelper transferLogHelper,
                                           ProjectAccessGuard accessGuard) {
        this.assignmentMapper = assignmentMapper;
        this.transferLogMapper = transferLogMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.taskMapper = taskMapper;
        this.permissionService = permissionService;
        this.notificationService = notificationService;
        this.projectAuthService = projectAuthService;
        this.projectTeamService = projectTeamService;
        this.transferLogHelper = transferLogHelper;
        this.accessGuard = accessGuard;
    }

    /* ====== 公开方法 ====== */

    /**
     * 项目转移（2026-08 重设计：支持负责人移交）。
     * <p>
     * 转移语义（与前端"项目转移/授权"对话框对应）：
     * <ul>
     *   <li>{@code toManagerId} 非空：移交项目所有权——委托 {@link IProjectTeamService#transferLeader}
     *       单点完成（biz_project.manager_id/manager_name + 项目组长 + 自动授权 + 审计日志）。
     *       审批待办（进度/工作量/文档/授权申请）为实时联查 {@code p.manager_id}，
     *       移交后存量待审自动流向新负责人，无需数据迁移；</li>
     *   <li>{@code toEngineerId} 非空：撤销项目现有全部工程师授权并授权目标工程师（原转移语义）；</li>
     *   <li>两者至少传一个，否则抛参数错误。</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    // 并发安全：通过 @Idempotent 幂等控制，防止并发/重复调用 transferProject 导致授权数据丢失。
    // 场景：前端因网络抖动重复提交、用户快速双击，或两次并发请求同时进入“撤销原授权”步骤，
    // 会造成后写入者覆盖前者、原授权被多次撤销或新授权重复插入，最终授权状态混乱。
    // 幂等窗口内（300 秒）相同 requestId 的重复请求将被拒绝，保证授权变更原子执行。
    @Idempotent(key = "project:transfer", expire = 300)
    public ProjectAssignmentVO transferProject(Long projectId, ProjectTransferDTO dto) {
        // 安全修复 #24：校验当前用户是否为该项目的负责人（管理员跳过），防止越权操作他人项目。
        // 注意：必须在移交前校验（基于旧 manager_id），移交后原负责人即失去操作权
        accessGuard.validateOwnership(projectId);

        boolean transferManager = dto.getToManagerId() != null;
        boolean transferEngineer = dto.getToEngineerId() != null;
        if (!transferManager && !transferEngineer) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "请至少指定新负责人或新工程师");
        }

        Long operatorId = UserContext.getUserId();
        String operatorName = transferLogHelper.getUserName(operatorId);
        log.info("[项目授权][转移] 开始：projectId={}, operator={}, toManager={}, toEngineer={}",
                projectId, operatorName, dto.getToManagerId(), dto.getToEngineerId());

        // 1. 校验项目存在
        BizProject project = getProjectOrThrow(projectId);

        // 2. 负责人移交（可选）：委托 transferLeader 单点，与项目编辑 PM 变更路径（修复 #3）一致
        if (transferManager) {
            if (dto.getToManagerId().equals(project.getManagerId())) {
                throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "新负责人不能是当前负责人本人");
            }
            SysUser newManager = validateManager(dto.getToManagerId());
            log.info("[项目授权][移交负责人] projectId={}, fromManagerId={}, toManager={}",
                    projectId, project.getManagerId(), newManager.getName());
            projectTeamService.transferLeader(projectId, newManager.getId());
            // 静默通知新负责人：审批责任已随之转移（失败不影响主流程）
            notifyManagerTransferred(project, newManager, operatorName);
        }

        // 3. 工程师替换（可选，原转移语义）
        if (transferEngineer) {
            // 3.1 校验目标工程师
            SysUser targetEngineer = validateEngineer(dto.getToEngineerId());
            // 3.2 查询原授权工程师（取第一条作为 from，用于审计记录）
            List<ProjectAssignmentVO> oldAssignments = assignmentMapper.selectAssignmentsByProject(projectId);
            SysUser fromEngineer = null;
            if (!oldAssignments.isEmpty()) {
                Long fromId = oldAssignments.get(0).getEngineerId();
                fromEngineer = userMapper.selectById(fromId);
            }

            // 3.3 撤销原所有有效授权（revoke_reason='项目转移替换'）——软删写路径单点
            projectAuthService.softDeleteAllAssignmentsByProject(projectId, "项目转移替换");
            // 3.4 直接授权目标工程师（代提申请 + 自动审批，biz_project_auth_request 留痕）
            //     assignment 写路径收敛：不再 buildAssignment 直写，走统一申请-审批链路
            projectAuthService.grantDirectAuthorization(projectId, targetEngineer.getId(),
                    StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : "项目转移自动授权");
            // 3.5 写审计日志
            transferLogHelper.writeTransferLog(project, "transfer", fromEngineer, targetEngineer, operatorId, operatorName, dto.getRemark());
            log.info("[项目授权][转移] 完成：projectId={}, from={}, to={}",
                    projectId,
                    fromEngineer != null ? fromEngineer.getName() : "无",
                    targetEngineer.getName());

            // 3.6 静默通知目标工程师（失败不影响主流程）
            notifyEngineerAssigned(project, targetEngineer, operatorName, "转移");

            return assignmentMapper.selectAssignmentsByProject(projectId).stream()
                    .filter(a -> a.getEngineerId().equals(targetEngineer.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ResultCode.SYSTEM_ERROR, "授权后查询失败"));
        }

        // 仅移交负责人：无新工程师授权记录可返回，前端以成功消息为准
        log.info("[项目授权][移交负责人] 完成：projectId={}, operator={}", projectId, operatorName);
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 并发安全：通过 @Idempotent 幂等控制，防止并发/重复调用 assignEngineer 导致重复授权。
    // 委托链路内 createAuthRequest/approveAuthRequest 各自有 pending 幂等校验与 CAS 状态校验，
    // @Idempotent 在请求层面提前拦截，避免并发竞态产生重复数据。
    @Idempotent(key = "project:assign", expire = 300)
    public ProjectAssignmentVO assignEngineer(Long projectId, Long engineerId, String remark) {
        // 安全修复 #24：校验当前用户是否为该项目的负责人（管理员跳过），防止越权操作他人项目
        accessGuard.validateOwnership(projectId);
        Long operatorId = UserContext.getUserId();
        String operatorName = transferLogHelper.getUserName(operatorId);
        log.info("[项目授权][新增] 开始（委托申请-审批链路）：projectId={}, operator={}, engineerId={}",
                projectId, operatorName, engineerId);

        // 校验（项目存在性/工程师有效性/申请权限）统一由委托链路 createAuthRequest 负责，
        // 此处仅轻量获取通知所需实体，避免同一请求内重复执行完整校验查询
        BizProject project = projectMapper.selectById(projectId);
        SysUser engineer = userMapper.selectById(engineerId);

        // ===== 授权写路径收敛（单点） =====
        // PM 直接授权委托 grantDirectAuthorization：代提申请 + 自动审批（复用已有待审批申请），
        // 由 approveAuthRequest 统一完成 assignment 写入、状态流转与审计日志；
        // 已存在有效授权时幂等跳过（返回后由下方查询返回既有 assignment）。
        // transferProject / ProjectTeamServiceImpl 的 assignment 写路径同样收敛至 ProjectAuthServiceImpl。
        AuthRequestVO granted = projectAuthService.grantDirectAuthorization(projectId, engineerId,
                StringUtils.hasText(remark) ? remark : "PM 直接授权");

        log.info("[项目授权][新增] 完成：projectId={}, engineer={}, requestId={}",
                projectId, engineer.getName(),
                granted != null ? granted.getId() : "已存在有效授权，跳过");

        // 静默通知目标工程师（失败不影响主流程，与转移路径一致）
        notifyEngineerAssigned(project, engineer, operatorName, "授权");

        return assignmentMapper.selectAssignmentsByProject(projectId).stream()
                .filter(a -> a.getEngineerId().equals(engineerId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.SYSTEM_ERROR, "授权后查询失败"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 并发安全：通过 @Idempotent 幂等控制，防止并发/重复调用 revokeEngineer 导致授权状态异常。
    // 委托链路 revokeByProjectAndEngineer 采用 CAS 软删（WHERE is_active=1），
    // 并发重复撤销时受影响行数为 0 即抛错，幂等注解在请求层面提前拦截。
    @Idempotent(key = "project:revoke", expire = 300)
    public void revokeEngineer(Long projectId, Long engineerId, String remark) {
        // 安全修复 #24：校验当前用户是否为该项目的负责人（管理员跳过），防止越权操作他人项目
        accessGuard.validateOwnership(projectId);
        log.info("[项目授权][撤销] 开始（委托统一软删路径）：projectId={}, operator={}, engineerId={}",
                projectId, UserContext.getUsername(), engineerId);

        // ===== 撤销双入口收敛 =====
        // assignment 软删（is_active=0 + revoke_reason/revoked_at）与审计日志委托
        // revokeByProjectAndEngineer 统一完成，与 revokeAssignment（按记录撤销）共享
        // ProjectAuthServiceImpl 单点软删写路径，消除跨 Service 的重复软删逻辑。
        // 无有效授权（含并发已撤销）由被委托方 CAS 0 行抛 DATA_NOT_FOUND。
        projectAuthService.revokeByProjectAndEngineer(projectId, engineerId, remark);

        log.info("[项目授权][撤销] 完成：projectId={}, engineerId={}", projectId, engineerId);
    }

    @Override
    public List<ProjectAssignmentVO> listProjectEngineers(Long projectId) {
        // IDOR 防护：查询项目工程师列表属于项目敏感信息，校验当前用户对该项目的访问权限
        if (!canAccessProject(projectId)) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权查看该项目的工程师列表");
        }
        return assignmentMapper.selectAssignmentsByProject(projectId);
    }

    @Override
    public List<SysUser> listActiveEngineers() {
        return userMapper.selectActiveEngineers();
    }

    @Override
    public List<SysUser> listActiveManagers() {
        return userMapper.selectActiveManagers();
    }

    @Override
    public PageResult<TransferLogVO> queryTransferLogs(TransferLogQueryDTO query) {
        // IDOR 防护：转移审计日志属于敏感信息，非 admin/PM 用户只能查看自己项目的日志
        Set<String> roles = permissionService.getCurrentUserRoles();
        boolean isPrivileged = roles != null && (roles.contains(CommonConstant.ROLE_CODE_ADMIN)
                || roles.contains(CommonConstant.ROLE_CODE_PROJECT_MANAGER));
        if (!isPrivileged) {
            // 非 admin/PM 用户：仅能查看自己作为项目经理的项目转移日志
            // 通过 query 的 projectId 限定（如有），或返回空列表
            // 由于 TransferLogQueryDTO 可能不支持按 managerId 过滤，简化为：非特权用户必须有 projectId 参数
            if (query.getProjectId() == null) {
                return new PageResult<>(Collections.emptyList(), 0L, query.getPageSize(), query.getPageNum());
            }
            // 校验当前用户对该项目的访问权限
            if (!canAccessProject(query.getProjectId())) {
                throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权查看该项目的转移日志");
            }
        }
        Page<TransferLogVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<TransferLogVO> result = transferLogMapper.selectLogPage(page, query);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public List<Long> getCurrentUserVisibleProjectIds() {
        Set<String> roles = permissionService.getCurrentUserRoles();
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        // 管理员 / 项目经理：不限制（返回 null 表示不过滤）
        if (roles.contains(CommonConstant.ROLE_CODE_ADMIN)
                || roles.contains(CommonConstant.ROLE_CODE_PROJECT_MANAGER)) {
            return null;
        }
        // 部门隔离预留：未来启用部门时，加以下逻辑
        // Long deptId = currentUser.getDeptId();
        // if (deptId != null && !isAdmin) {
        //     return projectMapper.selectProjectIdsByDept(deptId);
        // }
        // 工程师 / 普通用户：返回显式授权的项目ID列表（仅未过期的有效授权）
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Collections.emptyList();
        }
        List<Long> ids = assignmentMapper.selectAuthorizedProjectIds(userId);
        return ids != null ? ids : Collections.emptyList();
    }

    @Override
    public boolean canAccessProject(Long projectId) {
        // 委托共享单点：与 ProjectTeamServiceImpl 等服务共享同一份访问判定逻辑
        return accessGuard.canAccessProject(projectId);
    }

    /**
     * 阶段2：任务级数据隔离 - 获取当前用户在指定项目下可见的任务ID列表。
     * <p>
     * 实现策略（按角色差异化）：
     * <ul>
     *   <li>admin / 项目经理：返回 null（不限制，可查看项目下所有任务）；</li>
     *   <li>高级工程师 / 外包 / 只读：返回 null（同 PM，可查看项目下所有任务，写权限由权限点控制）；</li>
     *   <li>普通工程师：仅返回自己负责的任务ID列表（assignee_id = userId）。</li>
     * </ul>
     * 若用户未获得该项目授权（仅对工程师生效），返回空列表。
     *
     * @param projectId 项目ID
     * @return 可见任务ID列表，null 表示不限制，空列表表示无权访问该项目
     */
    @Override
    public List<Long> getCurrentUserVisibleTaskIds(Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Collections.emptyList();
        }

        Set<String> roles = permissionService.getCurrentUserRoles();
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        // admin / PM：不限制（返回 null 表示不过滤）
        if (roles.contains(CommonConstant.ROLE_CODE_ADMIN)
                || roles.contains(CommonConstant.ROLE_CODE_PROJECT_MANAGER)) {
            return null;
        }

        // 检查是否有项目授权（无授权直接返回空列表）
        List<Long> visibleProjectIds = getCurrentUserVisibleProjectIds();
        if (visibleProjectIds != null && !visibleProjectIds.contains(projectId)) {
            return Collections.emptyList();
        }

        // 工程师角色：仅返回自己负责的任务ID
        if (roles.contains(CommonConstant.ROLE_CODE_ENGINEER)) {
            List<BizTask> myTasks = taskMapper.selectList(new LambdaQueryWrapper<BizTask>()
                    .eq(BizTask::getProjectId, projectId)
                    .eq(BizTask::getAssigneeId, userId));
            if (myTasks == null || myTasks.isEmpty()) {
                return Collections.emptyList();
            }
            return myTasks.stream().map(BizTask::getId).collect(Collectors.toList());
        }

        // 其他角色（高级工程师 / 外包 / 只读等）：不限制，由权限点控制只读
        return null;
    }

    /* ====== 私有辅助方法 ====== */

    /**
     * 校验目标负责人：存在、启用、具备项目经理角色
     * <p>
     * 项目转移入口语义为"移交给另一位项目经理"，比项目编辑路径更严格
     * （编辑路径由前端用户下拉选择，transferLeader 仅校验用户存在）。
     *
     * @param managerId 目标负责人ID
     */
    private SysUser validateManager(Long managerId) {
        SysUser manager = userMapper.selectById(managerId);
        if (manager == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "目标负责人不存在");
        }
        if (manager.getStatus() == null || manager.getStatus() != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "目标负责人已禁用");
        }
        List<String> roleCodes = userMapper.selectRoleCodesByUserId(managerId);
        if (roleCodes == null || !roleCodes.contains(CommonConstant.ROLE_CODE_PROJECT_MANAGER)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "目标用户不具备项目经理角色");
        }
        return manager;
    }

    /**
     * 静默通知新负责人项目已移交（审批责任随之转移）
     * 失败不影响主流程（通知服务内部已捕获异常）
     */
    private void notifyManagerTransferred(BizProject project, SysUser newManager, String operatorName) {
        try {
            NotificationSendDTO notif = new NotificationSendDTO();
            notif.setUserId(newManager.getId());
            notif.setTitle("项目移交通知");
            notif.setContent(String.format(
                    "%s 将项目「%s」移交给您负责，该项目的进度、工作量、文档及授权审批已同步转移给您，请及时处理待办",
                    operatorName, project.getName()));
            notif.setNotifyType("system");
            notif.setBizType("project");
            notif.setBizId(project.getId());
            notif.setPriority(2);
            notificationService.sendNotification(notif);
        } catch (Exception e) {
            log.warn("[项目授权] 通知新负责人失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /**
     * 校验目标工程师：存在、启用、具备 engineer 角色
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
     * 静默通知工程师被授权/转移
     * 失败不影响主流程（通知服务内部已捕获异常）
     */
    private void notifyEngineerAssigned(BizProject project, SysUser engineer, String operatorName, String action) {
        try {
            NotificationSendDTO notif = new NotificationSendDTO();
            notif.setUserId(engineer.getId());
            notif.setTitle("项目" + action + "通知");
            notif.setContent(String.format("您已被 %s %s 访问项目「%s」，请及时查看相关任务",
                    operatorName, action, project.getName()));
            notif.setNotifyType("system");
            notif.setBizType("project");
            notif.setBizId(project.getId());
            notif.setPriority(2);
            notificationService.sendNotification(notif);
        } catch (Exception e) {
            log.warn("[项目授权] 通知工程师失败（不影响主流程）: {}", e.getMessage());
        }
    }

}
