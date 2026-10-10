package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.mapper.BizProjectAssignmentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.utils.UserContext;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 项目访问与所有权守卫（单点）
 * <p>
 * 项目所有权校验（IDOR 防护）与项目访问判定的唯一实现，供
 * {@link com.example.hbsz_idc_system_end.service.impl.ProjectAuthorizationServiceImpl} 与
 * {@link com.example.hbsz_idc_system_end.service.impl.ProjectTeamServiceImpl} 共同委托。
 * <p>
 * 重构背景：两个服务曾各自维护一份同构的 validateOwnership 拷贝，且为互相调用对方校验方法
 * 形成服务间循环依赖（以 @Lazy 掩盖）。将校验下沉到本无状态组件后：
 * <ul>
 *   <li>越权防护逻辑单点维护——修复 IDOR 类漏洞只需改一处；</li>
 *   <li>依赖方向变为 AuthorizationService / TeamService → Guard，单向无环；</li>
 *   <li>{@link IProjectAuthorizationService#canAccessProject} 保留为委托入口，
 *       既有调用方（任务/文档/工作量/日报等）不受影响。</li>
 * </ul>
 *
 * @author hbsz
 */
@Component
public class ProjectAccessGuard {

    private final BizProjectMapper projectMapper;
    private final BizProjectAssignmentMapper assignmentMapper;
    private final IPermissionService permissionService;

    public ProjectAccessGuard(BizProjectMapper projectMapper,
                              BizProjectAssignmentMapper assignmentMapper,
                              IPermissionService permissionService) {
        this.projectMapper = projectMapper;
        this.assignmentMapper = assignmentMapper;
        this.permissionService = permissionService;
    }

    /**
     * 校验当前用户对项目的所有权（IDOR 防护，写操作前置校验）
     * <ol>
     *   <li>admin 角色直接放行；</li>
     *   <li>校验项目存在，不存在抛 PROJECT_NOT_FOUND；</li>
     *   <li>校验当前用户为该项目的 manager_id，不匹配抛 FORBIDDEN。</li>
     * </ol>
     *
     * @param projectId 项目ID
     */
    public void validateOwnership(Long projectId) {
        Set<String> roles = permissionService.getCurrentUserRoles();
        // 管理员跳过校验
        if (roles != null && roles.contains(CommonConstant.ROLE_CODE_ADMIN)) {
            return;
        }
        BizProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "项目不存在");
        }
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null || !currentUserId.equals(project.getManagerId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "您不是该项目的负责人，无权执行此操作");
        }
    }

    /**
     * 校验当前用户是否有权访问指定项目（读操作前置校验）
     * <ul>
     *   <li>管理员与项目经理直接放行；</li>
     *   <li>工程师/普通用户需有显式有效授权（is_active=1 的 assignment 记录）。</li>
     * </ul>
     *
     * @param projectId 项目ID
     * @return true 有访问权限
     */
    public boolean canAccessProject(Long projectId) {
        Set<String> roles = permissionService.getCurrentUserRoles();
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        // 管理员 / 项目经理：放行
        if (roles.contains(CommonConstant.ROLE_CODE_ADMIN)
                || roles.contains(CommonConstant.ROLE_CODE_PROJECT_MANAGER)) {
            return true;
        }
        // 工程师 / 普通用户：检查显式授权
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return false;
        }
        return assignmentMapper.countActiveAssignment(projectId, userId) > 0;
    }

}
