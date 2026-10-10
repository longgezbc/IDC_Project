package com.example.hbsz_idc_system_end.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.CustomerImportDTO;
import com.example.hbsz_idc_system_end.dto.CustomerQueryDTO;
import com.example.hbsz_idc_system_end.dto.CustomerSaveDTO;
import com.example.hbsz_idc_system_end.dto.CustomerVO;
import com.example.hbsz_idc_system_end.dto.ImportResultVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.BizCustomer;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.mapper.BizCustomerMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.service.ICustomerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户服务实现
 * <p>
 * 客户主数据的核心服务。关注点：
 * <ul>
 *   <li>事务：create/update/delete 均为 {@code @Transactional(rollbackFor = Exception.class)}；</li>
 *   <li>唯一性：客户编号在 create/update 时校验唯一性，避免重复；</li>
 *   <li>引用约束：delete 时校验是否有关联项目，有则禁止删除避免孤儿数据；</li>
 *   <li>性能：列表查询通过批量 GROUP BY 一次性获取各客户关联项目数，规避 N+1。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class CustomerServiceImpl implements ICustomerService {

    private final BizCustomerMapper customerMapper;
    private final BizProjectMapper projectMapper;

    public CustomerServiceImpl(BizCustomerMapper customerMapper, BizProjectMapper projectMapper) {
        this.customerMapper = customerMapper;
        this.projectMapper = projectMapper;
    }

    @Override
    public PageResult<CustomerVO> queryPage(CustomerQueryDTO query) {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(BizCustomer::getName, kw)
                    .or().like(BizCustomer::getCustomerCode, kw)
                    .or().like(BizCustomer::getContactPerson, kw)
                    .or().like(BizCustomer::getContactPhone, kw));
        }
        if (query.getStatus() != null) {
            wrapper.eq(BizCustomer::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(BizCustomer::getCreatedAt);

        Page<BizCustomer> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<BizCustomer> result = customerMapper.selectPage(page, wrapper);

        // 批量查询关联项目数，规避 N+1
        Map<Long, Integer> projectCountMap = batchProjectCount(result.getRecords());

        List<CustomerVO> voList = result.getRecords().stream()
                .map(c -> convertToVO(c, projectCountMap.getOrDefault(c.getId(), 0)))
                .collect(Collectors.toList());
        return new PageResult<>(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public CustomerVO getDetail(Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "客户ID不能为空");
        }
        BizCustomer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在");
        }
        // 查询关联项目数
        Long count = projectMapper.selectCount(new LambdaQueryWrapper<BizProject>()
                .eq(BizProject::getCustomerId, id));
        return convertToVO(customer, count != null ? count.intValue() : 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerVO create(CustomerSaveDTO dto) {
        // 校验客户编号唯一性
        checkCodeUnique(dto.getCode(), null);
        // 校验客户名称唯一性（配合 V20260815 uk_customer_name 唯一约束，存量重名库约束被跳过时由此兜底）
        checkNameUnique(dto.getName().trim(), null);
        BizCustomer customer = new BizCustomer();
        customer.setCustomerCode(dto.getCode().trim());
        customer.setName(dto.getName().trim());
        customer.setContactPerson(dto.getContactPerson().trim());
        // 联系电话可选，为空时不调用 trim() 避免 NPE
        customer.setContactPhone(dto.getContactPhone() == null ? null : dto.getContactPhone().trim());
        customer.setContactEmail(dto.getContactEmail());
        customer.setAddress(dto.getAddress());
        customer.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        customer.setRemark(dto.getRemark());
        customerMapper.insert(customer);
        log.info("[客户服务] 新建客户成功: id={}, code={}, name={}, operatorId={}",
                customer.getId(), customer.getCustomerCode(), customer.getName(),
                com.example.hbsz_idc_system_end.utils.UserContext.getUserId());
        return convertToVO(customer, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerVO update(Long id, CustomerSaveDTO dto) {
        BizCustomer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在");
        }
        // 校验客户编号唯一性（排除自身）
        checkCodeUnique(dto.getCode(), id);
        // 校验客户名称唯一性（排除自身；配合 V20260815 uk_customer_name 唯一约束）
        checkNameUnique(dto.getName().trim(), id);

        customer.setCustomerCode(dto.getCode().trim());
        customer.setName(dto.getName().trim());
        customer.setContactPerson(dto.getContactPerson().trim());
        // 联系电话可选，为空时不调用 trim() 避免 NPE
        customer.setContactPhone(dto.getContactPhone() == null ? null : dto.getContactPhone().trim());
        customer.setContactEmail(dto.getContactEmail());
        customer.setAddress(dto.getAddress());
        if (dto.getStatus() != null) {
            customer.setStatus(dto.getStatus());
        }
        customer.setRemark(dto.getRemark());
        customerMapper.updateById(customer);
        log.info("[客户服务] 更新客户成功: id={}, code={}, name={}, operatorId={}",
                customer.getId(), customer.getCustomerCode(), customer.getName(),
                com.example.hbsz_idc_system_end.utils.UserContext.getUserId());
        return convertToVO(customer, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizCustomer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在");
        }
        // 校验是否有关联项目，有则禁止删除
        Long projectCount = projectMapper.selectCount(new LambdaQueryWrapper<BizProject>()
                .eq(BizProject::getCustomerId, id));
        if (projectCount != null && projectCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "该客户存在 " + projectCount + " 个关联项目，无法删除，请先处理关联项目");
        }
        customerMapper.deleteById(id);
        log.info("[客户服务] 删除客户成功: id={}, code={}, name={}, operatorId={}",
                id, customer.getCustomerCode(), customer.getName(),
                com.example.hbsz_idc_system_end.utils.UserContext.getUserId());
    }

    @Override
    public List<CustomerVO> listAllEnabled() {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomer::getStatus, 1)
                .orderByAsc(BizCustomer::getName);
        List<BizCustomer> list = customerMapper.selectList(wrapper);
        // 下拉选择不需要 projectCount，传 0 即可
        return list.stream().map(c -> convertToVO(c, 0)).collect(Collectors.toList());
    }

    /**
     * 校验客户编号唯一性。
     *
     * @param code      客户编号
     * @param excludeId 排除的客户ID（更新时传自身ID，新建时传 null）
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomer::getCustomerCode, code);
        if (excludeId != null) {
            wrapper.ne(BizCustomer::getId, excludeId);
        }
        Long count = customerMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "客户编号已存在: " + code);
        }
    }

    /**
     * 校验客户名称唯一性。
     * <p>
     * 配合 V20260815 迁移新增的 uk_customer_name 唯一约束：存量数据存在重名而约束被跳过的库，
     * 由本校验阻止新增/修改产生重复名称，给出友好提示而非触发数据库唯一键冲突。
     *
     * @param name      客户名称（建议传入 trim 后的值，与落库值一致）
     * @param excludeId 更新时排除自身的ID，新增时传 null
     */
    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomer::getName, name);
        if (excludeId != null) {
            wrapper.ne(BizCustomer::getId, excludeId);
        }
        Long count = customerMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "客户名称已存在: " + name);
        }
    }

    /**
     * 批量查询客户关联项目数，返回 customerId -> projectCount 映射，规避 N+1。
     * 当前实现：对客户列表做一次 IN 查询，使用 GROUP BY 聚合。
     * 若客户列表为空，返回空 Map。
     */
    private Map<Long, Integer> batchProjectCount(List<BizCustomer> customers) {
        if (customers == null || customers.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = customers.stream().map(BizCustomer::getId).collect(Collectors.toList());
        // 【修复M10】原实现加载全部项目实体后内存 for 循环计数，数据量大时内存与网络开销高；
        // 改为 GROUP BY 聚合查询，DB 侧一次完成计数。
        List<Map<String, Object>> rows = projectMapper.countByCustomerIds(ids);
        Map<Long, Integer> map = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object cidObj = row.get("customerId");
                Object cntObj = row.get("cnt");
                if (cidObj != null && cntObj != null) {
                    map.put(((Number) cidObj).longValue(), ((Number) cntObj).intValue());
                }
            }
        }
        return map;
    }

    /**
     * 实体转 VO，填充 projectCount。
     */
    private CustomerVO convertToVO(BizCustomer customer, int projectCount) {
        CustomerVO vo = new CustomerVO();
        vo.setId(customer.getId());
        vo.setCustomerCode(customer.getCustomerCode());
        vo.setName(customer.getName());
        vo.setContactPerson(customer.getContactPerson());
        vo.setContactPhone(customer.getContactPhone());
        vo.setContactEmail(customer.getContactEmail());
        vo.setAddress(customer.getAddress());
        vo.setStatus(customer.getStatus());
        vo.setRemark(customer.getRemark());
        vo.setCreatedAt(customer.getCreatedAt());
        vo.setUpdatedAt(customer.getUpdatedAt());
        vo.setProjectCount(projectCount);
        return vo;
    }

    /**
     * 批量导入客户（Excel）。
     * <p>
     * 使用 EasyExcel 同步读取整张 Sheet，逐行转换为 {@link CustomerSaveDTO} 并调用
     * {@link #create(CustomerSaveDTO)} 落库。单行异常被捕获并记录到 errors 列表，
     * 不影响其他行的导入。
     *
     * @param file Excel 文件
     * @return 导入结果汇总
     * @throws IOException 读取文件失败时抛出
     */
    @Override
    public ImportResultVO importCustomers(MultipartFile file) throws IOException {
        List<CustomerImportDTO> dataList = new ArrayList<>();
        try (InputStream in = file.getInputStream()) {
            EasyExcel.read(in, CustomerImportDTO.class, new ReadListener<CustomerImportDTO>() {
                @Override
                public void invoke(CustomerImportDTO data, AnalysisContext context) {
                    dataList.add(data);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // no-op
                }
            }).sheet().doRead();
        }

        List<String> errors = new ArrayList<>();
        int success = 0;
        int rowIndex = 1; // Excel 行号从 1 开始（不含表头）
        for (CustomerImportDTO data : dataList) {
            rowIndex++;
            try {
                CustomerSaveDTO dto = new CustomerSaveDTO();
                dto.setCode(data.getCode());
                dto.setName(data.getName());
                dto.setContactPerson(data.getContactPerson());
                dto.setContactPhone(data.getContactPhone());
                dto.setContactEmail(data.getContactEmail());
                dto.setAddress(data.getAddress());
                dto.setRemark(data.getRemark());
                this.create(dto);
                success++;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                errors.add("第" + rowIndex + "行: " + reason);
                log.warn("[客户导入] 第{}行导入失败: {}", rowIndex, reason);
            }
        }
        log.info("[客户导入] 完成: 总数={}, 成功={}, 失败={}", dataList.size(), success, errors.size());
        return ImportResultVO.of(dataList.size(), success, errors);
    }

    /**
     * 生成客户导入模板的字节内容。
     * <p>
     * 模板结构（避免用户盲猜字段规则导致导入失败）：
     * <ul>
     *   <li>Sheet1「客户导入模板」：表头 + 2 行真实合法示例数据；</li>
     *   <li>Sheet2「填写说明」：每个字段的必填性与格式要求，
     *       与 {@link CustomerSaveDTO} 的校验注解一一对应。</li>
     * </ul>
     *
     * @return Excel 文件字节
     * @throws IOException 生成文件失败时抛出
     */
    @Override
    public byte[] downloadImportTemplate() throws IOException {
        List<CustomerImportDTO> samples = new ArrayList<>();
        CustomerImportDTO sample1 = new CustomerImportDTO();
        sample1.setCode("CUST-2026-001");
        sample1.setName("北京华邦科技有限公司");
        sample1.setContactPerson("王经理");
        sample1.setContactPhone("010-88886666");
        sample1.setContactEmail("wang@huabang.com");
        sample1.setAddress("北京市朝阳区XX路8号");
        samples.add(sample1);

        CustomerImportDTO sample2 = new CustomerImportDTO();
        sample2.setCode("CUST-2026-002");
        sample2.setName("上海数据港股份有限公司");
        sample2.setContactPerson("李主任");
        sample2.setContactPhone("021-66668888");
        sample2.setAddress("上海市浦东新区XX大道100号");
        sample2.setRemark("重点客户");
        samples.add(sample2);

        List<List<String>> guide = new ArrayList<>();
        guide.add(java.util.Arrays.asList(
                "【使用须知】① 请先删除或修改第2-3行示例数据，再填写真实数据；"
                        + "② 每行一个客户，勿修改表头行；③ 客户编号在系统内唯一，重复导入会提示失败；"
                        + "④ 导入结果按行提示错误原因（如：第4行: 客户名称不能为空）。"));
        guide.add(java.util.Arrays.asList("列名", "是否必填", "填写说明"));
        guide.add(java.util.Arrays.asList("客户编号", "必填",
                "客户唯一编号，不超过50个字符（建议格式：CUST-年份-序号，如 CUST-2026-001）"));
        guide.add(java.util.Arrays.asList("客户名称", "必填",
                "客户公司全称，不超过200个字符"));
        guide.add(java.util.Arrays.asList("联系人", "必填",
                "客户方对接人姓名，不超过50个字符"));
        guide.add(java.util.Arrays.asList("联系电话", "选填",
                "不超过20个字符，支持手机或座机（如：010-88886666）"));
        guide.add(java.util.Arrays.asList("联系邮箱", "选填",
                "标准邮箱格式，如：wang@huabang.com"));
        guide.add(java.util.Arrays.asList("客户地址", "选填",
                "不超过300个字符"));
        guide.add(java.util.Arrays.asList("备注", "选填",
                "不超过500个字符"));

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (com.alibaba.excel.ExcelWriter writer = EasyExcel.write(out).build()) {
            com.alibaba.excel.write.metadata.WriteSheet mainSheet =
                    EasyExcel.writerSheet(0, "客户导入模板").head(CustomerImportDTO.class).build();
            writer.write(samples, mainSheet);
            com.alibaba.excel.write.metadata.WriteSheet guideSheet =
                    EasyExcel.writerSheet(1, "填写说明").build();
            writer.write(guide, guideSheet);
        }
        return out.toByteArray();
    }

}
