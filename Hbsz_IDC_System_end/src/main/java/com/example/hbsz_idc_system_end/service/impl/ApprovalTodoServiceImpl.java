package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.PageQuery;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.TodoCountVO;
import com.example.hbsz_idc_system_end.dto.TodoItemVO;
import com.example.hbsz_idc_system_end.mapper.ApprovalTodoMapper;
import com.example.hbsz_idc_system_end.service.IApprovalTodoService;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一审批待办中心服务实现。
 * <p>
 * 只读聚合：不新增任何审核写路径，审核动作仍由各业务模块接口执行
 * （进度 reviewProgress / 工作量 reviewRecords / 文档 review / 授权 approveAuthRequest），
 * CAS 并发防护、归属校验、通知联动全部复用。
 * <p>
 * 可见性规则：
 * <ul>
 *   <li>来源级：当前用户具备哪类审核权限，就聚合哪类（无任何审核权限返回空）；</li>
 *   <li>数据级：admin 全量；其他角色限定为其负责的项目（biz_project.manager_id）。</li>
 * </ul>
 * 常规路径 DEBUG、拒绝路径 INFO，遵循日志级别约定。
 *
 * @author hbsz
 */
@Slf4j
@Service
public class ApprovalTodoServiceImpl implements IApprovalTodoService {

    private static final Set<String> VALID_SOURCES = Set.of("progress", "workload", "document", "auth");

    private final ApprovalTodoMapper approvalTodoMapper;
    private final IPermissionService permissionService;

    public ApprovalTodoServiceImpl(ApprovalTodoMapper approvalTodoMapper,
                                   IPermissionService permissionService) {
        this.approvalTodoMapper = approvalTodoMapper;
        this.permissionService = permissionService;
    }

    @Override
    public PageResult<TodoItemVO> queryPage(String source, PageQuery query) {
        if (source != null && !VALID_SOURCES.contains(source)) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "无效的待办来源：" + source);
        }
        boolean canReviewProgress = hasPermission("task:progress:review");
        boolean canReviewWorkload = hasPermission("workload:review");
        boolean canReviewDocument = hasPermission("document:review");
        boolean canApproveAuth = hasPermission("project:auth:approve");

        if (!canReviewProgress && !canReviewWorkload && !canReviewDocument && !canApproveAuth) {
            log.info("[待办中心] 用户无任何审核权限，返回空列表: userId={}", UserContext.getUserId());
            return new PageResult<>(List.of(), 0, query.getPageSize(), query.getPageNum());
        }

        IPage<TodoItemVO> page = approvalTodoMapper.selectTodoPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                source, canReviewProgress, canReviewWorkload, canReviewDocument, canApproveAuth,
                resolveManagerId());
        log.debug("[待办中心] 待办查询: source={}, total={}", source, page.getTotal());
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getSize(), page.getCurrent());
    }

    @Override
    public TodoCountVO countTodos() {
        boolean canReviewProgress = hasPermission("task:progress:review");
        boolean canReviewWorkload = hasPermission("workload:review");
        boolean canReviewDocument = hasPermission("document:review");
        boolean canApproveAuth = hasPermission("project:auth:approve");

        TodoCountVO vo = new TodoCountVO();
        if (!canReviewProgress && !canReviewWorkload && !canReviewDocument && !canApproveAuth) {
            return vo;
        }
        List<Map<String, Object>> rows = approvalTodoMapper.countBySource(
                canReviewProgress, canReviewWorkload, canReviewDocument, canApproveAuth,
                resolveManagerId());
        for (Map<String, Object> row : rows) {
            vo.add(String.valueOf(row.get("source")), ((Number) row.get("cnt")).longValue());
        }
        vo.finish();
        return vo;
    }

    /** 当前用户是否拥有指定权限码。 */
    private boolean hasPermission(String code) {
        Set<String> permissions = permissionService.getCurrentUserPermissions();
        if (permissions == null) {
            return false;
        }
        // 与 PermissionServiceImpl.checkPermissions 语义一致：
        // 超级管理员持有通配权限 *:*:*（权限集合仅此一项），需直接放行，
        // 否则 admin 在待办中心会被误判为"无任何审核权限"而看到空列表
        return permissions.contains(CommonConstant.ALL_PERMISSION) || permissions.contains(code);
    }

    /**
     * 数据隔离：admin 全量（返回 null）；其他角色限定负责项目（返回当前用户ID）。
     */
    private Long resolveManagerId() {
        Set<String> roles = permissionService.getCurrentUserRoles();
        if (roles != null && roles.contains(CommonConstant.ROLE_CODE_ADMIN)) {
            return null;
        }
        return UserContext.getUserId();
    }
}
