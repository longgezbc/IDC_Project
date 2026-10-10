package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.OperationLogQueryDTO;
import com.example.hbsz_idc_system_end.dto.OperationLogVO;
import com.example.hbsz_idc_system_end.dto.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 操作日志查询服务接口。
 * <p>
 * 提供操作日志（sys_operation_log，由 OperationLogAspect 异步落库的审计数据）
 * 的分页检索与筛选选项查询，点亮沉没的审计数据。
 *
 * @author hbsz
 */
public interface IOperationLogService {

    /**
     * 分页查询操作日志。
     * <p>按操作时间倒序，支持操作人（模糊）/模块/业务类型/状态/时间范围组合筛选。
     *
     * @param query 查询条件（含分页参数）
     * @return 分页结果
     */
    PageResult<OperationLogVO> queryPage(OperationLogQueryDTO query);

    /**
     * 查询筛选下拉选项（去重后的模块与业务类型列表）。
     * <p>用于前端筛选区下拉框，避免硬编码枚举。
     *
     * @return { modules: [...], businessTypes: [...] }
     */
    Map<String, List<String>> listFilterOptions();
}
