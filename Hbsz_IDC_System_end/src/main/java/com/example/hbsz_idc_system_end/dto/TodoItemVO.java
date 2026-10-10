package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一审批待办项视图对象。
 * <p>
 * 聚合四类审核来源（进度填报/工作量填报/文档上传/项目授权申请）的待审记录，
 * 仅提供统一展示所需字段；审核动作复用各业务模块已有接口（CAS 逻辑不变）。
 *
 * @author hbsz
 */
@Data
public class TodoItemVO implements Serializable {

    /** 来源：progress=进度填报 / workload=工作量填报 / document=文档上传 / auth=项目授权申请 */
    private String source;

    /** 业务记录ID（进度日志ID/工作量记录ID/文档ID/授权申请ID） */
    private Long bizId;

    /** 项目ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** 待办标题（如「光缆架设」进度填报 50%） */
    private String title;

    /** 提交人姓名 */
    private String submitter;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 补充信息（进度变化/数量单位/版本大小/授权有效期） */
    private String detail;
}
