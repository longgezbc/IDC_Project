package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.dto.PageQuery;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.TodoItemVO;
import com.example.hbsz_idc_system_end.mapper.ApprovalTodoMapper;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ApprovalTodoServiceImpl 权限可见性单元测试（纯 Mockito，不依赖 DB/Redis）。
 * <p>
 * 锁定语义：
 * <ul>
 *   <li>超级管理员仅持有通配权限 *:*:*（permsCount=1），四个来源开关应全开
 *       （回归：曾因精确匹配导致 admin 被误判为"无任何审核权限"看到空列表）；</li>
 *   <li>普通用户按实际权限逐项开启，且 managerId 限定为本人；</li>
 *   <li>无任何审核权限的用户直接返回空列表，不触发 Mapper 查询。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("审批待办中心 - 权限可见性")
class ApprovalTodoServiceImplPermissionTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long PM_ID = 2L;

    @Mock
    private ApprovalTodoMapper approvalTodoMapper;
    @Mock
    private IPermissionService permissionService;

    private ApprovalTodoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ApprovalTodoServiceImpl(approvalTodoMapper, permissionService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("admin 仅持通配权限 *:*:*：四个来源开关全开且 managerId 为 null（全量）")
    void queryPage_adminWithWildcardPermission_seesAllSources() {
        UserContext.setUserId(ADMIN_ID);
        UserContext.setUsername("admin");
        when(permissionService.getCurrentUserPermissions()).thenReturn(Set.of(CommonConstant.ALL_PERMISSION));
        when(permissionService.getCurrentUserRoles()).thenReturn(Set.of(CommonConstant.ROLE_CODE_ADMIN));
        IPage<TodoItemVO> page = new Page<>(1, 10);
        when(approvalTodoMapper.selectTodoPage(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(page);

        service.queryPage(null, new PageQuery());

        verify(approvalTodoMapper).selectTodoPage(any(), any(),
                eq(true), eq(true), eq(true), eq(true), isNull());
    }

    @Test
    @DisplayName("PM 仅持 workload:review：只开工作量开关，managerId 限定本人")
    void queryPage_pmWithSinglePermission_onlyWorkloadSourceOpen() {
        UserContext.setUserId(PM_ID);
        UserContext.setUsername("项目经理李某");
        when(permissionService.getCurrentUserPermissions()).thenReturn(Set.of("workload:review"));
        when(permissionService.getCurrentUserRoles()).thenReturn(Set.of("pm"));
        IPage<TodoItemVO> page = new Page<>(1, 10);
        when(approvalTodoMapper.selectTodoPage(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(page);

        service.queryPage(null, new PageQuery());

        verify(approvalTodoMapper).selectTodoPage(any(), any(),
                eq(false), eq(true), eq(false), eq(false), eq(PM_ID));
    }

    @Test
    @DisplayName("无任何审核权限：返回空列表且不触发 Mapper 查询")
    void queryPage_noReviewPermission_returnsEmptyWithoutQuery() {
        UserContext.setUserId(PM_ID);
        UserContext.setUsername("工程师王某");
        when(permissionService.getCurrentUserPermissions()).thenReturn(Set.of("workload:edit"));

        PageResult<TodoItemVO> result = service.queryPage(null, new PageQuery());

        assertTrue(result.getRecords().isEmpty());
        assertEquals(0, result.getTotal());
        verify(approvalTodoMapper, never()).selectTodoPage(any(), any(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("权限集合为 null：视为无权限返回空列表")
    void queryPage_nullPermissions_returnsEmpty() {
        UserContext.setUserId(PM_ID);
        UserContext.setUsername("工程师赵某");
        when(permissionService.getCurrentUserPermissions()).thenReturn(null);

        PageResult<TodoItemVO> result = service.queryPage(null, new PageQuery());

        assertTrue(result.getRecords().isEmpty());
        verify(approvalTodoMapper, never()).selectTodoPage(any(), any(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }
}
