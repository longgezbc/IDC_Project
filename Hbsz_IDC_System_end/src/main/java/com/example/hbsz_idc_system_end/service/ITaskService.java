package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.*;

import java.util.List;

/**
 * 任务进度服务接口
 * <p>
 * 负责项目下任务（工序/节点）的管理与进度跟踪：任务增删改查、进度填报、
 * 项目整体进度汇总，以及关键节点的临期提醒。
 *
 * @author hbsz
 */
public interface ITaskService {

    /**
     * 查询项目任务列表
     *
     * @param projectId 项目ID
     * @return 该项目下的任务列表
     */
    List<TaskVO> listByProject(Long projectId);

    /**
     * 查询任务详情
     *
     * @param id 任务ID
     * @return 任务详情 VO
     */
    TaskVO getDetail(Long id);

    /**
     * 新增任务
     *
     * @param projectId 所属项目ID
     * @param dto       任务保存参数
     * @return 新增后的任务 VO
     */
    TaskVO create(Long projectId, TaskSaveDTO dto);

    /**
     * 更新任务
     *
     * @param id  任务ID
     * @param dto 任务保存参数
     * @return 更新后的任务 VO
     */
    TaskVO update(Long id, TaskSaveDTO dto);

    /**
     * 删除任务
     *
     * @param id 任务ID
     */
    void delete(Long id);

    /**
     * 提交进度填报（工程师发起，进入项目经理审核流程）
     * <p>
     * 工程师提交进度后不会立即更新任务进度，而是创建一条 pending 状态的进度日志，
     * 等待项目经理审核通过后才会回写到 {@code biz_task.progress}。
     *
     * @param id  任务ID
     * @param dto 进度填报参数（进度百分比、施工图片等）
     * @return 进度日志 VO（含日志ID、审核状态等）
     */
    ProgressLogVO reportProgress(Long id, ProgressReportDTO dto);

    /**
     * 审核进度填报（项目经理审核工程师提交的进度日志）
     * <p>
     * 审核通过时：将 progressLog 标记为 approved，并把进度值回写到 {@code biz_task.progress}，
     * 同时同步任务状态（100%→已完成，>0→进行中）和项目整体进度；
     * 审核驳回时：仅将 progressLog 标记为 rejected，不影响任务实际进度。
     *
     * @param logId 进度日志ID
     * @param dto   审核参数（是否通过、审核意见）
     * @return 审核后的进度日志 VO
     */
    ProgressLogVO reviewProgress(Long logId, ProgressReviewDTO dto);

    /**
     * 查询待审核进度日志列表
     * <p>
     * 默认按填报时间倒序返回 review_status='pending' 的日志。
     * 可选传入 projectId 限定范围。
     *
     * @param projectId 项目ID，为 null 时返回全部待审核
     * @return 待审核进度日志列表
     */
    List<ProgressLogVO> listPendingProgress(Long projectId);

    /**
     * 分页查询待审核进度日志
     * <p>
     * 按 reportTime 倒序（+ id 倒序兜底）分页返回 review_status='pending' 的日志，
     * 排序字段稳定，保证翻页不重复、不遗漏。可选传入 projectId 限定范围。
     * 参数非法值（页码 &lt;1、每页条数 &lt;1 或 &gt;100）由 {@link PageQuery} 及调用方校正。
     *
     * @param projectId 项目ID，为 null 时返回全部待审核
     * @param query    分页参数（页码从 1 开始）
     * @return 待审核进度日志分页结果
     */
    PageResult<ProgressLogVO> pagePendingProgress(Long projectId, PageQuery query);

    /**
     * 查询指定任务的进度日志列表
     * <p>
     * 用于查看任务的历史进度填报记录（含审核状态），按填报时间倒序返回。
     *
     * @param taskId 任务ID
     * @return 进度日志列表
     */
    List<ProgressLogVO> listProgressLogs(Long taskId);

    /**
     * 项目整体进度
     * <p>
     * 汇总项目下各任务进度，计算项目整体完成度。
     *
     * @param projectId 项目ID
     * @return 项目进度 VO
     */
    ProjectProgressVO getProjectProgress(Long projectId);

    /**
     * 项目关键节点提醒
     *
     * @param projectId 项目ID
     * @return 该项目下临近截止的关键任务列表
     */
    List<TaskVO> getUpcomingKeyTasks(Long projectId);

    /**
     * 全部关键节点提醒
     *
     * @return 全局范围内临近截止的关键任务列表
     */
    List<TaskVO> getAllUpcomingKeyTasks();

}