package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.DashboardSummaryVO;
import com.example.hbsz_idc_system_end.dto.MyTaskItemVO;
import com.example.hbsz_idc_system_end.dto.PageQuery;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.ProgressLogVO;
import com.example.hbsz_idc_system_end.dto.RiskBoardVO;

/**
 * 工作台服务接口
 * <p>
 * 提供按当前登录用户聚合的工作台数据：汇总统计、我的任务列表、我的填报列表、
 * 个人工时统计、PM 风险预警看板。数据范围严格限定为当前用户相关的数据。
 *
 * @author hbsz
 */
public interface IDashboardService {

    /**
     * 获取当前用户的工作台汇总数据。
     * <p>
     * 根据用户角色聚合：我的任务数、填报统计、待审核数、项目管理数、未读通知数等。
     *
     * @return 工作台汇总 VO
     */
    DashboardSummaryVO getSummary();

    /**
     * 分页查询当前用户的任务列表（分配给当前用户的任务）。
     *
     * @param query  分页参数
     * @param status 任务状态筛选（可空）
     * @return 任务分页列表
     */
    PageResult<MyTaskItemVO> myTasks(PageQuery query, String status);

    /**
     * 分页查询当前用户提交的进度填报列表。
     *
     * @param query        分页参数
     * @param reviewStatus 审核状态筛选（pending/approved/rejected，可空）
     * @return 进度日志分页列表
     */
    PageResult<ProgressLogVO> mySubmissions(PageQuery query, String reviewStatus);

    /**
     * 获取 PM 风险预警看板数据。
     * <p>
     * 聚合当前用户所负责项目的风险信息：逾期任务、即将到期任务（3 天内）、
     * 待审核填报数、各项目风险等级。仅 PM / admin 角色可访问。
     *
     * @return 风险预警看板 VO
     */
    RiskBoardVO getRiskBoard();

}