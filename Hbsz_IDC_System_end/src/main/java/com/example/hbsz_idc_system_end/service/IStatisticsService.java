package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.StatisticsVO;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * 统计分析服务接口
 * <p>
 * 面向管理决策，提供项目多维度统计能力：综合概览、状态/类型/优先级分布、
 * 月度趋势、合同金额统计，以及统计报表的 Excel 导出。
 *
 * @author hbsz
 */
public interface IStatisticsService {

    /**
     * 综合统计概览
     *
     * @return 综合统计 VO
     */
    StatisticsVO getOverview();

    /**
     * 项目状态分布
     *
     * @return 每项含状态及对应数量的分布列表
     */
    List<Map<String, Object>> getStatusDistribution();

    /**
     * 项目类型分布
     *
     * @return 每项含类型及对应数量的分布列表
     */
    List<Map<String, Object>> getTypeDistribution();

    /**
     * 项目优先级分布
     *
     * @return 每项含优先级及对应数量的分布列表
     */
    List<Map<String, Object>> getPriorityDistribution();

    /**
     * 月度趋势
     *
     * @param year 年份，为空则取当前年
     * @return 各月份的项目数量趋势列表
     */
    List<Map<String, Object>> getMonthlyTrend(Integer year);

    /**
     * 合同金额统计
     *
     * @return 合同金额相关的统计指标
     */
    Map<String, Object> getAmountStatistics();

    /**
     * 导出统计报表到 Excel（写入输出流）
     * <p>
     * 由调用方（Controller）负责打开与关闭输出流，本方法只负责写入内容。
     *
     * @param out 输出流
     */
    void exportReport(OutputStream out);

}
