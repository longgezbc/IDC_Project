package com.example.hbsz_idc_system_end.service.workload;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.WorkloadItemSaveDTO;
import com.example.hbsz_idc_system_end.entity.BizWorkloadItem;
import com.example.hbsz_idc_system_end.entity.BizWorkloadRecord;
import com.example.hbsz_idc_system_end.mapper.BizWorkloadItemMapper;
import com.example.hbsz_idc_system_end.mapper.BizWorkloadRecordMapper;
import com.example.hbsz_idc_system_end.vo.WorkloadItemVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 工作量清单维护协作类（原 WorkloadServiceImpl 清单维护域拆出）
 * <p>
 * 职责：作业清单查询/新增/编辑/删除 + 标准模板导入（106 项 / 15 类）。
 * 事务注解在协作类方法上（经门面跨 bean 调用生效，勿在类内部自调用事务方法）。
 *
 * @author hbsz
 */
@Slf4j
@Component
public class WorkloadItemManager {

    private final BizWorkloadItemMapper itemMapper;
    private final BizWorkloadRecordMapper recordMapper;
    private final ObjectMapper objectMapper;
    private final WorkloadQuerySupport support;

    /** 标准作业清单模板资源（106 项 / 15 类），启动时加载一次 */
    private volatile List<WorkloadItemSaveDTO> templateItems;

    public WorkloadItemManager(BizWorkloadItemMapper itemMapper,
                               BizWorkloadRecordMapper recordMapper,
                               ObjectMapper objectMapper,
                               WorkloadQuerySupport support) {
        this.itemMapper = itemMapper;
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
        this.support = support;
    }

    /**
     * 启动时预加载标准模板清单。
     * <p>
     * 模板为 classpath 资源 workload-template.json（由《工作量统计模板.xlsx》转换生成），
     * 加载失败仅告警不阻断启动——导入功能不可用，其余功能不受影响。
     */
    @PostConstruct
    void loadTemplate() {
        try (InputStream in = new ClassPathResource("workload-template.json").getInputStream()) {
            templateItems = objectMapper.readValue(in, new TypeReference<List<WorkloadItemSaveDTO>>() {
            });
            log.info("[工作量统计] 标准作业清单模板加载成功：{} 项", templateItems.size());
        } catch (IOException e) {
            templateItems = List.of();
            log.warn("[工作量统计] 标准作业清单模板加载失败，模板导入功能不可用: {}", e.getMessage());
        }
    }

    public List<WorkloadItemVO> listItems(Long projectId, LocalDate statisticsDate) {
        support.checkProjectAccess(projectId);
        List<BizWorkloadItem> items = support.listItemsByProject(projectId);
        Map<Long, Map<String, Object>> aggregates = support.aggregateByItem(projectId, statisticsDate);
        return items.stream().map(item -> support.toItemVO(item, aggregates.get(item.getId()))).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkloadItemVO createItem(WorkloadItemSaveDTO dto) {
        support.checkProjectAccess(dto.getProjectId());
        support.checkProjectExists(dto.getProjectId());
        BizWorkloadItem item = new BizWorkloadItem();
        applySaveDTO(item, dto);
        if (item.getSortOrder() == null) {
            // 追加到末尾：当前最大排序号 + 1
            item.setSortOrder((int) (itemMapper.selectCount(new LambdaQueryWrapper<BizWorkloadItem>()
                    .eq(BizWorkloadItem::getProjectId, dto.getProjectId())) + 1));
        }
        itemMapper.insert(item);
        return support.toItemVO(item, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkloadItemVO updateItem(Long id, WorkloadItemSaveDTO dto) {
        BizWorkloadItem item = requireItem(id);
        support.checkProjectAccess(item.getProjectId());
        support.checkProjectExists(dto.getProjectId());
        applySaveDTO(item, dto);
        itemMapper.updateById(item);
        return support.toItemVO(item, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        BizWorkloadItem item = requireItem(id);
        support.checkProjectAccess(item.getProjectId());
        // 级联删除该清单项的全部填报明细，避免残留孤儿记录
        recordMapper.delete(new LambdaQueryWrapper<BizWorkloadRecord>()
                .eq(BizWorkloadRecord::getItemId, id));
        itemMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int importTemplate(Long projectId) {
        support.checkProjectAccess(projectId);
        support.checkProjectExists(projectId);
        if (templateItems == null || templateItems.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "标准模板未加载，请联系管理员检查部署包");
        }
        // 项目已有清单时跳过，防止重复导入（前端按钮二次确认）
        long existing = itemMapper.selectCount(new LambdaQueryWrapper<BizWorkloadItem>()
                .eq(BizWorkloadItem::getProjectId, projectId));
        if (existing > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS,
                    "该项目已有 " + existing + " 条清单项，如需重新导入请先清空清单");
        }
        int sortOrder = 0;
        for (WorkloadItemSaveDTO dto : templateItems) {
            BizWorkloadItem item = new BizWorkloadItem();
            item.setProjectId(projectId);
            item.setCategory(dto.getCategory());
            item.setWorkScope(dto.getWorkScope());
            item.setRatedWorkload(dto.getRatedWorkload());
            item.setUnit(dto.getUnit());
            item.setPerformanceScore(dto.getPerformanceScore() != null ? dto.getPerformanceScore() : BigDecimal.ONE);
            item.setTotalQuantity(BigDecimal.ZERO);
            item.setRemark(dto.getRemark());
            item.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : ++sortOrder);
            itemMapper.insert(item);
        }
        log.info("[工作量统计] 项目 {} 导入标准模板清单 {} 项", projectId, templateItems.size());
        return templateItems.size();
    }

    /** DTO 字段应用到实体（新增/更新共用） */
    private void applySaveDTO(BizWorkloadItem item, WorkloadItemSaveDTO dto) {
        item.setProjectId(dto.getProjectId());
        item.setCategory(dto.getCategory());
        item.setWorkScope(dto.getWorkScope());
        item.setRatedWorkload(dto.getRatedWorkload());
        item.setUnit(dto.getUnit());
        item.setPerformanceScore(dto.getPerformanceScore() != null ? dto.getPerformanceScore() : BigDecimal.ONE);
        item.setTotalQuantity(dto.getTotalQuantity() != null ? dto.getTotalQuantity() : BigDecimal.ZERO);
        item.setRemark(dto.getRemark());
        item.setSortOrder(dto.getSortOrder());
    }

    /** 加载清单项，不存在时抛 DATA_NOT_FOUND */
    private BizWorkloadItem requireItem(Long id) {
        BizWorkloadItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "清单项不存在");
        }
        return item;
    }
}
