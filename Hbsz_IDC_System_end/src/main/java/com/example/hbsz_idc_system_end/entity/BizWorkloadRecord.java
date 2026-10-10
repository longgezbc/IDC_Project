package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工作量每日填报明细实体
 * <p>
 * 对应数据库表 biz_workload_record。源自《工作量统计模板.xlsx》的"人员×作业范围"
 * 矩阵单元格：某施工人员在某日期对某清单项的完成数量。
 * <p>
 * 人员建模说明（2026-08-18 升级）：填报人员必须是项目组授权成员，
 * worker_user_id 关联 sys_user.id（成员账号），worker_name 为姓名快照（冗余展示）；
 * 历史自由文本记录的 worker_user_id 为 NULL，仅按 worker_name 兼容展示。
 * 唯一约束（item_id + record_date + worker_name）保证同一人员同一天对同一清单项
 * 仅一条记录，重复保存走更新语义。
 * <p>
 * 审核流程字段说明（2026-08-18 新增，参照 biz_task_progress_log）：
 * <ul>
 *   <li>reviewStatus：pending（待审核）/ approved（已通过）/ rejected（已驳回）；
 *       填报后默认 pending，项目经理审核通过后才计入已完成量与绩效；</li>
 *   <li>reviewerId / reviewerName / reviewTime / reviewRemark：审核人信息与意见；</li>
 *   <li>重新编辑 pending/rejected 记录会重置回 pending；approved 记录禁止直接修改。</li>
 * </ul>
 *
 * @author hbsz
 */
@Data
@TableName("biz_workload_record")
public class BizWorkloadRecord implements Serializable {

    /** 主键ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID（冗余，加速按项目+日期聚合） */
    private Long projectId;

    /** 工作量清单项ID（FK→biz_workload_item.id） */
    private Long itemId;

    /** 填报日期（工作量归属日） */
    private LocalDate recordDate;

    /** 施工人员姓名（成员姓名快照，历史自由文本记录无 worker_user_id） */
    private String workerName;

    /** 施工人员用户ID（FK→sys_user.id，须为项目组在组成员） */
    private Long workerUserId;

    /** 当日完成数量 */
    private BigDecimal quantity;

    /** 备注 */
    private String remark;

    /** 审核状态：pending待审核 / approved已通过 / rejected已驳回 */
    private String reviewStatus;

    /** 审核人用户ID（FK→sys_user.id） */
    private Long reviewerId;

    /** 审核人姓名（冗余） */
    private String reviewerName;

    /** 审核时间 */
    private LocalDateTime reviewTime;

    /** 审核意见（驳回原因） */
    private String reviewRemark;

    /** 填报人用户ID（FK→sys_user.id） */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
