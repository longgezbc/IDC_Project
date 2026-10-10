package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.DailyReportVO;
import com.example.hbsz_idc_system_end.dto.ExportRecordVO;

import java.util.List;

/**
 * 甘特图进度日报服务接口
 * <p>
 * 定义以甘特图任务进度为核心的日报数据采集、预览、Excel 导出以及导出历史管理的业务契约。
 * 导出格式固定为 Excel（.xlsx），每个项目独占一个 Sheet，支持按项目筛选。
 *
 * @author hbsz
 */
public interface IDailyReportService {

    /**
     * 获取甘特图进度日报预览数据
     * <p>
     * 采集项目甘特图进度汇总与任务级进度明细，组装为前端可直接渲染的 VO。
     * 当 projectId 为 null 时查询全部未归档项目，否则仅查询指定项目。
     *
     * @param startDate 统计开始日期（yyyy-MM-dd，用于报告标题展示）
     * @param endDate   统计结束日期（yyyy-MM-dd，用于报告标题展示）
     * @param projectId 项目ID（可选，为 null 时查询全部项目）
     * @return 甘特图进度日报数据视图对象
     */
    DailyReportVO getDailyReport(String startDate, String endDate, Long projectId);

    /**
     * 导出甘特图进度日报 Excel 文件并保存导出记录
     * <p>
     * 采集甘特图数据后生成 Excel 文件（每个项目一个 Sheet），落盘存储并写入导出历史记录表。
     *
     * @param startDate 统计开始日期（yyyy-MM-dd）
     * @param endDate   统计结束日期（yyyy-MM-dd）
     * @param projectId 项目ID（可选，为 null 时导出全部项目）
     * @param userId    导出人ID
     * @param userName  导出人姓名
     * @return 导出记录信息（含文件名、大小等）
     */
    ExportRecordVO exportDailyReport(String startDate, String endDate, Long projectId,
                                      Long userId, String userName);

    /**
     * 分页查询当前用户的导出历史记录
     *
     * @param userId 用户ID
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @return 导出记录列表
     */
    List<ExportRecordVO> getExportHistory(Long userId, int page, int size);

    /**
     * 导出历史记录总数
     *
     * @param userId 用户ID
     * @return 记录总数
     */
    long getExportHistoryTotal(Long userId);

    /**
     * 根据记录ID获取导出记录详情（含文件路径，供下载使用）
     *
     * @param id     记录ID
     * @param userId 当前用户ID（权限校验）
     * @return 导出记录实体，不存在或不属于该用户时返回 null
     */
    com.example.hbsz_idc_system_end.entity.BizExportRecord getRecordForDownload(Long id, Long userId);

    /**
     * 删除导出记录（同时删除服务器上的文件）
     *
     * @param id     记录ID
     * @param userId 当前用户ID（权限校验）
     */
    void deleteExportRecord(Long id, Long userId);
}
