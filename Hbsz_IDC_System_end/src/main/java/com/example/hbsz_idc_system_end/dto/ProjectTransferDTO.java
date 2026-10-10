package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目转移/授权操作入参 DTO
 * <p>
 * 由项目经理（PM）调用转移/授权接口时传入。
 * <p>
 * 转移语义（2026-08 重设计）：
 * <ul>
 *   <li>{@code toManagerId}：目标负责人（可选）。传入时移交项目所有权（biz_project.manager_id），
 *       该项目全部审批（进度/工作量/文档/授权申请）将自动流向新负责人——审批待办为实时联查
 *       manager_id，无需迁移存量数据；</li>
 *   <li>{@code toEngineerId}：目标工程师（可选）。传入时撤销项目现有全部工程师授权并授权目标工程师；</li>
 *   <li>两者至少传入一个，由 Service 层校验（字段级注解无法表达"至少一项"约束）。</li>
 * </ul>
 *
 * @author hbsz
 */
@Data
public class ProjectTransferDTO implements Serializable {

    /** 目标工程师ID（与 toManagerId 至少传一个） */
    private Long toEngineerId;

    /** 目标负责人ID（与 toEngineerId 至少传一个；传入即移交项目所有权与审批责任） */
    private Long toManagerId;

    /** 操作备注（用于审计日志） */
    private String remark;

}
