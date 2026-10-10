package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.hbsz_idc_system_end.dto.TodoItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 统一审批待办聚合数据访问层（Mapper）。
 * <p>
 * 通过 UNION ALL 聚合四类审核来源的待审记录（XML 实现，见 mapper/ApprovalTodoMapper.xml）：
 * 进度填报（biz_task_progress_log）/ 工作量填报（biz_workload_record）/
 * 文档上传（biz_document，注意状态值为中文「待审核」）/ 项目授权申请（biz_project_auth_request）。
 * <p>
 * 可见性控制：
 * <ul>
 *   <li>来源级：canXxx 布尔参数对应审核权限，无权限的分支恒返回 0 行（SQL 静态无动态拼接）；</li>
 *   <li>数据级：managerId 非空时限定项目负责人本人项目（admin 传 null 查全量）。</li>
 * </ul>
 *
 * @author hbsz
 */
@Mapper
public interface ApprovalTodoMapper {

    /**
     * 分页查询统一待办列表（按提交时间倒序）。
     *
     * @param page             分页对象（由 MyBatis-Plus 分页拦截器注入 LIMIT）
     * @param source           来源筛选（progress/workload/document/auth），null 查全部
     * @param canReviewProgress 当前用户是否有进度审核权限
     * @param canReviewWorkload 当前用户是否有工作量审核权限
     * @param canReviewDocument 当前用户是否有文档审核权限
     * @param canApproveAuth    当前用户是否有授权审批权限
     * @param managerId        非管理员的项目负责人用户ID（限定其名下项目），管理员传 null
     * @return 待办分页结果
     */
    IPage<TodoItemVO> selectTodoPage(IPage<TodoItemVO> page,
                                     @Param("source") String source,
                                     @Param("canReviewProgress") boolean canReviewProgress,
                                     @Param("canReviewWorkload") boolean canReviewWorkload,
                                     @Param("canReviewDocument") boolean canReviewDocument,
                                     @Param("canApproveAuth") boolean canApproveAuth,
                                     @Param("managerId") Long managerId);

    /**
     * 按来源分组统计待审数量。
     *
     * @param canReviewProgress 当前用户是否有进度审核权限
     * @param canReviewWorkload 当前用户是否有工作量审核权限
     * @param canReviewDocument 当前用户是否有文档审核权限
     * @param canApproveAuth    当前用户是否有授权审批权限
     * @param managerId        非管理员的项目负责人用户ID，管理员传 null
     * @return [{ source: 'progress', cnt: 12 }, ...]（仅含 cnt>0 的来源）
     */
    List<Map<String, Object>> countBySource(@Param("canReviewProgress") boolean canReviewProgress,
                                            @Param("canReviewWorkload") boolean canReviewWorkload,
                                            @Param("canReviewDocument") boolean canReviewDocument,
                                            @Param("canApproveAuth") boolean canApproveAuth,
                                            @Param("managerId") Long managerId);

    /**
     * 按项目负责人分组统计三类业务待办（进度/工作量/文档）——每日待办摘要调度专用。
     * <p>
     * 不依赖登录上下文（定时任务无 UserContext），按 biz_project.manager_id 归属，
     * 与待办中心的数据隔离口径一致。不含 auth（授权审批归 admin，单独统计）。
     *
     * @return [{ userId: 2, source: 'progress', cnt: 3 }, ...]
     */
    List<Map<String, Object>> countPendingByManager();

    /**
     * 统计待审授权申请总数（每日摘要调度通知 admin 用）。
     *
     * @return pending 状态的授权申请条数
     */
    long countPendingAuth();
}
