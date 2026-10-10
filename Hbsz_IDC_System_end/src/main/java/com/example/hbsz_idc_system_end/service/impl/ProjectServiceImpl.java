package com.example.hbsz_idc_system_end.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.*;
import com.example.hbsz_idc_system_end.entity.BizArchiveRecord;
import com.example.hbsz_idc_system_end.entity.BizCustomer;
import com.example.hbsz_idc_system_end.entity.BizDesignDrawing;
import com.example.hbsz_idc_system_end.entity.BizDocument;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizTask;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.BizArchiveRecordMapper;
import com.example.hbsz_idc_system_end.mapper.BizCustomerMapper;
import com.example.hbsz_idc_system_end.mapper.BizDesignDrawingMapper;
import com.example.hbsz_idc_system_end.mapper.BizDocumentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizTaskMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.dto.NotificationSendDTO;
import com.example.hbsz_idc_system_end.service.IFileStorageService;
import com.example.hbsz_idc_system_end.service.INotificationService;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.service.IProjectAuthorizationService;
import com.example.hbsz_idc_system_end.service.IProjectService;
import com.example.hbsz_idc_system_end.service.IProjectTeamService;
import com.example.hbsz_idc_system_end.service.ProjectAccessGuard;
import com.example.hbsz_idc_system_end.utils.ConvertUtils;
import com.example.hbsz_idc_system_end.utils.SqlUtils;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目服务实现
 * <p>
 * 项目主数据的核心服务，聚合客户、设计图纸、归档记录等关联数据。关注点：
 * <ul>
 *   <li>数据隔离：非管理员且无 statistics:query 权限的用户，列表仅返回本人负责的项目；</li>
 *   <li>事务：create/update/delete/archive/unarchive 均为 {@code @Transactional(rollbackFor = Exception.class)}，
 *       联动客户、图纸、归档记录等多表写入；</li>
 *   <li>性能：列表查询通过 {@link #fillDesignDrawingsBatch} 批量补全图纸规避 N+1，统计走单条聚合 SQL；</li>
 *   <li>安全：关键字/负责人做 LIKE 通配符转义防注入。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class ProjectServiceImpl implements IProjectService {

    private final BizProjectMapper projectMapper;
    private final BizCustomerMapper customerMapper;
    private final BizDesignDrawingMapper drawingMapper;
    private final BizArchiveRecordMapper archiveRecordMapper;
    private final IPermissionService permissionService;
    private final BizTaskMapper taskMapper;
    private final BizDocumentMapper documentMapper;
    private final INotificationService notificationService;
    private final IProjectAuthorizationService projectAuthorizationService;
    private final IProjectTeamService projectTeamService;
    private final IFileStorageService fileStorageService;
    private final ProjectAccessGuard accessGuard;
    private final SysUserMapper userMapper;

    public ProjectServiceImpl(BizProjectMapper projectMapper, BizCustomerMapper customerMapper,
                              BizDesignDrawingMapper drawingMapper, BizArchiveRecordMapper archiveRecordMapper,
                              IPermissionService permissionService,
                              BizTaskMapper taskMapper,
                              BizDocumentMapper documentMapper,
                              INotificationService notificationService,
                              IProjectAuthorizationService projectAuthorizationService,
                              IProjectTeamService projectTeamService,
                              IFileStorageService fileStorageService,
                              ProjectAccessGuard accessGuard,
                              SysUserMapper userMapper) {
        this.projectMapper = projectMapper;
        this.customerMapper = customerMapper;
        this.drawingMapper = drawingMapper;
        this.archiveRecordMapper = archiveRecordMapper;
        this.permissionService = permissionService;
        this.taskMapper = taskMapper;
        this.documentMapper = documentMapper;
        this.notificationService = notificationService;
        this.projectAuthorizationService = projectAuthorizationService;
        this.projectTeamService = projectTeamService;
        this.fileStorageService = fileStorageService;
        this.accessGuard = accessGuard;
        this.userMapper = userMapper;
    }

    /**
     * 自注入代理，用于 importProjects 中通过代理调用 @Transactional 的 create() 方法，
     * 避免 this.create() 的 self-invocation 导致事务失效。
     */
    @Lazy
    @Autowired
    private IProjectService self;

    /**
     * 分页查询项目列表
     * <p>
     * 数据隔离（基于角色）：
     * <ul>
     *   <li>管理员：可见全部项目；</li>
     *   <li>项目经理：可见全部项目（PM 需全局只读以监督所有项目）；</li>
     *   <li>工程师 / 普通用户：仅可见被显式授权的项目（biz_project_assignment 表 is_active=1）。</li>
     * </ul>
     * 通过 {@link IProjectAuthorizationService#getCurrentUserVisibleProjectIds()} 注入可见项目ID列表，
     * 返回 null 时表示不限制（管理员/PM），返回空列表时强制返回零条结果（未授权工程师）。
     * 查询后批量填充设计图纸，规避逐条查询的 N+1 问题。
     */
    @Override
    public PageResult<ProjectVO> queryPage(ProjectQueryDTO query) {
        // 数据隔离：根据当前用户角色注入可见项目ID列表
        List<Long> visibleProjectIds = projectAuthorizationService.getCurrentUserVisibleProjectIds();
        query.setVisibleProjectIds(visibleProjectIds);
        if (visibleProjectIds != null) {
            log.debug("[项目服务] 数据隔离：当前用户可见项目数 = {}", visibleProjectIds.size());
        }
        // 转义 LIKE 查询通配符，防止 % _ 被当作通配符
        if (query.getKeyword() != null) {
            query.setKeyword(SqlUtils.escapeLike(query.getKeyword()));
        }
        if (query.getManager() != null) {
            query.setManager(SqlUtils.escapeLike(query.getManager()));
        }
        Page<ProjectVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<ProjectVO> result = projectMapper.selectProjectPage(page, query);

        // 批量填充设计图纸（解决 N+1 查询问题）
        fillDesignDrawingsBatch(result.getRecords());

        return new PageResult<>(result.getRecords(), result.getTotal(), result.getSize(), result.getCurrent());
    }

    /**
     * 判断当前用户是否为管理员
     */
    private boolean isAdmin() {
        try {
            return permissionService.getCurrentUserRoles().contains(CommonConstant.ROLE_CODE_ADMIN);
        } catch (Exception e) {
            log.warn("[项目服务] 判断管理员角色失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断当前用户是否拥有统计查询权限
     * 用于统计页面跳过数据隔离，获取全局统计数据
     */
    private boolean hasStatisticsPermission() {
        try {
            return permissionService.getCurrentUserPermissions().contains("statistics:query");
        } catch (Exception e) {
            log.warn("[项目服务] 判断统计权限失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 查询项目详情
     * <p>
     * 权限校验：管理员/项目经理可访问任意项目；工程师/普通用户必须有显式授权。
     * 校验失败抛出 {@link ResultCode#PROJECT_NOT_AUTHORIZED}。
     */
    @Override
    public ProjectVO getDetail(Long id) {
        // 权限校验：当前用户是否有权访问该项目
        if (!projectAuthorizationService.canAccessProject(id)) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权访问该项目");
        }
        ProjectVO vo = projectMapper.selectProjectDetailById(id);
        if (vo == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "项目不存在");
        }
        fillDesignDrawings(vo);
        return vo;
    }

    /**
     * 新建项目
     * <p>
     * 事务内完成：项目编号唯一性校验、客户信息保存或复用（{@link #saveOrGetCustomer}）、
     * 项目主记录插入、设计图纸批量插入，任一步失败整体回滚。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO create(ProjectSaveDTO dto) {
        // 校验项目编号唯一
        LambdaQueryWrapper<BizProject> codeWrapper = new LambdaQueryWrapper<>();
        codeWrapper.eq(BizProject::getProjectCode, dto.getCode());
        if (projectMapper.selectCount(codeWrapper) > 0) {
            throw new BusinessException(ResultCode.PROJECT_CODE_EXISTS, "项目编号已存在");
        }
        // 校验项目名称唯一（配合 V20260815 uk_project_name 唯一约束，存量重名库约束被跳过时由此兜底）
        LambdaQueryWrapper<BizProject> nameWrapper = new LambdaQueryWrapper<>();
        nameWrapper.eq(BizProject::getName, dto.getName());
        if (projectMapper.selectCount(nameWrapper) > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "项目名称已存在");
        }

        // 保存或查询客户
        BizCustomer customer = saveOrGetCustomer(dto);

        // 构建项目实体
        BizProject project = new BizProject();
        project.setProjectCode(dto.getCode());
        project.setName(dto.getName());
        project.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "进行中");
        project.setPriority(dto.getPriority());
        project.setProjectType(dto.getType());
        project.setCustomerId(customer.getId());
        project.setManagerName(dto.getManager());
        // 设置项目经理用户ID：用于 PM 工作台统计、风险看板、进度审核通知等场景。
        // 前端显式传 managerId 时直接使用；未传时按经理姓名唯一匹配启用用户自动关联，
        // 避免 manager_id 为 NULL 导致审核通知/每日摘要/审批归属静默断链（配套 V20260822 回填）。
        if (dto.getManagerId() != null) {
            project.setManagerId(dto.getManagerId());
        } else {
            project.setManagerId(resolveManagerIdByName(dto.getManager()));
        }
        project.setManagerPhone(dto.getManagerPhone());
        project.setAddress(dto.getAddress());
        project.setRegion(dto.getRegion());
        project.setStartDate(dto.getStartDate());
        project.setEndDate(dto.getEndDate());
        // 数据校验：开始日期不能晚于结束日期，防止非法时间区间导致甘特图/查询异常
        if (dto.getStartDate() != null && dto.getEndDate() != null
                && dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        project.setContractAmount(dto.getContractAmount());
        project.setBudget(dto.getBudget());
        project.setProgress(dto.getProgress() != null ? dto.getProgress() : 0);
        project.setDescription(dto.getDescription());
        project.setIsArchived(0);
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "创建项目需要登录");
        }
        project.setCreatedBy(userId);

        projectMapper.insert(project);

        // 保存设计图纸（批量插入，避免循环单条 insert 产生多次 DB 网络往返）
        if (!CollectionUtils.isEmpty(dto.getDesignDrawings())) {
            List<BizDesignDrawing> drawings = buildDrawings(dto.getDesignDrawings(), project.getId());
            drawingMapper.batchInsert(drawings);
        }

        log.info("新建项目成功: {} ({})", project.getName(), project.getProjectCode());
        return getDetail(project.getId());
    }

    /**
     * 按项目经理姓名唯一匹配启用状态（status=1）的用户，返回其 ID。
     * <p>
     * 用于前端仅提交经理姓名（未显式选择用户）时自动建立用户关联，
     * 保障审核通知、每日待办摘要、审批中心归属等依赖 manager_id 的链路不因数据缺失而断链。
     * 无匹配、重名（同名多用户，避免错关联）或姓名为空时返回 null，保持原可空语义。
     *
     * @param managerName 项目经理姓名（自由文本）
     * @return 唯一匹配的用户 ID；无法唯一确定时为 null
     */
    private Long resolveManagerIdByName(String managerName) {
        if (!StringUtils.hasText(managerName)) {
            return null;
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getName, managerName).eq(SysUser::getStatus, 1);
        List<SysUser> matched = userMapper.selectList(wrapper);
        if (matched.size() == 1) {
            return matched.get(0).getId();
        }
        if (matched.size() > 1) {
            log.warn("[项目服务] 项目经理姓名重名，无法自动关联用户: {}", managerName);
        }
        return null;
    }

    /**
     * 更新项目
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO update(Long id, ProjectSaveDTO dto) {
        BizProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "项目不存在");
        }

        // 校验当前用户对该项目的访问权限（admin/PM/被授权工程师可访问）
        if (!projectAuthorizationService.canAccessProject(id)) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权访问该项目");
        }

        // 校验项目编号唯一（排除自身）
        if (StringUtils.hasText(dto.getCode()) && !dto.getCode().equals(project.getProjectCode())) {
            LambdaQueryWrapper<BizProject> codeWrapper = new LambdaQueryWrapper<>();
            codeWrapper.eq(BizProject::getProjectCode, dto.getCode()).ne(BizProject::getId, id);
            if (projectMapper.selectCount(codeWrapper) > 0) {
                throw new BusinessException(ResultCode.PROJECT_CODE_EXISTS, "项目编号已存在");
            }
            project.setProjectCode(dto.getCode());
        }

        // 校验项目名称唯一（排除自身；配合 V20260815 uk_project_name 唯一约束）
        if (StringUtils.hasText(dto.getName())) {
            LambdaQueryWrapper<BizProject> nameWrapper = new LambdaQueryWrapper<>();
            nameWrapper.eq(BizProject::getName, dto.getName()).ne(BizProject::getId, id);
            if (projectMapper.selectCount(nameWrapper) > 0) {
                throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "项目名称已存在");
            }
            project.setName(dto.getName());
        }
        if (StringUtils.hasText(dto.getStatus())) project.setStatus(dto.getStatus());
        if (StringUtils.hasText(dto.getPriority())) project.setPriority(dto.getPriority());
        if (StringUtils.hasText(dto.getType())) project.setProjectType(dto.getType());

        // 修复 #3：PM 变更统一委托给 transferLeader，避免 update 与 transferLeader 两条路径产生数据不一致
        // transferLeader 会同时更新 biz_project.manager_id/manager_name + biz_project_team leader + biz_project_assignment + 审计日志
        // 当 PM 变更时，不在 update 中直接设置 managerId/managerName，而是委托 transferLeader 统一处理
        Long originalManagerId = project.getManagerId();
        // 修复（2026-08-24 行动项#3）：显式 managerId 缺失但经理姓名文本变化时，按姓名唯一解析目标用户；
        // 解析出与当前负责人不同的用户即视为 PM 变更，同样走 transferLeader 单点，防止文本改链绕过
        // 组长/授权/审计联动造成 manager_id 与 team/assignment 不一致（审批归属分叉缺口）。
        // 重名/无匹配 → resolveManagerIdByName 返回 null 并 WARN，不视为变更，仅更新姓名文本（保持原关联）。
        Long targetManagerId = dto.getManagerId();
        if (targetManagerId == null && originalManagerId != null
                && StringUtils.hasText(dto.getManager())
                && !dto.getManager().equals(project.getManagerName())) {
            Long resolved = resolveManagerIdByName(dto.getManager());
            if (resolved != null && !resolved.equals(originalManagerId)) {
                targetManagerId = resolved;
            }
        }
        boolean pmChanged = targetManagerId != null && !targetManagerId.equals(originalManagerId);
        if (!pmChanged) {
            // PM 未变更：走原有逻辑（直接设置 managerName/managerId）
            if (StringUtils.hasText(dto.getManager())) project.setManagerName(dto.getManager());
            if (dto.getManagerId() != null) {
                project.setManagerId(dto.getManagerId());
            } else if (StringUtils.hasText(dto.getManager()) && project.getManagerId() == null) {
                // 存量项目未关联 PM 用户：按姓名唯一匹配自动补链（修复审核通知/待办归属断链）
                project.setManagerId(resolveManagerIdByName(dto.getManager()));
            }
        }
        // managerPhone 始终在 update 中处理（transferLeader 不涉及电话字段）
        if (dto.getManagerPhone() != null) project.setManagerPhone(dto.getManagerPhone());
        if (dto.getAddress() != null) project.setAddress(dto.getAddress());
        if (dto.getRegion() != null) project.setRegion(dto.getRegion());
        if (dto.getStartDate() != null) project.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) project.setEndDate(dto.getEndDate());
        // 数据校验：开始日期不能晚于结束日期（合并DTO与实体已有日期后校验）
        if (project.getStartDate() != null && project.getEndDate() != null
                && project.getStartDate().isAfter(project.getEndDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (dto.getContractAmount() != null) project.setContractAmount(dto.getContractAmount());
        if (dto.getBudget() != null) project.setBudget(dto.getBudget());
        if (dto.getProgress() != null) project.setProgress(dto.getProgress());
        if (dto.getDescription() != null) project.setDescription(dto.getDescription());

        // 更新客户信息
        if (StringUtils.hasText(dto.getCustomerName())) {
            BizCustomer customer = saveOrGetCustomer(dto);
            project.setCustomerId(customer.getId());
        }

        // 修复 #1：检查乐观锁更新结果，version 不匹配时提示用户刷新后重试
        // MyBatis-Plus OptimisticLockerInnerInterceptor 自动追加 WHERE version = #{old} 并 SET version = #{old}+1
        // 并发更新时后提交者的 version 与数据库不匹配，updateById 返回 0
        int updatedRows = projectMapper.updateById(project);
        if (updatedRows == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "项目数据已被其他用户修改，请刷新页面后重试");
        }

        // 修复 #3：PM 变更时委托 transferLeader 统一处理 team leader / assignment / 审计日志
        // 在 updateById 之后调用，确保项目的其他字段先更新成功，再处理 PM 变更联动
        // targetManagerId：显式 managerId 或姓名文本唯一解析出的目标用户（行动项#3）
        if (pmChanged) {
            log.info("[项目服务] 检测到 PM 变更，委托 transferLeader 处理联动: projectId={}, 旧PM={}, 新PM={}",
                    id, originalManagerId, targetManagerId);
            projectTeamService.transferLeader(id, targetManagerId);
        }

        // 更新设计图纸：基于 id 的 diff 同步（新增/更新/删除），详见 syncDesignDrawings
        // 语义修复：原"先删后增"会重置全部自增 id 与 uploaded_by，无法体现"保留"语义。
        // 注意：null 与空列表语义不同——null 表示前端未提交图纸字段（不触碰现有图纸），
        // 空列表表示清空该项目全部图纸。
        if (dto.getDesignDrawings() != null) {
            syncDesignDrawings(id, dto.getDesignDrawings());
        }

        log.info("更新项目成功: {}", id);
        return getDetail(id);
    }

    /**
     * 删除项目
     * <p>
     * 删除前校验关联任务和文档（存在则拒绝删除，要求用户先清理）。
     * 通过后，数据库外键 ON DELETE CASCADE 自动级联清理以下关联表：
     * biz_project_team、biz_project_assignment、biz_design_drawing、
     * biz_project_auth_request、biz_archive_record、biz_project_transfer_log、
     * biz_task_progress_log。
     * <p>
     * Java 层仅负责：查询设计图纸文件URL（删除前）+ 事务提交后删除物理文件。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "项目不存在");
        }
        // 删除是高危操作，仅 admin 或项目负责人可执行（委托共享守卫单点）
        accessGuard.validateOwnership(id);
        // 数据完整性：删除前校验关联任务和文档，拒绝删除以防止用户误删有业务数据的项目
        LambdaQueryWrapper<BizTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(BizTask::getProjectId, id);
        long taskCount = taskMapper.selectCount(taskWrapper);
        if (taskCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "项目下存在任务，无法删除");
        }
        LambdaQueryWrapper<BizDocument> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(BizDocument::getProjectId, id);
        long docCount = documentMapper.selectCount(docWrapper);
        if (docCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "项目下存在文档，无法删除");
        }

        // 查询设计图纸文件URL列表（删除前查询，DB CASCADE后记录将不存在）
        LambdaQueryWrapper<BizDesignDrawing> drawingWrapper = new LambdaQueryWrapper<>();
        drawingWrapper.eq(BizDesignDrawing::getProjectId, id);
        List<BizDesignDrawing> drawings = drawingMapper.selectList(drawingWrapper);
        List<String> drawingFileUrls = drawings.stream()
                .map(BizDesignDrawing::getFileUrl)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        // 删除项目主记录（DB ON DELETE CASCADE 自动级联清理所有关联表）
        projectMapper.deleteById(id);

        // 事务提交后删除设计图纸物理文件（失败仅记录日志，不影响DB记录已删除的事实）
        if (!drawingFileUrls.isEmpty()) {
            final List<String> finalUrls = drawingFileUrls;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String fileUrl : finalUrls) {
                        try {
                            fileStorageService.deleteFile(fileUrl);
                        } catch (Exception e) {
                            log.warn("[项目删除] 设计图纸物理文件删除失败（DB记录已删除）: fileUrl={}, error={}",
                                    fileUrl, e.getMessage());
                        }
                    }
                }
            });
        }

        log.info("删除项目成功: {} (DB CASCADE自动清理关联表, 图纸物理文件={}个)", id, drawingFileUrls.size());
    }

    /**
     * 归档项目
     * <p>
     * 事务内将项目标记为已归档（isArchived=1、状态置为“已归档”、记录归档日期），
     * 并写入一条 archive 类型的归档操作流水，便于审计追溯。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO archive(Long id) {
        BizProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "项目不存在");
        }
        // 归档是写操作，仅 admin 或项目负责人可执行（委托共享守卫单点）
        accessGuard.validateOwnership(id);
        project.setIsArchived(1);
        project.setStatus("已归档");
        project.setArchiveDate(LocalDate.now());
        projectMapper.updateById(project);

        // 记录归档操作
        BizArchiveRecord record = new BizArchiveRecord();
        record.setProjectId(id);
        record.setProjectCode(project.getProjectCode());
        record.setProjectName(project.getName());
        record.setOperationType("archive");
        record.setArchiveDate(LocalDate.now());
        record.setOperatorId(UserContext.getUserId());
        archiveRecordMapper.insert(record);

        log.info("归档项目成功: {}", id);

        // 通知项目相关人员（项目经理 + 任务负责人，排除操作人）
        notifyProjectArchived(project);

        return getDetail(id);
    }

    /**
     * 取消归档
     * <p>
     * 归档的逆操作：清除归档标记与归档日期、状态回退为“已完成”，
     * 并写入一条 restore 类型的归档操作流水。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO unarchive(Long id) {
        BizProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "项目不存在");
        }
        // 取消归档是写操作，仅 admin 或项目负责人可执行（委托共享守卫单点）
        accessGuard.validateOwnership(id);
        project.setIsArchived(0);
        project.setStatus("已完成");
        project.setArchiveDate(null);
        projectMapper.updateById(project);

        // 记录恢复操作
        BizArchiveRecord record = new BizArchiveRecord();
        record.setProjectId(id);
        record.setProjectCode(project.getProjectCode());
        record.setProjectName(project.getName());
        record.setOperationType("restore");
        record.setOperatorId(UserContext.getUserId());
        archiveRecordMapper.insert(record);

        log.info("取消归档成功: {}", id);
        return getDetail(id);
    }

    /**
     * 通知项目相关人员项目已归档。
     * <p>
     * 通知对象：项目经理 + 项目下所有任务负责人（去重，排除操作人本人）。
     * 静默投递——通知失败不影响归档主流程。
     *
     * @param project 已归档的项目实体
     */
    private void notifyProjectArchived(BizProject project) {
        try {
            Long operatorId = UserContext.getUserId();
            String content = "项目「" + project.getName() + "」已归档";
            // 收集通知目标（项目经理 + 任务负责人，去重）
            java.util.Set<Long> targets = new java.util.HashSet<>();
            if (project.getManagerId() != null) {
                targets.add(project.getManagerId());
            }
            // 查询项目下所有任务的负责人
            List<BizTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<BizTask>()
                    .eq(BizTask::getProjectId, project.getId())
                    .isNotNull(BizTask::getAssigneeId));
            for (BizTask t : tasks) {
                if (t.getAssigneeId() != null) {
                    targets.add(t.getAssigneeId());
                }
            }
            // 排除操作人本人
            targets.remove(operatorId);
            // P1-3.3：改为批量投递，避免循环单条 INSERT 导致 N+1 DB 写入。
            // NotificationService.sendNotifications 已使用 batchInsert 一次性写入，
            // 并通过 parallelStream 并行 WebSocket 推送，性能显著优于循环单条调用。
            List<NotificationSendDTO> dtoList = new java.util.ArrayList<>(targets.size());
            for (Long userId : targets) {
                NotificationSendDTO notif = new NotificationSendDTO();
                notif.setUserId(userId);
                notif.setTitle("项目归档通知");
                notif.setContent(content);
                notif.setNotifyType("archive");
                notif.setBizType("project");
                notif.setBizId(project.getId());
                notif.setPriority(2);
                dtoList.add(notif);
            }
            if (!dtoList.isEmpty()) {
                notificationService.sendNotifications(dtoList);
            }
        } catch (Exception e) {
            log.warn("[项目服务] 通知项目归档失败: projectId={}, error={}", project.getId(), e.getMessage());
        }
    }

    /**
     * 项目统计概览
     * 使用聚合查询替代多次 selectCount 和 selectList
     */
    @Override
    public ProjectStatisticsVO getStatistics() {
        ProjectStatisticsVO vo = new ProjectStatisticsVO();

        // 一次聚合查询获取所有统计值（替代 3次 selectCount + 1次 sumContractAmount + 1次 selectList）
        java.util.Map<String, Object> stats = projectMapper.selectOverviewStatistics();
        vo.setActiveCount(toInt(stats.get("activeProjects")));
        vo.setCompletedCount(toInt(stats.get("completedProjects")));
        vo.setArchivedCount(toInt(stats.get("archivedProjects")));
        vo.setTotalCount(vo.getActiveCount() + vo.getCompletedCount() + vo.getArchivedCount());
        vo.setTotalContractAmount(toBigDecimal(stats.get("totalContractAmount")));
        vo.setAvgProgress(toInt(stats.get("avgProgress")));


        vo.setDocumentCount(0);
        vo.setUpcomingKeyTaskCount(0);
        return vo;
    }


    /**
     * 安全地将对象转为 int
     * <p>
     * 优化说明：原私有实现已抽取到 {@link ConvertUtils#toInt(Object)} 工具类，
     * 此处保留薄封装便于 Service 内调用，不破坏既有代码风格。
     */
    private int toInt(Object value) {
        return ConvertUtils.toInt(value);
    }

    /**
     * 安全地将对象转为 BigDecimal
     * <p>
     * 优化说明：原私有实现已抽取到 {@link ConvertUtils#toBigDecimal(Object)} 工具类，
     * 此处保留薄封装便于 Service 内调用，不破坏既有代码风格。
     */
    private java.math.BigDecimal toBigDecimal(Object value) {
        return ConvertUtils.toBigDecimal(value);
    }

    /**
     * 根据前端 DTO 列表构造设计图纸实体列表
     * <p>
     * 抽取此方法便于 create / update 两处复用，统一图纸字段的默认值逻辑。
     *
     * @param drawingDTOs 前端传入的图纸 DTO 列表
     * @param projectId   所属项目ID
     * @return 可直接用于批量 insert 的实体列表
     */
    private List<BizDesignDrawing> buildDrawings(List<ProjectSaveDTO.DesignDrawingDTO> drawingDTOs, Long projectId) {
        Long currentUserId = UserContext.getUserId();
        List<BizDesignDrawing> drawings = new ArrayList<>(drawingDTOs.size());
        for (ProjectSaveDTO.DesignDrawingDTO drawingDTO : drawingDTOs) {
            BizDesignDrawing drawing = new BizDesignDrawing();
            drawing.setProjectId(projectId);
            applyDrawingFields(drawing, drawingDTO);
            drawing.setUploadedBy(currentUserId);
            drawings.add(drawing);
        }
        return drawings;
    }

    /**
     * 将 DTO 字段映射到图纸实体（新增/更新共用）。
     * <p>
     * 抽取此方法统一图纸字段的默认值逻辑，id / projectId / uploadedBy / createdAt
     * 等生命周期字段由调用方按场景决定是否保留，本方法只覆盖业务字段。
     *
     * @param drawing    目标实体（新增场景为空对象，更新场景为库中查出的既有记录）
     * @param drawingDTO 前端传入的图纸 DTO
     */
    private void applyDrawingFields(BizDesignDrawing drawing, ProjectSaveDTO.DesignDrawingDTO drawingDTO) {
        drawing.setName(drawingDTO.getName());
        drawing.setDrawingType(StringUtils.hasText(drawingDTO.getType()) ? drawingDTO.getType() : "图纸");
        drawing.setFileSize(drawingDTO.getSize());
        // 同步写入字节字段，支持按大小排序与精确统计（size 文本变化时随之重算）
        drawing.setFileSizeBytes(ConvertUtils.parseSizeToBytes(drawingDTO.getSize()));
        if (StringUtils.hasText(drawingDTO.getUploadDate())) {
            drawing.setUploadDate(LocalDate.parse(drawingDTO.getUploadDate()));
        }
    }

    /**
     * 基于 id 的设计图纸 diff 同步（更新项目场景）。
     * <p>
     * 前端提交该项目图纸的<b>全量列表</b>，与库中现有记录做 diff：
     * <ul>
     *   <li>新增：DTO 无 id 的记录批量插入，uploaded_by 取当前用户；</li>
     *   <li>更新：DTO 携带 id 且库中存在同 id 记录时逐条 updateById，
     *       保留原记录的 id / uploaded_by / created_at（不重置上传人与创建时间）；</li>
     *   <li>删除：库中存在但本次未提交的 id 批量删除。</li>
     * </ul>
     * 安全（IDOR 防护）：DTO 携带的 id 在库中不存在或不属于当前项目时，
     * 一律剥离 id 降级为新增处理，避免越权改写其他项目的图纸记录。
     *
     * @param projectId   项目ID
     * @param drawingDTOs 前端提交的图纸全量列表（可能为空列表=清空全部图纸）
     */
    private void syncDesignDrawings(Long projectId, List<ProjectSaveDTO.DesignDrawingDTO> drawingDTOs) {
        // 查询该项目现有图纸，key=id（diff 的"旧集合"基准）
        LambdaQueryWrapper<BizDesignDrawing> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizDesignDrawing::getProjectId, projectId);
        List<BizDesignDrawing> existing = drawingMapper.selectList(wrapper);
        Map<Long, BizDesignDrawing> existingMap = existing.stream()
                .collect(Collectors.toMap(BizDesignDrawing::getId, Function.identity()));

        List<BizDesignDrawing> toInsert = new ArrayList<>();
        Set<Long> retainedIds = new HashSet<>();
        for (ProjectSaveDTO.DesignDrawingDTO drawingDTO : drawingDTOs) {
            BizDesignDrawing target = drawingDTO.getId() == null ? null : existingMap.get(drawingDTO.getId());
            if (target == null) {
                // 新增（id 在本项目下无匹配记录，含 id 为 null / 不存在 / 非本项目归属三种情况）
                toInsert.add(buildDrawing(drawingDTO, projectId));
            } else {
                // 更新：在库中既有记录上覆盖业务字段，保留 id / uploaded_by / created_at
                applyDrawingFields(target, drawingDTO);
                drawingMapper.updateById(target);
                retainedIds.add(target.getId());
            }
        }
        if (!toInsert.isEmpty()) {
            drawingMapper.batchInsert(toInsert);
        }
        // 删除：库中存在但本次未提交的 id
        List<Long> toDeleteIds = existing.stream()
                .map(BizDesignDrawing::getId)
                .filter(existingId -> !retainedIds.contains(existingId))
                .collect(Collectors.toList());
        if (!toDeleteIds.isEmpty()) {
            drawingMapper.deleteBatchIds(toDeleteIds);
        }
        if (log.isDebugEnabled()) {
            log.debug("[项目服务] 图纸 diff 同步完成: projectId={}, 新增={}, 更新={}, 删除={}",
                    projectId, toInsert.size(), retainedIds.size(), toDeleteIds.size());
        }
    }

    /** 构造单条待插入的图纸实体（uploaded_by 取当前用户） */
    private BizDesignDrawing buildDrawing(ProjectSaveDTO.DesignDrawingDTO drawingDTO, Long projectId) {
        BizDesignDrawing drawing = new BizDesignDrawing();
        drawing.setProjectId(projectId);
        applyDrawingFields(drawing, drawingDTO);
        drawing.setUploadedBy(UserContext.getUserId());
        return drawing;
    }

    /**
     * 批量填充设计图纸（解决 N+1 查询问题）
     * 一次查询所有项目的设计图纸，在内存中按 projectId 分组
     */
    private void fillDesignDrawingsBatch(List<ProjectVO> voList) {
        if (CollectionUtils.isEmpty(voList)) {
            return;
        }
        // 收集所有 projectId
        List<Long> projectIds = voList.stream()
                .map(vo -> {
                    try {
                        return Long.parseLong(vo.getId());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        if (projectIds.isEmpty()) {
            voList.forEach(vo -> vo.setDesignDrawings(java.util.Collections.emptyList()));
            return;
        }

        // 一次查询所有设计图纸
        List<BizDesignDrawing> allDrawings = drawingMapper.selectByProjectIds(projectIds);

        // 按 projectId 分组
        java.util.Map<Long, List<BizDesignDrawing>> drawingMap = allDrawings.stream()
                .collect(Collectors.groupingBy(BizDesignDrawing::getProjectId));

        // 填充到每个 VO
        for (ProjectVO vo : voList) {
            try {
                Long projectId = Long.parseLong(vo.getId());
                List<BizDesignDrawing> drawings = drawingMap.getOrDefault(projectId, java.util.Collections.emptyList());
                List<ProjectVO.DesignDrawingVO> list = drawings.stream().map(d -> {
                    ProjectVO.DesignDrawingVO dvo = new ProjectVO.DesignDrawingVO();
                    dvo.setId(String.valueOf(d.getId()));
                    dvo.setName(d.getName());
                    dvo.setType(d.getDrawingType());
                    dvo.setUploadDate(d.getUploadDate() != null ? d.getUploadDate().toString() : null);
                    dvo.setSize(d.getFileSize());
                    return dvo;
                }).collect(Collectors.toList());
                vo.setDesignDrawings(list);
            } catch (NumberFormatException e) {
                vo.setDesignDrawings(java.util.Collections.emptyList());
            }
        }
    }

    /**
     * 填充设计图纸（单条，用于详情查询）
     */
    private void fillDesignDrawings(ProjectVO vo) {
        try {
            Long projectId = Long.parseLong(vo.getId());
            LambdaQueryWrapper<BizDesignDrawing> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BizDesignDrawing::getProjectId, projectId);
            List<BizDesignDrawing> drawings = drawingMapper.selectList(wrapper);
            List<ProjectVO.DesignDrawingVO> list = drawings.stream().map(d -> {
                ProjectVO.DesignDrawingVO dvo = new ProjectVO.DesignDrawingVO();
                dvo.setId(String.valueOf(d.getId()));
                dvo.setName(d.getName());
                dvo.setType(d.getDrawingType());
                dvo.setUploadDate(d.getUploadDate() != null ? d.getUploadDate().toString() : null);
                dvo.setSize(d.getFileSize());
                return dvo;
            }).collect(Collectors.toList());
            vo.setDesignDrawings(list);
        } catch (NumberFormatException e) {
            vo.setDesignDrawings(java.util.Collections.emptyList());
        }
    }

    /**
     * 保存或查询客户
     * <p>
     * 多级匹配策略：优先按数字主键 ID 查询，失败则按客户编号（如 "C001"）查询，
     * 再退化为按客户名称查询；命中则更新联系人信息，均未命中则新建客户。
     * 实现项目与客户的自动关联与信息同步。
     */
    private BizCustomer saveOrGetCustomer(ProjectSaveDTO dto) {
        // 优先按ID查询（customerId 为字符串类型，如 "C001"）
        if (StringUtils.hasText(dto.getCustomerId())) {
            try {
                Long customerId = Long.parseLong(dto.getCustomerId());
                BizCustomer customer = customerMapper.selectById(customerId);
                if (customer != null) return customer;
            } catch (NumberFormatException ignored) {
                // customerId 不是数字ID，尝试按客户编号查询
                LambdaQueryWrapper<BizCustomer> codeWrapper = new LambdaQueryWrapper<>();
                codeWrapper.eq(BizCustomer::getCustomerCode, dto.getCustomerId());
                BizCustomer byCode = customerMapper.selectOne(codeWrapper);
                if (byCode != null) return byCode;
            }
        }
        // 按名称查询
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomer::getName, dto.getCustomerName());
        BizCustomer customer = customerMapper.selectOne(wrapper);
        if (customer != null) {
            // 更新联系人信息
            customer.setContactPerson(dto.getCustomerContact());
            customer.setContactPhone(dto.getCustomerPhone());
            if (dto.getCustomerEmail() != null) customer.setContactEmail(dto.getCustomerEmail());
            customerMapper.updateById(customer);
            return customer;
        }
        // 新建客户
        customer = new BizCustomer();
        // 【修复L3】原用 System.currentTimeMillis() 生成编号，高并发下易产生重复值导致冲突；
        // 改为 UUID 前 8 位，碰撞概率极低，从根本上规避并发冲突。
        customer.setCustomerCode("C" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        customer.setName(dto.getCustomerName());
        customer.setContactPerson(dto.getCustomerContact());
        customer.setContactPhone(dto.getCustomerPhone());
        customer.setContactEmail(dto.getCustomerEmail());
        customer.setStatus(1);
        customerMapper.insert(customer);
        return customer;
    }

    /**
     * 批量导入项目（Excel）。
     * <p>
     * 使用 EasyExcel 同步读取整张 Sheet，逐行转换为 {@link ProjectSaveDTO} 并调用
     * {@link #create(ProjectSaveDTO)} 落库。单行异常被捕获并记录到 errors 列表，
     * 不影响其他行的导入。日期字符串按 yyyy-MM-dd 解析为 {@link LocalDate}。
     *
     * @param file Excel 文件
     * @return 导入结果汇总
     * @throws IOException 读取文件失败时抛出
     */
    @Override
    public ImportResultVO importProjects(MultipartFile file) throws IOException {
        List<ProjectImportDTO> dataList = new ArrayList<>();
        try (InputStream in = file.getInputStream()) {
            EasyExcel.read(in, ProjectImportDTO.class, new ReadListener<ProjectImportDTO>() {
                @Override
                public void invoke(ProjectImportDTO data, AnalysisContext context) {
                    dataList.add(data);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // no-op
                }
            }).sheet().doRead();
        }

        List<String> errors = new ArrayList<>();
        int success = 0;
        int rowIndex = 1; // Excel 行号从 1 开始（不含表头）
        for (ProjectImportDTO data : dataList) {
            rowIndex++;
            try {
                ProjectSaveDTO dto = new ProjectSaveDTO();
                dto.setCode(data.getCode());
                dto.setName(data.getName());
                dto.setType(data.getType());
                dto.setPriority(data.getPriority());
                dto.setCustomerName(data.getCustomerName());
                dto.setCustomerContact(data.getCustomerContact());
                dto.setCustomerPhone(data.getCustomerPhone());
                dto.setCustomerEmail(data.getCustomerEmail());
                dto.setManager(data.getManager());
                dto.setContractAmount(data.getContractAmount());
                dto.setAddress(data.getAddress());
                dto.setDescription(data.getDescription());
                if (StringUtils.hasText(data.getStartDate())) {
                    dto.setStartDate(LocalDate.parse(data.getStartDate().trim()));
                }
                if (StringUtils.hasText(data.getEndDate())) {
                    dto.setEndDate(LocalDate.parse(data.getEndDate().trim()));
                }
                self.create(dto);
                success++;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                errors.add("第" + rowIndex + "行: " + reason);
                log.warn("[项目导入] 第{}行导入失败: {}", rowIndex, reason);
            }
        }
        log.info("[项目导入] 完成: 总数={}, 成功={}, 失败={}", dataList.size(), success, errors.size());
        return ImportResultVO.of(dataList.size(), success, errors);
    }

    /**
     * 生成项目导入模板的字节内容。
     * <p>
     * 模板结构（避免用户盲猜字段规则导致导入失败）：
     * <ul>
     *   <li>Sheet1「项目导入模板」：表头 + 2 行真实合法示例数据；</li>
     *   <li>Sheet2「填写说明」：每个字段的必填性、格式要求、可选值清单
     *       （项目类型/优先级与前端常量 options.js 保持一致），
     *       与 {@link ProjectSaveDTO} 的校验注解及 importProjects 的日期解析规则一一对应。</li>
     * </ul>
     *
     * @return Excel 文件字节
     * @throws IOException 生成文件失败时抛出
     */
    @Override
    public byte[] downloadImportTemplate() throws IOException {
        List<ProjectImportDTO> samples = new ArrayList<>();
        ProjectImportDTO sample1 = new ProjectImportDTO();
        sample1.setCode("PRJ-2026-001");
        sample1.setName("亦庄数据中心A栋综合布线工程");
        sample1.setType("新建");
        sample1.setPriority("高");
        sample1.setCustomerName("北京华邦科技有限公司");
        sample1.setCustomerContact("王经理");
        sample1.setCustomerPhone("13812345678");
        sample1.setCustomerEmail("wang@huabang.com");
        sample1.setManager("张伟");
        sample1.setStartDate("2026-03-01");
        sample1.setEndDate("2026-08-31");
        sample1.setContractAmount(new BigDecimal("1250000.00"));
        sample1.setAddress("北京市亦庄经济开发区XX产业园8号");
        sample1.setDescription("A栋3-5层机房桥架敷设与光缆熔接");
        samples.add(sample1);

        ProjectImportDTO sample2 = new ProjectImportDTO();
        sample2.setCode("PRJ-2026-002");
        sample2.setName("静安机房改造项目");
        sample2.setType("改造");
        sample2.setPriority("中");
        sample2.setCustomerName("上海数据港股份有限公司");
        sample2.setCustomerContact("李主任");
        sample2.setCustomerPhone("13987654321");
        sample2.setManager("张伟");
        sample2.setStartDate("2026-05-10");
        sample2.setEndDate("2026-09-30");
        sample2.setContractAmount(new BigDecimal("680000"));
        sample2.setAddress("上海市静安区XX路66号");
        samples.add(sample2);

        List<List<String>> guide = new ArrayList<>();
        guide.add(java.util.Arrays.asList(
                "【使用须知】① 请先删除或修改第2-3行示例数据，再填写真实数据；"
                        + "② 每行一个项目，勿修改表头行；③ 日期必须为 yyyy-MM-dd 格式（如：2026-03-01）；"
                        + "④ 导入结果按行提示错误原因（如：第4行: 项目类型不能为空）。"));
        guide.add(java.util.Arrays.asList("列名", "是否必填", "填写说明"));
        guide.add(java.util.Arrays.asList("项目编号", "必填",
                "项目唯一编号，系统内唯一（建议格式：PRJ-年份-序号，如 PRJ-2026-001）"));
        guide.add(java.util.Arrays.asList("项目名称", "必填",
                "项目全称"));
        guide.add(java.util.Arrays.asList("项目类型", "必填",
                "可选值（三选一）：新建 / 改造 / 维护"));
        guide.add(java.util.Arrays.asList("优先级", "必填",
                "可选值（三选一）：高 / 中 / 低"));
        guide.add(java.util.Arrays.asList("客户名称", "必填",
                "客户公司全称；系统不存在的客户会在导入时自动创建"));
        guide.add(java.util.Arrays.asList("客户联系人", "必填",
                "客户方对接人姓名"));
        guide.add(java.util.Arrays.asList("客户联系电话", "必填",
                "客户方联系电话"));
        guide.add(java.util.Arrays.asList("客户邮箱", "选填",
                "标准邮箱格式"));
        guide.add(java.util.Arrays.asList("项目经理姓名", "必填",
                "请填写系统中已有用户的姓名（可在「系统管理-用户管理」查询）；填写不存在的姓名虽可导入，"
                        + "但该项目将无法关联到具体用户，影响其工作台统计与审核通知"));
        guide.add(java.util.Arrays.asList("开始日期", "必填",
                "格式 yyyy-MM-dd，如：2026-03-01"));
        guide.add(java.util.Arrays.asList("结束日期", "必填",
                "格式 yyyy-MM-dd，必须晚于开始日期"));
        guide.add(java.util.Arrays.asList("合同金额", "选填",
                "数字，单位：元，如：1250000.00"));
        guide.add(java.util.Arrays.asList("项目地址", "选填",
                "项目实施地址"));
        guide.add(java.util.Arrays.asList("项目描述", "选填",
                "项目简要说明"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (com.alibaba.excel.ExcelWriter writer = EasyExcel.write(out).build()) {
            com.alibaba.excel.write.metadata.WriteSheet mainSheet =
                    EasyExcel.writerSheet(0, "项目导入模板").head(ProjectImportDTO.class).build();
            writer.write(samples, mainSheet);
            com.alibaba.excel.write.metadata.WriteSheet guideSheet =
                    EasyExcel.writerSheet(1, "填写说明").build();
            writer.write(guide, guideSheet);
        }
        return out.toByteArray();
    }

}
