package com.example.hbsz_idc_system_end.service;

import java.util.List;

/**
 * 批量操作服务。
 * <p>
 * 提供项目批量归档/删除能力，全部在单一事务内执行，任一条失败整体回滚。
 *
 * @author hbsz
 */
public interface IBatchOperationService {

    /**
     * 批量归档项目。
     *
     * @param ids 项目ID列表
     * @return 成功归档的数量
     */
    int batchArchiveProjects(List<Long> ids);

    /**
     * 批量删除项目。
     *
     * @param ids 项目ID列表
     * @return 成功删除的数量
     */
    int batchDeleteProjects(List<Long> ids);
}
