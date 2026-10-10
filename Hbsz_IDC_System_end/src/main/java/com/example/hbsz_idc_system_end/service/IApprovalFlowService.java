package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.ApprovalFlowVO;

import java.util.List;
import java.util.Map;

/**
 * 审批流程图服务接口。
 * <p>
 * 将业务审批数据（授权申请、进度审核、项目转移）转换为前端流程图组件
 * 所需的通用 {nodes, links} 数据结构，并提供配套的简要列表查询。
 *
 * @author hbsz
 */
public interface IApprovalFlowService {

    /**
     * 获取项目授权申请的审批流程图数据。
     *
     * @param id 授权申请ID
     * @return 流程图 VO
     * @throws com.example.hbsz_idc_system_end.common.exception.BusinessException 授权申请不存在
     */
    ApprovalFlowVO getAuthRequestFlow(Long id);

    /**
     * 获取任务进度审核的审批流程图数据。
     *
     * @param id 进度填报记录ID
     * @return 流程图 VO
     * @throws com.example.hbsz_idc_system_end.common.exception.BusinessException 进度记录不存在
     */
    ApprovalFlowVO getProgressReviewFlow(Long id);

    /**
     * 获取项目转移的审批流程图数据。
     *
     * @param id 转移日志ID
     * @return 流程图 VO
     * @throws com.example.hbsz_idc_system_end.common.exception.BusinessException 转移记录不存在
     */
    ApprovalFlowVO getProjectTransferFlow(Long id);

    /**
     * 获取待审批的授权申请简要列表（供流程图列表页使用）。
     *
     * @return 授权申请简要列表（按创建时间倒序）
     */
    List<Map<String, Object>> listAuthRequests();

    /**
     * 获取待审核的进度填报简要列表（供流程图列表页使用）。
     *
     * @return 进度填报简要列表（按填报时间倒序）
     */
    List<Map<String, Object>> listProgressReviews();

    /**
     * 获取项目转移日志简要列表（供流程图列表页使用）。
     *
     * @return 转移日志简要列表（按操作时间倒序）
     */
    List<Map<String, Object>> listProjectTransfers();
}
