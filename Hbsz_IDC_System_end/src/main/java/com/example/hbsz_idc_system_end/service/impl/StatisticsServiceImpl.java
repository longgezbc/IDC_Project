package com.example.hbsz_idc_system_end.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.example.hbsz_idc_system_end.dto.StatisticsVO;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.service.IStatisticsService;
import com.example.hbsz_idc_system_end.utils.ConvertUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 统计分析服务实现
 * <p>
 * 面向管理决策的只读统计服务，数据均来自项目表的聚合查询。关注点：
 * <ul>
 *   <li>性能：概览用单条聚合 SQL 一次取全部指标，替代多次 count/list；</li>
 *   <li>并发：Excel 导出的 5 项统计相互无依赖，使用 {@link CompletableFuture} 并行执行，
 *       总耗时由“串行求和”降为“最慢单次”，显著缩短响应时间；</li>
 *   <li>导出：基于 EasyExcel 写多 Sheet（概览/状态/类型/优先级/月度趋势）。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class StatisticsServiceImpl implements IStatisticsService {

    private final BizProjectMapper projectMapper;
    private final Executor statisticsExecutor;

    public StatisticsServiceImpl(BizProjectMapper projectMapper,
                                @org.springframework.beans.factory.annotation.Qualifier("statisticsExecutor") Executor statisticsExecutor) {
        this.projectMapper = projectMapper;
        this.statisticsExecutor = statisticsExecutor;
    }

    /**
     * 综合统计概览
     * <p>
     * 核心指标（总数/进行中/已完成/已归档/合同总额/平均进度）通过单条聚合 SQL 一次取得，
     * 再补充状态、类型、优先级分布与当年月度趋势，避免多次全表统计。
     */
    @Override
    public StatisticsVO getOverview() {
        StatisticsVO vo = new StatisticsVO();

        // 使用聚合查询替代全表加载，一次查询获取所有概览统计
        Map<String, Object> overviewStats = projectMapper.selectOverviewStatistics();
        vo.setTotalProjects(toInt(overviewStats.get("totalProjects")));
        vo.setActiveProjects(toInt(overviewStats.get("activeProjects")));
        vo.setCompletedProjects(toInt(overviewStats.get("completedProjects")));
        vo.setArchivedProjects(toInt(overviewStats.get("archivedProjects")));
        vo.setTotalContractAmount(toBigDecimal(overviewStats.get("totalContractAmount")));
        vo.setAvgProgress(toInt(overviewStats.get("avgProgress")));

        // 状态分布
        vo.setStatusDistribution(projectMapper.countByStatus());
        // 类型分布
        vo.setTypeDistribution(projectMapper.countByType());
        // 优先级分布
        vo.setPriorityDistribution(projectMapper.countByPriority());
        // 月度趋势（当年）
        vo.setMonthlyTrend(projectMapper.countByMonth(LocalDate.now().getYear()));

        return vo;
    }

    /**
     * 项目状态分布
     */
    @Override
    public List<Map<String, Object>> getStatusDistribution() {
        return projectMapper.countByStatus();
    }

    /**
     * 项目类型分布
     */
    @Override
    public List<Map<String, Object>> getTypeDistribution() {
        return projectMapper.countByType();
    }

    /**
     * 项目优先级分布
     */
    @Override
    public List<Map<String, Object>> getPriorityDistribution() {
        return projectMapper.countByPriority();
    }

    /**
     * 月度趋势
     */
    @Override
    public List<Map<String, Object>> getMonthlyTrend(Integer year) {
        if (year == null) {
            year = LocalDate.now().getYear();
        }
        log.info("[统计服务] 查询月度趋势, year={}", year);
        List<Map<String, Object>> result = projectMapper.countByMonth(year);
        log.info("[统计服务] 月度趋势查询结果, 共 {} 条记录", result.size());
        // 优化：原实现每行结果都打印一条 INFO 日志，数据量大时日志膨胀严重
        // 改为 DEBUG 级别，仅在生产排查时按需开启
        if (log.isDebugEnabled()) {
            for (Map<String, Object> row : result) {
                log.debug("[统计服务] 月度趋势数据: {}", row);
            }
        }
        return result;
    }

    /**
     * 合同金额统计
     */
    @Override
    public Map<String, Object> getAmountStatistics() {
        return projectMapper.sumContractAmount();
    }

    /**
     * 导出统计报表到 Excel（写入输出流）
     * <p>
     * 5 项统计查询彼此独立，使用 {@link CompletableFuture} 并行执行并 join 汇总，
     * 再通过 EasyExcel 的 ExcelWriter 输出概览、状态、类型、优先级、月度趋势共 5 个 Sheet。
     * 并发查询依赖公共 ForkJoinPool，高并发导出场景需关注线程池竞争。
     *
     * @param out 输出流
     */
    @Override
    public void exportReport(OutputStream out) {
        log.info("[统计服务] 开始生成统计报表Excel");
        // 优化：5 次聚合查询无依赖关系，改为 CompletableFuture 并行执行
        // 原串行实现总耗时 ≈ 5 次查询时间之和；并行后总耗时 ≈ 最慢一次查询时间
        // 预计可缩短 60-70% 导出接口响应时间
        // 使用自定义线程池 statisticsExecutor，避免占用 ForkJoinPool.commonPool 影响其他并行任务
        CompletableFuture<StatisticsVO> overviewFuture = CompletableFuture.supplyAsync(this::getOverview, statisticsExecutor);
        CompletableFuture<List<Map<String, Object>>> statusFuture = CompletableFuture.supplyAsync(this::getStatusDistribution, statisticsExecutor);
        CompletableFuture<List<Map<String, Object>>> typeFuture = CompletableFuture.supplyAsync(this::getTypeDistribution, statisticsExecutor);
        CompletableFuture<List<Map<String, Object>>> priorityFuture = CompletableFuture.supplyAsync(this::getPriorityDistribution, statisticsExecutor);
        CompletableFuture<List<Map<String, Object>>> trendFuture = CompletableFuture.supplyAsync(() -> getMonthlyTrend(null), statisticsExecutor);

        // 等待所有查询完成
        CompletableFuture.allOf(overviewFuture, statusFuture, typeFuture, priorityFuture, trendFuture).join();
        StatisticsVO overview = overviewFuture.join();
        List<Map<String, Object>> statusDist = statusFuture.join();
        List<Map<String, Object>> typeDist = typeFuture.join();
        List<Map<String, Object>> priorityDist = priorityFuture.join();
        List<Map<String, Object>> monthlyTrend = trendFuture.join();

        // 转换为 List<List<Object>> 行数据（每行一个 List）
        // Sheet1: 概览
        List<List<Object>> overviewData = new ArrayList<>();
        overviewData.add(row("指标", "数值"));
        overviewData.add(row("项目总数", overview.getTotalProjects()));
        overviewData.add(row("进行中项目", overview.getActiveProjects()));
        overviewData.add(row("已完成项目", overview.getCompletedProjects()));
        overviewData.add(row("已归档项目", overview.getArchivedProjects()));
        overviewData.add(row("合同总金额", overview.getTotalContractAmount()));
        overviewData.add(row("平均进度(%)", overview.getAvgProgress()));

        // Sheet2: 状态分布
        List<List<Object>> statusData = new ArrayList<>();
        statusData.add(row("状态", "项目数"));
        for (Map<String, Object> m : statusDist) {
            statusData.add(row(m.get("name"), m.get("value")));
        }

        // Sheet3: 类型分布
        List<List<Object>> typeData = new ArrayList<>();
        typeData.add(row("类型", "项目数"));
        for (Map<String, Object> m : typeDist) {
            typeData.add(row(m.get("name"), m.get("value")));
        }

        // Sheet4: 优先级分布
        List<List<Object>> priorityData = new ArrayList<>();
        priorityData.add(row("优先级", "项目数"));
        for (Map<String, Object> m : priorityDist) {
            priorityData.add(row(m.get("name"), m.get("value")));
        }

        // Sheet5: 月度趋势
        List<List<Object>> trendData = new ArrayList<>();
        trendData.add(row("月份", "项目数"));
        for (Map<String, Object> m : monthlyTrend) {
            trendData.add(row(m.get("name"), m.get("value")));
        }

        // 表头样式
        WriteCellStyle headStyle = new WriteCellStyle();
        headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        WriteFont headFont = new WriteFont();
        headFont.setFontHeightInPoints((short) 11);
        headFont.setBold(true);
        headStyle.setWriteFont(headFont);
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);

        // 内容样式
        WriteCellStyle contentStyle = new WriteCellStyle();
        contentStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);

        // 使用 ExcelWriter 写入多个 sheet
        try (com.alibaba.excel.ExcelWriter writer = EasyExcel.write(out)
                .registerWriteHandler(new com.alibaba.excel.write.style.HorizontalCellStyleStrategy(headStyle, contentStyle))
                .build()) {
            writeSheet(writer, 0, "概览", overviewData);
            writeSheet(writer, 1, "状态分布", statusData);
            writeSheet(writer, 2, "类型分布", typeData);
            writeSheet(writer, 3, "优先级分布", priorityData);
            writeSheet(writer, 4, "月度趋势", trendData);
        }
        log.info("[统计服务] 统计报表Excel生成完成");
    }

    private void writeSheet(com.alibaba.excel.ExcelWriter writer, int sheetNo, String sheetName, List<List<Object>> data) {
        com.alibaba.excel.write.metadata.WriteSheet sheet = EasyExcel.writerSheet(sheetNo, sheetName).head(List.class).build();
        writer.write(data, sheet);
    }

    private List<Object> row(Object... values) {
        List<Object> r = new ArrayList<>();
        for (Object v : values) {
            r.add(v);
        }
        return r;
    }

    /**
     * 安全地将对象转为 int（处理 Long/Integer/BigDecimal/BigInteger 等类型）
     * <p>
     * 优化说明：原私有实现已抽取到 {@link ConvertUtils#toInt(Object)} 工具类，
     * 此处保留薄封装便于 Service 内调用，不破坏既有代码风格。
     */
    private int toInt(Object value) {
        return ConvertUtils.toInt(value);
    }

    /**
     * 安全地将对象转为 BigDecimal
     * <p>
     * 优化说明：原私有实现已抽取到 {@link ConvertUtils#toBigDecimal(Object)} 工具类，
     * 此处保留薄封装便于 Service 内调用，不破坏既有代码风格。
     */
    private BigDecimal toBigDecimal(Object value) {
        return ConvertUtils.toBigDecimal(value);
    }

}
