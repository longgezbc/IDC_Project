package com.example.hbsz_idc_system_end.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.ImportResultVO;
import com.example.hbsz_idc_system_end.dto.MaterialFlowVO;
import com.example.hbsz_idc_system_end.dto.MaterialImportDTO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.BizMaterial;
import com.example.hbsz_idc_system_end.entity.BizMaterialAlert;
import com.example.hbsz_idc_system_end.entity.BizMaterialTransaction;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.mapper.BizMaterialAlertMapper;
import com.example.hbsz_idc_system_end.mapper.BizMaterialMapper;
import com.example.hbsz_idc_system_end.mapper.BizMaterialTransactionMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.service.IMaterialService;
import com.example.hbsz_idc_system_end.utils.SqlUtils;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 材料库存服务实现。
 * <p>
 * 核心逻辑：
 * <ul>
 *   <li>入库/出库/调整均在 {@code @Transactional} 内更新库存 + 记录流水 + 预警检查</li>
 *   <li>出库时库存不足抛 BusinessException</li>
 *   <li>库存低于安全线且无 pending 预警时自动生成预警记录</li>
 *   <li>入库后库存恢复到安全线以上时自动解除 pending 预警</li>
 * </ul>
 * @author hbsz
 */
@Slf4j
@Service
public class MaterialServiceImpl extends ServiceImpl<BizMaterialMapper, BizMaterial> implements IMaterialService {

    private final BizMaterialTransactionMapper transactionMapper;
    private final BizMaterialAlertMapper alertMapper;
    private final BizProjectMapper projectMapper;

    public MaterialServiceImpl(BizMaterialTransactionMapper transactionMapper, BizMaterialAlertMapper alertMapper,
                               BizProjectMapper projectMapper) {
        this.transactionMapper = transactionMapper;
        this.alertMapper = alertMapper;
        this.projectMapper = projectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importMaterials(MultipartFile file) throws IOException {
        List<MaterialImportDTO> dataList = new ArrayList<>();
        try (InputStream in = file.getInputStream()) {
            EasyExcel.read(in, MaterialImportDTO.class, new ReadListener<MaterialImportDTO>() {
                @Override
                public void invoke(MaterialImportDTO data, AnalysisContext context) {
                    dataList.add(data);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // no-op
                }
            }).sheet().doRead();
        }

        List<String> errors = new ArrayList<>();
        int rowIndex = 1; // Excel 行号（含表头，从 2 开始为数据行）
        // 同型号多行（分批领用台账）合并：数量累加，首个出现的类型/厂商/状态作为档案信息
        Map<String, MaterialImportDTO> merged = new LinkedHashMap<>();
        for (MaterialImportDTO data : dataList) {
            rowIndex++;
            if (!StringUtils.hasText(data.getStandardModel())) {
                errors.add("第" + rowIndex + "行: 标准型号不能为空");
                continue;
            }
            if (data.getQuantity() == null || data.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("第" + rowIndex + "行: 数量必须大于0");
                continue;
            }
            MaterialImportDTO exist = merged.get(data.getStandardModel());
            if (exist != null) {
                exist.setQuantity(exist.getQuantity().add(data.getQuantity()));
            } else {
                merged.put(data.getStandardModel(), data);
            }
        }

        int success = 0;
        for (Map.Entry<String, MaterialImportDTO> entry : merged.entrySet()) {
            MaterialImportDTO data = entry.getValue();
            try {
                String code = data.getStandardModel().trim();
                BizMaterial exist = this.getOne(new LambdaQueryWrapper<BizMaterial>()
                        .eq(BizMaterial::getCode, code).last("LIMIT 1"));
                String remark = buildImportRemark(data);
                if (exist == null) {
                    // 建档：编号=标准型号，名称=物料类型+型号（便于识别），分类=物料类型
                    BizMaterial m = new BizMaterial();
                    m.setCode(code);
                    m.setName(StringUtils.hasText(data.getMaterialType())
                            ? data.getMaterialType().trim() + " " + code : code);
                    m.setCategory(StringUtils.hasText(data.getMaterialType()) ? data.getMaterialType().trim() : "未分类");
                    m.setSpec(code);
                    m.setUnit("件");
                    m.setCurrentStock(BigDecimal.ZERO);
                    m.setSafetyStock(BigDecimal.ZERO);
                    m.setStatus(1);
                    m.setRemark(remark);
                    this.save(m);
                    exist = m;
                }
                // 台账数量作为初始库存入账，流水可追溯（"已领用"状态记于备注，实际去向后续按项目出库补录）
                this.stockIn(exist.getId(), data.getQuantity(), "历史台账批量导入");
                success++;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                // 错误定位用标准型号（合并后与 Excel 行号不再一一对应）
                errors.add("标准型号 " + data.getStandardModel() + ": " + reason);
                log.warn("[材料导入] {} 导入失败: {}", data.getStandardModel(), reason);
            }
        }
        log.info("[材料导入] 完成: 总行数={}, 合并后成功={}, 失败={}", dataList.size(), success, errors.size());
        return ImportResultVO.of(dataList.size(), success, errors);
    }

    private String buildImportRemark(MaterialImportDTO data) {
        String manufacturer = StringUtils.hasText(data.getManufacturer()) ? data.getManufacturer().trim() : "未知";
        String status = StringUtils.hasText(data.getStatusText()) ? data.getStatusText().trim() : "未知";
        return "厂商: " + manufacturer + "；台账状态: " + status + "；来源: 批量导入";
    }

    @Override
    public byte[] downloadImportTemplate() throws IOException {
        List<MaterialImportDTO> samples = new ArrayList<>();
        MaterialImportDTO s1 = new MaterialImportDTO();
        s1.setStandardModel("FAN-40B-1-D");
        s1.setMaterialType("网络设备风扇");
        s1.setManufacturer("H3C");
        s1.setStatusText("已领用");
        s1.setQuantity(new BigDecimal("8"));
        samples.add(s1);
        MaterialImportDTO s2 = new MaterialImportDTO();
        s2.setStandardModel("SW-A-PSR550-12A-B");
        s2.setMaterialType("网络设备电源");
        s2.setManufacturer("H3C");
        s2.setStatusText("在库");
        s2.setQuantity(new BigDecimal("4"));
        samples.add(s2);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, MaterialImportDTO.class)
                    .sheet("物料导入模板")
                    .doWrite(samples);
            return out.toByteArray();
        }
    }

    @Override
    public PageResult<BizMaterial> queryPage(int pageNum, int pageSize, String keyword, String category) {
        LambdaQueryWrapper<BizMaterial> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = SqlUtils.escapeLike(keyword.trim());
            w.and(q -> q.like(BizMaterial::getName, kw).or().like(BizMaterial::getCode, kw));
        }
        if (StringUtils.hasText(category)) {
            w.eq(BizMaterial::getCategory, category);
        }
        w.orderByDesc(BizMaterial::getCreatedAt);
        Page<BizMaterial> page = new Page<>(pageNum, pageSize);
        this.page(page, w);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getSize(), page.getCurrent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(Long materialId, BigDecimal quantity, String remark) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "入库数量必须大于0");
        }
        // 原子自增，避免并发入库丢失更新（读-改-写在并发下会互相覆盖）
        BizMaterial m = getById(materialId);
        if (m == null) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "材料不存在");
        boolean updated = this.update(new LambdaUpdateWrapper<BizMaterial>()
                .eq(BizMaterial::getId, materialId)
                .setSql("current_stock = current_stock + " + quantity.toPlainString()));
        if (!updated) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "材料不存在");
        BigDecimal before = m.getCurrentStock();
        BigDecimal after = before.add(quantity);
        recordTransaction(m, "in", quantity, before, after, null, null, remark);
        // 入库后库存恢复到安全线以上 → 解除 pending 预警
        if (after.compareTo(m.getSafetyStock()) >= 0) {
            resolveAlertsIfRestocked(materialId);
        }
        log.info("[材料入库] material={}, qty={}, {}->{}", m.getName(), quantity, before, after);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stockOut(Long materialId, BigDecimal quantity, Long projectId, String remark) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "出库数量必须大于0");
        }
        // 业务规则：材料以项目为单位领用，出库必须记录去向项目（用于流水追溯）
        if (projectId == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "出库必须指定去向项目");
        }
        BizMaterial m = getById(materialId);
        if (m == null) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "材料不存在");
        // 去向项目校验并回填项目名（流水冗余列，避免展示时二次查询）
        BizProject project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "去向项目不存在");
        BigDecimal before = m.getCurrentStock();
        // 条件原子扣减：WHERE current_stock >= quantity 在数据库层防并发超卖，
        // 读-改-写两段式在并发出库时会双双通过库存检查导致负库存
        boolean deducted = this.update(new LambdaUpdateWrapper<BizMaterial>()
                .eq(BizMaterial::getId, materialId)
                .ge(BizMaterial::getCurrentStock, quantity)
                .setSql("current_stock = current_stock - " + quantity.toPlainString()));
        if (!deducted) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "库存不足：当前 " + before + " " + m.getUnit() + "，需出库 " + quantity);
        }
        BigDecimal after = before.subtract(quantity);
        recordTransaction(m, "out", quantity, before, after, projectId, project.getName(), remark);
        // 出库后检查预警
        checkAndCreateAlert(m);
        log.info("[材料出库] material={}, qty={}, {}->{}, 去向项目={}",
                m.getName(), quantity, before, after, project.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustStock(Long materialId, BigDecimal newStock, String remark) {
        if (newStock == null || newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "调整后库存不能为负");
        }
        BizMaterial m = getById(materialId);
        if (m == null) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "材料不存在");
        BigDecimal before = m.getCurrentStock();
        BigDecimal after = newStock;
        m.setCurrentStock(after);
        updateById(m);
        // 调整流水 quantity = after - before（可为负）
        recordTransaction(m, "adjust", after.subtract(before), before, after, null, null, remark);
        checkAndCreateAlert(m);
        log.info("[材料调整] material={}, {}->{}", m.getName(), before, after);
    }

    @Override
    public PageResult<BizMaterialTransaction> listTransactions(Long materialId, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizMaterialTransaction> w = new LambdaQueryWrapper<BizMaterialTransaction>()
                .eq(BizMaterialTransaction::getMaterialId, materialId)
                .orderByDesc(BizMaterialTransaction::getCreatedAt);
        Page<BizMaterialTransaction> page = new Page<>(pageNum, pageSize);
        transactionMapper.selectPage(page, w);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getSize(), page.getCurrent());
    }

    @Override
    public PageResult<MaterialFlowVO> listProjectOutbound(Long projectId, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizMaterialTransaction> w = new LambdaQueryWrapper<BizMaterialTransaction>()
                .eq(BizMaterialTransaction::getProjectId, projectId)
                // 只取出库流水（入库/调整流水不挂项目，显式过滤保证语义与"项目领用台账"一致）
                .eq(BizMaterialTransaction::getTransactionType, "out")
                .orderByDesc(BizMaterialTransaction::getCreatedAt);
        Page<BizMaterialTransaction> page = new Page<>(pageNum, pageSize);
        transactionMapper.selectPage(page, w);
        // 批量关联材料名称/规格/单位（流水表未冗余材料信息，一次 in 查询补齐）
        Map<Long, BizMaterial> materialMap = page.getRecords().isEmpty() ? Collections.emptyMap()
                : this.listByIds(page.getRecords().stream()
                        .map(BizMaterialTransaction::getMaterialId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BizMaterial::getId, m -> m));
        List<MaterialFlowVO> voList = page.getRecords().stream().map(t -> {
            BizMaterial m = materialMap.get(t.getMaterialId());
            MaterialFlowVO vo = new MaterialFlowVO();
            vo.setId(t.getId());
            vo.setMaterialId(t.getMaterialId());
            vo.setMaterialName(m != null ? m.getName() : "（材料已删除）");
            vo.setSpec(m != null ? m.getSpec() : null);
            vo.setUnit(m != null ? m.getUnit() : null);
            vo.setQuantity(t.getQuantity());
            vo.setBeforeStock(t.getBeforeStock());
            vo.setAfterStock(t.getAfterStock());
            vo.setProjectId(t.getProjectId());
            vo.setProjectName(t.getProjectName());
            vo.setOperatorName(t.getOperatorName());
            vo.setRemark(t.getRemark());
            vo.setCreatedAt(t.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(voList, page.getTotal(), page.getSize(), page.getCurrent());
    }

    @Override
    public List<BizMaterialAlert> listPendingAlerts() {
        return alertMapper.selectList(new LambdaQueryWrapper<BizMaterialAlert>()
                .eq(BizMaterialAlert::getStatus, "pending")
                .orderByDesc(BizMaterialAlert::getCreatedAt));
    }

    @Override
    public void resolveAlert(Long alertId) {
        BizMaterialAlert alert = alertMapper.selectById(alertId);
        if (alert != null && "pending".equals(alert.getStatus())) {
            alert.setStatus("resolved");
            alert.setResolvedAt(LocalDateTime.now());
            alertMapper.updateById(alert);
            log.info("[材料预警] 已处理: alertId={}, material={}", alertId, alert.getMaterialName());
        }
    }

    /** 记录库存变更流水 */
    private void recordTransaction(BizMaterial m, String type, BigDecimal quantity,
                                    BigDecimal before, BigDecimal after,
                                    Long projectId, String projectName, String remark) {
        BizMaterialTransaction t = new BizMaterialTransaction();
        t.setMaterialId(m.getId());
        t.setTransactionType(type);
        t.setQuantity(quantity);
        t.setBeforeStock(before);
        t.setAfterStock(after);
        t.setProjectId(projectId);
        t.setProjectName(projectName);
        t.setOperatorId(UserContext.getUserId());
        t.setOperatorName(UserContext.getUsername());
        t.setRemark(remark);
        transactionMapper.insert(t);
    }

    /** 库存低于安全线且无 pending 预警时生成预警记录 */
    private void checkAndCreateAlert(BizMaterial m) {
        if (m.getCurrentStock().compareTo(m.getSafetyStock()) >= 0) return;
        Long pendingCount = alertMapper.selectCount(new LambdaQueryWrapper<BizMaterialAlert>()
                .eq(BizMaterialAlert::getMaterialId, m.getId())
                .eq(BizMaterialAlert::getStatus, "pending"));
        if (pendingCount == 0) {
            BizMaterialAlert alert = new BizMaterialAlert();
            alert.setMaterialId(m.getId());
            alert.setMaterialName(m.getName());
            alert.setCurrentStock(m.getCurrentStock());
            alert.setSafetyStock(m.getSafetyStock());
            alert.setStatus("pending");
            alertMapper.insert(alert);
            log.warn("[材料预警] 新增: material={}, stock={}, safety={}",
                    m.getName(), m.getCurrentStock(), m.getSafetyStock());
        }
    }

    /** 库存恢复到安全线以上时解除 pending 预警 */
    private void resolveAlertsIfRestocked(Long materialId) {
        List<BizMaterialAlert> pending = alertMapper.selectList(new LambdaQueryWrapper<BizMaterialAlert>()
                .eq(BizMaterialAlert::getMaterialId, materialId)
                .eq(BizMaterialAlert::getStatus, "pending"));
        for (BizMaterialAlert a : pending) {
            a.setStatus("resolved");
            a.setResolvedAt(LocalDateTime.now());
            alertMapper.updateById(a);
        }
    }
}
