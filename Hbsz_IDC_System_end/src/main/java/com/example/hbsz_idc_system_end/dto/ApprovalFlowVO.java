package com.example.hbsz_idc_system_end.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

/**
 * 审批流程图视图对象。
 * <p>
 * 通用数据结构，可表示项目授权审批、任务进度审核、项目转移审批等场景。
 * 前端（Web + 小程序）通过此 VO 渲染流程图组件。
 *
 * @author hbsz
 */
@Data
@Schema(description = "审批流程图视图对象")
public class ApprovalFlowVO {

    @Schema(description = "流程类型：auth_request=项目授权审批 / progress_review=任务进度审核 / project_transfer=项目转移审批")
    private String flowType;

    @Schema(description = "业务ID（对应 biz_project_auth_request.id / biz_task_progress_log.id / biz_project_transfer_log.id）")
    private Long bizId;

    @Schema(description = "关联项目ID")
    private Long projectId;

    @Schema(description = "关联项目名称")
    private String projectName;

    @Schema(description = "流程标题，如'项目授权审批流程'")
    private String title;

    @Schema(description = "节点列表")
    private List<FlowNode> nodes;

    @Schema(description = "连线列表")
    private List<FlowLink> links;

    /**
     * 流程节点
     */
    @Data
    @Schema(description = "流程节点")
    public static class FlowNode {
        @Schema(description = "节点ID，如 'apply'、'review'")
        private String id;

        @Schema(description = "节点名称，如 '发起申请'")
        private String name;

        @Schema(description = "状态：done=已完成 / current=当前节点 / pending=待处理 / rejected=已拒绝")
        private String status;

        @Schema(description = "操作人姓名")
        private String operator;

        @Schema(description = "操作人角色")
        private String role;

        @Schema(description = "操作时间，格式 yyyy-MM-dd HH:mm")
        private String time;

        @Schema(description = "备注")
        private String remark;
    }

    /**
     * 流程连线
     */
    @Data
    @Schema(description = "流程连线")
    public static class FlowLink {
        @Schema(description = "源节点ID")
        private String source;

        @Schema(description = "目标节点ID")
        private String target;

        @Schema(description = "连线标签，如 '提交'、'通过'")
        private String label;

        @Schema(description = "连线类型：normal(默认) / reject(红色虚线)")
        private String type;
    }
}
