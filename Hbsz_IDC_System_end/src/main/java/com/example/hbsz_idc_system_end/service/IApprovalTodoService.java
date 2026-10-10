package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.PageQuery;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.TodoCountVO;
import com.example.hbsz_idc_system_end.dto.TodoItemVO;

/**
 * 统一审批待办中心服务接口。
 * <p>
 * 聚合四类审核来源（进度填报/工作量填报/文档上传/项目授权申请）的待审记录，
 * 提供统一列表与分类计数。只读聚合——审核动作复用各业务模块已有接口，
 * 保证 CAS 并发防护、权限校验与通知联动逻辑不变。
 *
 * @author hbsz
 */
public interface IApprovalTodoService {

    /**
     * 分页查询当前用户的待办列表。
     * <p>
     * 可见性：仅返回当前用户具备对应审核权限的来源；
     * 非管理员额外限定为其负责的项目（管理员全量）。
     *
     * @param source 来源筛选（progress/workload/document/auth），null 查全部
     * @param query  分页参数
     * @return 待办分页结果（提交时间倒序）
     */
    PageResult<TodoItemVO> queryPage(String source, PageQuery query);

    /**
     * 按来源统计当前用户的待办数量。
     *
     * @return 分类计数（progress/workload/document/auth/total）
     */
    TodoCountVO countTodos();
}
