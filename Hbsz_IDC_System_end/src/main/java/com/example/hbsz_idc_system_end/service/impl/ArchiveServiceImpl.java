package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.dto.ArchiveStatisticsVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.ProjectQueryDTO;
import com.example.hbsz_idc_system_end.dto.ProjectVO;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.service.IArchiveService;
import com.example.hbsz_idc_system_end.utils.ConvertUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * 归档服务实现
 * <p>
 * 负责已归档项目的检索与统计。数据来源为项目主表中处于归档状态的记录，
 * 通过 {@link BizProjectMapper} 直接执行分页与聚合 SQL，均为只读查询，不涉及写库事务。
 *
 * @author hbsz
 */
@Slf4j
@Service
public class ArchiveServiceImpl implements IArchiveService {

    private final BizProjectMapper projectMapper;

    public ArchiveServiceImpl(BizProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    /**
     * 分页查询归档项目列表
     * <p>
     * 使用 MyBatis-Plus 分页插件在数据库层完成分页与关键字过滤，避免全量加载后内存分页。
     */
    @Override
    public PageResult<ProjectVO> queryArchivedPage(ProjectQueryDTO query) {
        Page<ProjectVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<ProjectVO> result = projectMapper.selectArchivedProjectPage(page, query.getKeyword());
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getSize(), result.getCurrent());
    }

    /**
     * 归档统计
     * <p>
     * 性能优化：使用单条聚合 SQL 一次性统计归档总数、合同总金额、本年度与本月归档数量，
     * 替代全表加载后在内存中逐条统计，显著降低数据传输量与 GC 压力。
     */
    @Override
    public ArchiveStatisticsVO getStatistics() {
        ArchiveStatisticsVO vo = new ArchiveStatisticsVO();

        // 使用聚合查询替代全表加载，一次查询获取所有归档统计
        LocalDate now = LocalDate.now();
        Map<String, Object> stats = projectMapper.selectArchiveStatistics(now.getYear(), now.getMonthValue());
        vo.setTotalCount(ConvertUtils.toInt(stats.get("totalCount")));
        vo.setTotalContractAmount(ConvertUtils.toBigDecimal(stats.get("totalContractAmount")));
        vo.setCurrentYearCount(ConvertUtils.toInt(stats.get("currentYearCount")));
        vo.setCurrentMonthCount(ConvertUtils.toInt(stats.get("currentMonthCount")));

        return vo;
    }

}
