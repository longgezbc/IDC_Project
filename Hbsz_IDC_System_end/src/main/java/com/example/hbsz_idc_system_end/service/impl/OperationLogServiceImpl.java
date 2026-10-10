package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.OperationLogQueryDTO;
import com.example.hbsz_idc_system_end.dto.OperationLogVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.SysOperationLog;
import com.example.hbsz_idc_system_end.mapper.SysOperationLogMapper;
import com.example.hbsz_idc_system_end.service.IOperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 操作日志查询服务实现。
 * <p>
 * 只读服务：日志写入由 {@code OperationLogAspect} 负责，本类仅提供检索。
 * 常规路径 DEBUG、异常路径 WARN，遵循日志级别约定。
 *
 * @author hbsz
 */
@Slf4j
@Service
public class OperationLogServiceImpl implements IOperationLogService {

    private final SysOperationLogMapper operationLogMapper;

    public OperationLogServiceImpl(SysOperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public PageResult<OperationLogVO> queryPage(OperationLogQueryDTO query) {
        LocalDateTime start = parseStart(query.getStartDate());
        LocalDateTime end = parseEnd(query.getEndDate());
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "起始日期不能晚于结束日期");
        }

        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getUsername()),
                        SysOperationLog::getUsername, query.getUsername())
                .eq(StringUtils.hasText(query.getModule()),
                        SysOperationLog::getModule, query.getModule())
                .eq(StringUtils.hasText(query.getBusinessType()),
                        SysOperationLog::getBusinessType, query.getBusinessType())
                .eq(query.getStatus() != null, SysOperationLog::getStatus, query.getStatus())
                // 时间范围：startDate 含当天 00:00:00，endDate 放大到 23:59:59.999999999
                .ge(start != null, SysOperationLog::getCreatedAt, start)
                .le(end != null, SysOperationLog::getCreatedAt, end)
                .orderByDesc(SysOperationLog::getId);

        Page<SysOperationLog> page = operationLogMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        List<OperationLogVO> records = page.getRecords().stream()
                .map(OperationLogVO::from)
                .collect(Collectors.toList());
        log.debug("[操作日志] 分页查询: username={}, module={}, total={}",
                query.getUsername(), query.getModule(), page.getTotal());
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    @Override
    public Map<String, List<String>> listFilterOptions() {
        // module 注解取值为有限中文枚举（各 Controller 上标注），distinct 后全量返回
        List<String> modules = distinctValues("module");
        List<String> businessTypes = distinctValues("business_type");
        Map<String, List<String>> options = new HashMap<>(4);
        options.put("modules", modules);
        options.put("businessTypes", businessTypes);
        return options;
    }

    /**
     * 查询指定列去重后的非空值列表（保序去重，忽略 NULL 与空串）。
     *
     * @param column 数据库列名（本类内部常量传入，无注入风险）
     * @return 去重值列表
     */
    private List<String> distinctValues(String column) {
        QueryWrapper<SysOperationLog> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT " + column);
        return operationLogMapper.selectObjs(wrapper).stream()
                .filter(v -> v != null && StringUtils.hasText(String.valueOf(v)))
                .map(String::valueOf)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.toList());
    }

    /**
     * 解析查询 DTO 的时间范围字符串为起始时间（当天 00:00:00）。
     * 非法格式抛业务异常（参数校验失败），不静默吞掉。
     */
    private static LocalDateTime parseStart(String date) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "起始日期格式错误，应为 yyyy-MM-dd");
        }
    }

    private static LocalDateTime parseEnd(String date) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date).atTime(LocalTime.MAX);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "结束日期格式错误，应为 yyyy-MM-dd");
        }
    }
}
