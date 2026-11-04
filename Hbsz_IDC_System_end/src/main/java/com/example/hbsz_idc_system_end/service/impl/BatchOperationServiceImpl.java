package com.example.hbsz_idc_system_end.service.impl;

import com.example.hbsz_idc_system_end.service.IBatchOperationService;
import com.example.hbsz_idc_system_end.service.IProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 批量操作服务实现。
 * <p>
 * 在单一 {@code @Transactional} 内循环调用 {@link IProjectService} 的单条操作，
 * 任一项目归档/删除失败则整体回滚，保证批量操作的原子性。
 * <p>
 * 注：循环调用单条方法适用于批量规模 ≤ 100 的场景；如需更大批量，
 * 建议改为 mapper 批量 UPDATE（is_archived=1 WHERE id IN (...)）。
 *
 * @author hbsz
 */
@Slf4j
@Service
public class BatchOperationServiceImpl implements IBatchOperationService {

    private final IProjectService projectService;

    public BatchOperationServiceImpl(IProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchArchiveProjects(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        int count = 0;
        for (Long id : ids) {
            projectService.archive(id);
            count++;
        }
        log.info("[批量操作] 批量归档项目完成: count={}", count);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDeleteProjects(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        int count = 0;
        for (Long id : ids) {
            projectService.delete(id);
            count++;
        }
        log.info("[批量操作] 批量删除项目完成: count={}", count);
        return count;
    }
}
