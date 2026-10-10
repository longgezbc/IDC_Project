package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.ArchiveStatisticsVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.ProjectQueryDTO;
import com.example.hbsz_idc_system_end.dto.ProjectVO;

/**
 * 归档服务接口
 * <p>
 * 分层说明：
 * 接口定义对外暴露的业务能力，具体实现见 {@link com.example.hbsz_idc_system_end.service.impl.ArchiveServiceImpl}。
 * Controller / 其他 Service 通过本接口注入，便于切换实现、做单元测试 Mock。
 *
 * @author hbsz
 */
public interface IArchiveService {

    /**
     * 分页查询归档项目列表
     *
     * @param query 查询条件（含分页参数与关键字）
     * @return 分页结果
     */
    PageResult<ProjectVO> queryArchivedPage(ProjectQueryDTO query);

    /**
     * 归档统计
     *
     * @return 归档统计 VO
     */
    ArchiveStatisticsVO getStatistics();

}
