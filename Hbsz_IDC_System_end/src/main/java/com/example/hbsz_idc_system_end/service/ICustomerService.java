package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.CustomerQueryDTO;
import com.example.hbsz_idc_system_end.dto.CustomerSaveDTO;
import com.example.hbsz_idc_system_end.dto.CustomerVO;
import com.example.hbsz_idc_system_end.dto.ImportResultVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 客户服务接口。
 * <p>
 * 提供客户管理的核心业务能力：分页查询、详情、新建、更新、删除，
 * 以及用于下拉选择的"全部启用客户"列表。
 * 删除时需校验关联项目，避免误删导致项目孤儿数据。
 *
 * @author hbsz
 */
public interface ICustomerService {

    /**
     * 分页查询客户列表（按关键字、状态过滤）。
     *
     * @param query 查询条件
     * @return 分页结果，每条记录附带关联项目数
     */
    PageResult<CustomerVO> queryPage(CustomerQueryDTO query);

    /**
     * 查询客户详情（含关联项目数）。
     *
     * @param id 客户ID
     * @return 客户详情
     */
    CustomerVO getDetail(Long id);

    /**
     * 新建客户。客户编号唯一性由本方法校验。
     *
     * @param dto 客户保存数据
     * @return 新建后的客户
     */
    CustomerVO create(CustomerSaveDTO dto);

    /**
     * 更新客户。客户编号唯一性校验时会排除自身。
     *
     * @param id  客户ID
     * @param dto 客户保存数据
     * @return 更新后的客户
     */
    CustomerVO update(Long id, CustomerSaveDTO dto);

    /**
     * 删除客户。若存在关联项目则禁止删除并抛出业务异常。
     *
     * @param id 客户ID
     */
    void delete(Long id);

    /**
     * 查询所有启用状态的客户列表（用于下拉选择，不分页）。
     *
     * @return 启用客户列表（不含禁用客户，不含 projectCount）
     */
    List<CustomerVO> listAllEnabled();

    /**
     * 批量导入客户（Excel）。
     * <p>
     * 逐行解析 Excel 并调用 {@link #create(CustomerSaveDTO)} 创建客户，
     * 单行失败不影响其他行，最终返回汇总结果。
     *
     * @param file Excel 文件
     * @return 导入结果汇总
     * @throws IOException 读取文件失败时抛出
     */
    ImportResultVO importCustomers(MultipartFile file) throws IOException;

    /**
     * 生成客户导入模板（空 Excel，仅含表头）的字节内容。
     *
     * @return Excel 文件字节
     * @throws IOException 生成文件失败时抛出
     */
    byte[] downloadImportTemplate() throws IOException;

}
