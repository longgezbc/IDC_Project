package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.TeamMemberAddDTO;
import com.example.hbsz_idc_system_end.dto.TeamMemberQueryDTO;
import com.example.hbsz_idc_system_end.dto.TeamMemberVO;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizProjectTeam;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectTeamMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.IProjectAuthService;
import com.example.hbsz_idc_system_end.service.IProjectTeamService;
import com.example.hbsz_idc_system_end.service.ProjectAccessGuard;
import com.example.hbsz_idc_system_end.service.ProjectTransferLogHelper;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 项目组管理服务实现
 * <p>
 * 阶段4：项目组跨部门协作。负责项目组成员的添加、移除、查询、转让组长等管理操作。
 * <p>
 * 关键设计要点：
 * <ul>
 *   <li>所有写操作均标注 {@code @Transactional(rollbackFor = Exception.class)}，
 *       保证项目组成员变更、授权记录变更与审计日志写入在同一事务内，异常时整体回滚；</li>
 *   <li>添加成员/转让组长时同步创建授权记录（permissionScope=read_write），
 *       实现项目组与授权体系联动——assignment 写路径统一委托
 *       {@link IProjectAuthService}（授权写路径单点收敛）；</li>
 *   <li>移除成员时同步撤销对应授权记录（容错软删，无有效授权不阻断移除）；</li>
 *   <li>转让组长时同步更新 {@link BizProject#getManagerId()}，保证项目管理人员与组长一致。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class ProjectTeamServiceImpl implements IProjectTeamService {

    /** 默认组内角色：成员 */
    private static final String DEFAULT_ROLE = "member";
    /** 组长角色 */
    private static final String ROLE_LEADER = "leader";
    /** 默认授权范围：读写 */
    private static final String DEFAULT_SCOPE = "read_write";

    private final BizProjectTeamMapper teamMapper;
    private final BizProjectMapper projectMapper;
    private final SysUserMapper userMapper;
    private final IProjectAuthService projectAuthService;
    private final ProjectTransferLogHelper transferLogHelper;
    private final ProjectAccessGuard accessGuard;

    // 依赖方向说明：所有权/访问校验统一委托 ProjectAccessGuard（与授权服务共享单点），
    // 本服务不再依赖 IProjectAuthorizationService，消除服务间循环依赖。
    public ProjectTeamServiceImpl(BizProjectTeamMapper teamMapper,
                                   BizProjectMapper projectMapper,
                                   SysUserMapper userMapper,
                                   IProjectAuthService projectAuthService,
                                   ProjectTransferLogHelper transferLogHelper,
                                   ProjectAccessGuard accessGuard) {
        this.teamMapper = teamMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.projectAuthService = projectAuthService;
        this.transferLogHelper = transferLogHelper;
        this.accessGuard = accessGuard;
    }

    /* ====== 公开方法 ====== */

    /**
     * 添加项目组成员。
     * <p>
     * 业务流程：
     * <ol>
     *   <li>校验项目存在；</li>
     *   <li>校验用户存在且启用；</li>
     *   <li>校验用户未重复加入（is_active=1）；</li>
     *   <li>创建 biz_project_team 记录；</li>
     *   <li>同时创建 biz_project_assignment（permissionScope=read_write）；</li>
     *   <li>写审计日志。</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamMemberVO addMember(TeamMemberAddDTO dto) {
        // IDOR 防护：添加成员是敏感操作，仅 admin 或项目负责人可执行（委托共享守卫单点）
        accessGuard.validateOwnership(dto.getProjectId());

        Long operatorId = UserContext.getUserId();
        String operatorName = transferLogHelper.getUserName(operatorId);

        // 校验项目存在
        BizProject project = getProjectOrThrow(dto.getProjectId());
        // 校验用户存在
        SysUser user = getUserOrThrow(dto.getUserId());

        // 校验用户未重复加入（同一项目下 is_active=1 的记录唯一）
        int existsActive = teamMapper.selectCount(new LambdaQueryWrapper<BizProjectTeam>()
                .eq(BizProjectTeam::getProjectId, dto.getProjectId())
                .eq(BizProjectTeam::getUserId, dto.getUserId())
                .eq(BizProjectTeam::getIsActive, 1)).intValue();
        if (existsActive > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "该用户已在项目组中");
        }

        // 创建项目组成员记录
        BizProjectTeam team = new BizProjectTeam();
        team.setProjectId(dto.getProjectId());
        team.setUserId(dto.getUserId());
        team.setRoleInTeam(StringUtils.hasText(dto.getRoleInTeam()) ? dto.getRoleInTeam() : DEFAULT_ROLE);
        team.setJoinedAt(LocalDateTime.now());
        team.setIsActive(1);
        team.setRemark(dto.getRemark());
        teamMapper.insert(team);

        // 同步创建 biz_project_assignment（permissionScope=read_write）——委托授权写路径单点
        // 项目组成员可为非工程师（前端成员选择器不限角色），无法走申请-审批链路（审批强制 engineer 角色），
        // 因此走 createDirectAssignment 直写原语；查重与唯一索引冲突防护由其内部统一处理
        projectAuthService.createDirectAssignment(dto.getProjectId(), dto.getUserId(),
                operatorId, DEFAULT_SCOPE, "项目组加入自动授权");

        log.info("[项目组][添加成员] 记录ID={}, 项目={}, 成员={}, 角色={}, 操作人={}",
                team.getId(), project.getName(), user.getName(), team.getRoleInTeam(), operatorName);

        // 写审计日志
        transferLogHelper.writeTransferLog(project, "team_add", null, user, operatorId, operatorName,
                "添加项目组成员：" + user.getName() + "，角色：" + team.getRoleInTeam());

        return getTeamMemberVO(team.getId());
    }

    /**
     * 移除项目组成员。
     * <p>
     * 业务流程：
     * <ol>
     *   <li>校验记录存在且在组中（is_active=1）；</li>
     *   <li>设置 left_at、is_active=0；</li>
     *   <li>同时撤销对应的 biz_project_assignment 记录；</li>
     *   <li>写审计日志。</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long id, String reason) {
        Long operatorId = UserContext.getUserId();
        String operatorName = transferLogHelper.getUserName(operatorId);

        BizProjectTeam team = teamMapper.selectById(id);
        if (team == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "项目组成员记录不存在");
        }
        if (team.getIsActive() == null || team.getIsActive() == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该成员已离组，无需重复操作");
        }

        // IDOR 防护：移除成员是敏感操作，仅 admin 或项目负责人可执行（委托共享守卫单点）
        accessGuard.validateOwnership(team.getProjectId());

        BizProject project = getProjectOrThrow(team.getProjectId());
        SysUser user = userMapper.selectById(team.getUserId());

        // 1. 软删除项目组成员记录
        LambdaUpdateWrapper<BizProjectTeam> teamUpdate = new LambdaUpdateWrapper<>();
        teamUpdate.eq(BizProjectTeam::getId, id)
                .eq(BizProjectTeam::getIsActive, 1)
                .set(BizProjectTeam::getIsActive, 0)
                .set(BizProjectTeam::getLeftAt, LocalDateTime.now());
        teamMapper.update(null, teamUpdate);

        // 2. 同步撤销 biz_project_assignment——委托软删写路径单点
        // 容错语义：成员可能无有效授权（如已被 PM 单独撤销），0 行属正常场景，不阻断移除流程
        projectAuthService.softDeleteActiveAssignments(team.getProjectId(), team.getUserId(),
                StringUtils.hasText(reason) ? "项目组移除：" + reason : "项目组移除");

        log.info("[项目组][移除成员] 记录ID={}, 项目={}, 成员={}, 操作人={}",
                id, project.getName(),
                user != null ? user.getName() : team.getUserId(), operatorName);

        // 写审计日志
        transferLogHelper.writeTransferLog(project, "team_remove", user, null, operatorId, operatorName,
                "移除项目组成员：" + (user != null ? user.getName() : team.getUserId())
                        + (StringUtils.hasText(reason) ? "，原因：" + reason : ""));
    }

    /**
     * 分页查询项目组成员列表。
     */
    @Override
    public PageResult<TeamMemberVO> listMembers(TeamMemberQueryDTO query) {
        // IDOR 防护：查询成员列表，校验当前用户对该项目的访问权限（委托共享守卫单点）
        if (query.getProjectId() != null
                && !accessGuard.canAccessProject(query.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权查看该项目成员");
        }

        Page<TeamMemberVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<TeamMemberVO> result = teamMapper.selectTeamMemberPage(page, query);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getSize(), result.getCurrent());
    }

    /**
     * 查询当前用户参与的所有项目组。
     * <p>
     * 语义为"取全量"（review F3）：此前固定 pageSize=100，超过 100 条项目组时
     * 被静默截断；改为按 total 循环翻页直到取满，硬上限防异常数据无限翻页。
     */
    @Override
    public List<TeamMemberVO> listMyTeams() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Collections.emptyList();
        }
        TeamMemberQueryDTO query = new TeamMemberQueryDTO();
        query.setUserId(userId);
        query.setIsActive(1);
        List<TeamMemberVO> all = new ArrayList<>();
        final int pageSize = 100;
        int pageNum = 1;
        while (true) {
            query.setPageNum(pageNum);
            query.setPageSize(pageSize);
            IPage<TeamMemberVO> result = teamMapper.selectTeamMemberPage(
                    new Page<>(query.getPageNum(), query.getPageSize()), query);
            all.addAll(result.getRecords());
            if (result.getRecords().isEmpty() || all.size() >= result.getTotal()) {
                return all;
            }
            if (pageNum >= 50) {
                log.warn("[项目组] 我的项目组全量拉取达到 50 页上限（约 {} 条），结果截断返回", all.size());
                return all;
            }
            pageNum++;
        }
    }

    /**
     * 转让组长。
     * <p>
     * 业务流程：
     * <ol>
     *   <li>校验项目存在；</li>
     *   <li>校验新组长用户存在；</li>
     *   <li>旧 leader 设为 member；</li>
     *   <li>新 leader 设为 leader（若未加入项目组则自动加入）；</li>
     *   <li>更新 biz_project.manager_id；</li>
     *   <li>写审计日志。</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferLeader(Long projectId, Long newLeaderId) {
        // IDOR 防护：转让组长是极高危操作，仅 admin 或项目负责人可执行（委托共享守卫单点）
        accessGuard.validateOwnership(projectId);

        Long operatorId = UserContext.getUserId();
        String operatorName = transferLogHelper.getUserName(operatorId);

        // 校验项目存在
        BizProject project = getProjectOrThrow(projectId);
        // 校验新组长用户存在
        SysUser newLeader = getUserOrThrow(newLeaderId);

        // 查询当前项目的 leader
        BizProjectTeam oldLeader = teamMapper.selectOne(new LambdaQueryWrapper<BizProjectTeam>()
                .eq(BizProjectTeam::getProjectId, projectId)
                .eq(BizProjectTeam::getRoleInTeam, ROLE_LEADER)
                .eq(BizProjectTeam::getIsActive, 1)
                .last("LIMIT 1"));

        // 旧 leader 设为 member
        if (oldLeader != null) {
            LambdaUpdateWrapper<BizProjectTeam> oldUpdate = new LambdaUpdateWrapper<>();
            oldUpdate.eq(BizProjectTeam::getId, oldLeader.getId())
                    .set(BizProjectTeam::getRoleInTeam, DEFAULT_ROLE);
            teamMapper.update(null, oldUpdate);
        }

        // 查询新组长是否已在项目组
        BizProjectTeam newLeaderTeam = teamMapper.selectOne(new LambdaQueryWrapper<BizProjectTeam>()
                .eq(BizProjectTeam::getProjectId, projectId)
                .eq(BizProjectTeam::getUserId, newLeaderId)
                .eq(BizProjectTeam::getIsActive, 1)
                .last("LIMIT 1"));

        if (newLeaderTeam != null) {
            // 已加入：更新角色为 leader
            LambdaUpdateWrapper<BizProjectTeam> newUpdate = new LambdaUpdateWrapper<>();
            newUpdate.eq(BizProjectTeam::getId, newLeaderTeam.getId())
                    .set(BizProjectTeam::getRoleInTeam, ROLE_LEADER);
            teamMapper.update(null, newUpdate);
        } else {
            // 未加入：自动加入并设置为 leader
            BizProjectTeam team = new BizProjectTeam();
            team.setProjectId(projectId);
            team.setUserId(newLeaderId);
            team.setRoleInTeam(ROLE_LEADER);
            team.setJoinedAt(LocalDateTime.now());
            team.setIsActive(1);
            team.setRemark("转让组长时自动加入");
            teamMapper.insert(team);

            // 同步创建 biz_project_assignment——委托授权写路径单点（内部查重，
            // 避免与项目转移/添加成员等操作产生的已有授权记录冲突，即修复 #2 场景）
            projectAuthService.createDirectAssignment(projectId, newLeaderId,
                    operatorId, DEFAULT_SCOPE, "转让组长自动授权");
        }

        // 更新 biz_project.manager_id
        LambdaUpdateWrapper<BizProject> projectUpdate = new LambdaUpdateWrapper<>();
        projectUpdate.eq(BizProject::getId, projectId)
                .set(BizProject::getManagerId, newLeaderId)
                .set(BizProject::getManagerName, newLeader.getName());
        projectMapper.update(null, projectUpdate);

        log.info("[项目组][转让组长] 项目={}, 旧组长={}, 新组长={}, 操作人={}",
                project.getName(),
                oldLeader != null ? oldLeader.getUserId() : "无",
                newLeader.getName(), operatorName);

        // 写审计日志
        SysUser oldLeaderUser = oldLeader != null ? userMapper.selectById(oldLeader.getUserId()) : null;
        transferLogHelper.writeTransferLog(project, "transfer_leader", oldLeaderUser, newLeader,
                operatorId, operatorName, "转让组长：" + newLeader.getName());
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
     * 校验用户存在并返回
     */
    private SysUser getUserOrThrow(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "用户ID不能为空");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "用户已禁用");
        }
        return user;
    }

    /**
     * 根据 ID 查询项目组成员 VO（含冗余字段）
     * <p>
     * 【修复M9】原实现使用 pageSize=100 分页查询后内存 filter，当记录数超过 100 时会丢数据，
     * 且每次都全表分页效率低。改为直接按 id 精确查询，单次查询且无丢数据风险。
     */
    private TeamMemberVO getTeamMemberVO(Long id) {
        return teamMapper.selectTeamMemberById(id);
    }

}
