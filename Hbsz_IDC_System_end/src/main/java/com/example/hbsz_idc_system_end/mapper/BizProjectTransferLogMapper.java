package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.hbsz_idc_system_end.dto.TransferLogQueryDTO;
import com.example.hbsz_idc_system_end.dto.TransferLogVO;
import com.example.hbsz_idc_system_end.entity.BizProjectTransferLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 项目转移审计日志 Mapper
 * <p>
 * 继承 BaseMapper 提供标准 CRUD，补充分页查询（含关联展示字段）。
 *
 * @author hbsz
 */
@Mapper
public interface BizProjectTransferLogMapper extends BaseMapper<BizProjectTransferLog> {

    /**
     * 分页查询转移日志（按操作时间倒序）
     *
     * @param page  分页对象
     * @param query 查询条件（projectId / operatedBy / 时间范围）
     * @return 分页结果
     */
    IPage<TransferLogVO> selectLogPage(IPage<TransferLogVO> page,
                                       @Param("query") TransferLogQueryDTO query);

}
