package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.PageQuery;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.TodoCountVO;
import com.example.hbsz_idc_system_end.dto.TodoItemVO;
import com.example.hbsz_idc_system_end.service.IApprovalTodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一审批待办中心控制器。
 * <p>
 * 业务模块：审批聚合（四类审核待办的统一入口）。
 * 基础请求路径：{@code /approval/todos}。
 * <p>
 * 权限设计：不使用单一 @RequiresPermission（四类审核权限为 OR 关系，注解无法表达），
 * 由 Service 按当前用户实际拥有的审核权限过滤来源——无任何审核权限时返回空列表，
 * 本身即是访问防护。审核动作复用各业务模块接口，权限校验在各模块 Controller 执行。
 * 只读接口，不标注 @OperationLog。
 *
 * @author hbsz
 */
@Tag(name = "审批待办中心", description = "聚合进度/工作量/文档/授权四类审核待办")
@RestController
@RequestMapping("/approval/todos")
public class ApprovalTodoController {

    private final IApprovalTodoService approvalTodoService;

    public ApprovalTodoController(IApprovalTodoService approvalTodoService) {
        this.approvalTodoService = approvalTodoService;
    }

    /**
     * 分页查询当前用户的审批待办。
     * <p>HTTP：GET /approval/todos。
     * 可见性：仅返回具备对应审核权限的来源；非管理员限定其负责的项目。
     *
     * @param source 来源筛选（progress/workload/document/auth），缺省查全部
     * @param query  分页参数
     * @return 待办分页结果（提交时间倒序）
     */
    @Operation(summary = "分页查询审批待办", description = "聚合四类审核待审记录，按用户权限与负责项目过滤")
    @GetMapping
    public Result<PageResult<TodoItemVO>> queryPage(
            @RequestParam(required = false) String source,
            PageQuery query) {
        return Result.success(approvalTodoService.queryPage(source, query));
    }

    /**
     * 按来源统计待办数量。
     * <p>HTTP：GET /approval/todos/count。用于待办中心统计卡片与导航角标。
     *
     * @return { progress, workload, document, auth, total }
     */
    @Operation(summary = "待办分类计数", description = "按来源统计待审数量（进度/工作量/文档/授权/合计）")
    @GetMapping("/count")
    public Result<TodoCountVO> countTodos() {
        return Result.success(approvalTodoService.countTodos());
    }
}
