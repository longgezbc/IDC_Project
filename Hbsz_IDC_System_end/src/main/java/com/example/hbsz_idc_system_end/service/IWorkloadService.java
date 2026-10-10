package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.ExportRecordVO;
import com.example.hbsz_idc_system_end.dto.WorkloadItemSaveDTO;
import com.example.hbsz_idc_system_end.dto.WorkloadRecordBatchDTO;
import com.example.hbsz_idc_system_end.dto.WorkloadReviewDTO;
import com.example.hbsz_idc_system_end.vo.WorkloadItemVO;
import com.example.hbsz_idc_system_end.vo.WorkloadMatrixVO;
import com.example.hbsz_idc_system_end.vo.WorkloadMyReportVO;
import com.example.hbsz_idc_system_end.vo.WorkloadRecordVO;
import com.example.hbsz_idc_system_end.vo.WorkloadWorkerPerformanceVO;
import com.example.hbsz_idc_system_end.vo.WorkloadWorkerVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 工作量统计服务接口
 * <p>
 * 数字化《工作量统计模板.xlsx》：项目维护作业清单（作业类型×作业范围），
 * 项目组授权成员按日填报完成量（填报进入待审核状态，项目经理审核通过后
 * 计入统计），服务实时聚合已完成/剩余/今日量，并按
 * 绩效分 = Σ(数量÷额定工作量×绩效权重) 折算个人标准工日绩效。
 *
 * @author hbsz
 */
public interface IWorkloadService {

    /**
     * 查询项目可填报人员列表（项目组在组成员，组长置前）。
     *
     * @param projectId 项目ID
     * @return 成员列表（userId/姓名/组内角色）
     */
    List<WorkloadWorkerVO> listWorkers(Long projectId);

    /**
     * 查询项目工作量清单（含实时聚合统计，仅统计已审核记录）。
     *
     * @param projectId      项目ID
     * @param statisticsDate 当日统计日期（今日完成量归属日）
     * @return 清单列表（按 sortOrder 升序），每项含已完成/剩余/今日量
     */
    List<WorkloadItemVO> listItems(Long projectId, LocalDate statisticsDate);

    /**
     * 新增清单项。
     *
     * @param dto 清单项数据
     * @return 新增后的清单项 VO
     */
    WorkloadItemVO createItem(WorkloadItemSaveDTO dto);

    /**
     * 更新清单项。
     *
     * @param id  清单项ID
     * @param dto 清单项数据
     * @return 更新后的清单项 VO
     */
    WorkloadItemVO updateItem(Long id, WorkloadItemSaveDTO dto);

    /**
     * 删除清单项（同时删除其全部填报明细）。
     *
     * @param id 清单项ID
     */
    void deleteItem(Long id);

    /**
     * 从内置标准模板（106 项 / 15 类作业目录）导入清单。
     * <p>
     * 项目已有清单时跳过导入，避免重复数据。
     *
     * @param projectId 项目ID
     * @return 导入的清单项数量
     */
    int importTemplate(Long projectId);

    /**
     * 构建某日统计矩阵：清单行 × 人员列 + 每人当日绩效。
     *
     * @param projectId 项目ID
     * @param date      统计日期
     * @return 统计矩阵 VO
     */
    WorkloadMatrixVO getMatrix(Long projectId, LocalDate date);

    /**
     * 批量保存某日填报明细（填报人员必须是项目组在组成员）。
     * <p>
     * 审核语义：新记录进入待审核（pending）；覆盖 pending/rejected 记录时
     * 重置回 pending；approved 记录拒绝直接修改。
     *
     * @param dto 批量填报数据
     */
    void saveRecords(WorkloadRecordBatchDTO dto);

    /**
     * 我的快速填报上下文（"快速填报"卡片流一次性数据源）。
     * <p>
     * 返回：我参与的项目组、今日已有填报、昨日数量、最近 14 天常用清单项
     * （按填报频次排序）。projectId 为空时仅返回项目列表（供前端自动选择）。
     * 保存路径复用 {@link #saveRecords}，本方法仅提供查询上下文。
     *
     * @param projectId 项目ID（可空 = 仅查项目列表）
     * @param date      填报日期（默认今天）
     * @return 快速填报上下文
     */
    WorkloadMyReportVO getMyReportContext(Long projectId, LocalDate date);

    /**
     * 查询填报明细审核列表（按项目+日期+状态筛选，供项目经理审核）。
     *
     * @param projectId    项目ID
     * @param recordDate   填报日期（可空 = 全部日期）
     * @param reviewStatus 审核状态（可空 = 全部状态）
     * @return 填报明细列表（含作业上下文与审核信息）
     */
    List<WorkloadRecordVO> listReviewRecords(Long projectId, LocalDate recordDate, String reviewStatus);

    /**
     * 批量审核填报明细（CAS 语义：仅 pending 记录可审核）。
     * <p>
     * 权限要求：仅 admin 或项目负责人可执行（服务内校验）。
     *
     * @param dto 审核数据（ids + approve + remark）
     * @return 实际审核成功的记录数（并发冲突时可能小于 ids 数量）
     */
    int reviewRecords(WorkloadReviewDTO dto);

    /**
     * 查询人员绩效区间汇总（按绩效分降序，仅统计已审核记录）。
     *
     * @param projectId 项目ID
     * @param startDate 区间开始日期（含）
     * @param endDate   区间结束日期（含）
     * @return 人员绩效列表
     */
    List<WorkloadWorkerPerformanceVO> getWorkerPerformance(Long projectId, LocalDate startDate, LocalDate endDate);

    /**
     * 导出某日工作量统计 Excel（还原模板样式：作业类型合并 + 动态人员列 + 今日绩效行）。
     *
     * @param projectId 项目ID
     * @param date      统计日期
     * @param userId    操作人用户ID（记录导出历史）
     * @param userName  操作人姓名
     * @return 导出记录 VO（含下载ID）
     */
    ExportRecordVO exportExcel(Long projectId, LocalDate date, Long userId, String userName);

}
