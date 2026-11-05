package com.example.hbsz_idc_system_end.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.hbsz_idc_system_end.dto.ImportResultVO;
import com.example.hbsz_idc_system_end.dto.MaterialFlowVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.BizMaterial;
import com.example.hbsz_idc_system_end.entity.BizMaterialAlert;
import com.example.hbsz_idc_system_end.entity.BizMaterialTransaction;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * 材料库存服务。提供材料 CRUD、入库/出库/调整（含流水记录与预警检查）、预警查询。
 * @author hbsz
 */
public interface IMaterialService extends IService<BizMaterial> {

    /** 分页查询材料（按名称/编号/分类筛选） */
    PageResult<BizMaterial> queryPage(int pageNum, int pageSize, String keyword, String category);

    /** 入库：增加库存 + 记录流水 + 解除预警 */
    void stockIn(Long materialId, BigDecimal quantity, String remark);

    /** 出库：扣减库存 + 记录流水 + 触发预警检查 */
    void stockOut(Long materialId, BigDecimal quantity, Long projectId, String remark);

    /** 调整：直接设置库存值 + 记录流水 + 预警检查 */
    void adjustStock(Long materialId, BigDecimal newStock, String remark);

    /** 查询材料的变更流水 */
    PageResult<BizMaterialTransaction> listTransactions(Long materialId, int pageNum, int pageSize);

    /** 按项目查材料出库流水（项目维度去向追溯，关联材料名称/规格/单位） */
    PageResult<MaterialFlowVO> listProjectOutbound(Long projectId, int pageNum, int pageSize);

    /** 批量导入材料（出库物料导入模板：标准型号/物料类型/厂商/当前状态/数量） */
    ImportResultVO importMaterials(MultipartFile file) throws IOException;

    /** 下载材料导入模板（xlsx，含示例行与填写说明） */
    byte[] downloadImportTemplate() throws IOException;

    /** 查询待处理预警 */
    List<BizMaterialAlert> listPendingAlerts();

    /** 处理预警（标记 resolved） */
    void resolveAlert(Long alertId);
}
