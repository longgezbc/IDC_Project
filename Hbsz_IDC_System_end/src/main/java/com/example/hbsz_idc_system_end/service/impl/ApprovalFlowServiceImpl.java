package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.ApprovalFlowVO;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizProjectAuthRequest;
import com.example.hbsz_idc_system_end.entity.BizProjectTransferLog;
import com.example.hbsz_idc_system_end.entity.BizTaskProgressLog;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.BizProjectAuthRequestMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectTransferLogMapper;
import com.example.hbsz_idc_system_end.mapper.BizTaskProgressLogMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.IApprovalFlowService;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批流程图服务实现。
 * <p>
 * 由 ApprovalFlowController 的越层数据访问逻辑下沉而来（架构修复：
 * Controller 不再直接注入 Mapper），流程组装规则与原实现保持一致。
 *
 * @author hbsz
 */
@Service
public class ApprovalFlowServiceImpl implements IApprovalFlowService {

    private final BizProjectAuthRequestMapper authRequestMapper;
    private final BizTaskProgressLogMapper progressLogMapper;
    private final BizProjectTransferLogMapper transferLogMapper;
    private final SysUserMapper userMapper;
    private final BizProjectMapper projectMapper;

    /** 日期时间格式化器 */
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ApprovalFlowServiceImpl(BizProjectAuthRequestMapper authRequestMapper,
                                   BizTaskProgressLogMapper progressLogMapper,
                                   BizProjectTransferLogMapper transferLogMapper,
                                   SysUserMapper userMapper,
                                   BizProjectMapper projectMapper) {
        this.authRequestMapper = authRequestMapper;
        this.progressLogMapper = progressLogMapper;
        this.transferLogMapper = transferLogMapper;
        this.userMapper = userMapper;
        this.projectMapper = projectMapper;
    }

    // ==================== 项目授权审批流程 ====================

    @Override
    public ApprovalFlowVO getAuthRequestFlow(Long id) {
        BizProjectAuthRequest req = authRequestMapper.selectById(id);
        if (req == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "授权申请不存在");
        }

        ApprovalFlowVO vo = new ApprovalFlowVO();
        vo.setFlowType("auth_request");
        vo.setBizId(id);
        vo.setProjectId(req.getProjectId());
        vo.setProjectName(getProjectName(req.getProjectId()));
        vo.setTitle("项目授权审批流程");

        // 查询人员名称
        String applicantName = getUserName(req.getApplicantId());
        String targetName = getUserName(req.getTargetEngineerId());
        String approverName = getUserName(req.getApproverId());

        List<ApprovalFlowVO.FlowNode> nodes = new ArrayList<>();
        List<ApprovalFlowVO.FlowLink> links = new ArrayList<>();

        // 节点1：发起申请
        ApprovalFlowVO.FlowNode applyNode = new ApprovalFlowVO.FlowNode();
        applyNode.setId("apply");
        applyNode.setName("发起申请");
        applyNode.setOperator(applicantName);
        applyNode.setRole("申请人");
        applyNode.setTime(formatTime(req.getCreatedAt()));
        applyNode.setRemark(req.getReason());
        nodes.add(applyNode);

        // 节点2：审批
        ApprovalFlowVO.FlowNode reviewNode = new ApprovalFlowVO.FlowNode();
        reviewNode.setId("review");
        reviewNode.setName("PM审核");

        // 节点3：通过
        ApprovalFlowVO.FlowNode approveNode = new ApprovalFlowVO.FlowNode();
        approveNode.setId("approve");
        approveNode.setName("审批通过");

        // 节点4：拒绝
        ApprovalFlowVO.FlowNode rejectNode = new ApprovalFlowVO.FlowNode();
        rejectNode.setId("reject");
        rejectNode.setName("审批拒绝");

        // 根据状态设置各节点状态
        String status = req.getStatus();
        if ("pending".equals(status)) {
            applyNode.setStatus("done");
            reviewNode.setStatus("current");
            approveNode.setStatus("pending");
            rejectNode.setStatus("pending");
        } else if ("approved".equals(status)) {
            applyNode.setStatus("done");
            reviewNode.setStatus("done");
            reviewNode.setOperator(approverName);
            reviewNode.setRole("项目经理");
            reviewNode.setTime(formatTime(req.getApprovedAt()));
            reviewNode.setRemark(req.getComment());
            approveNode.setStatus("done");
            approveNode.setOperator(approverName);
            approveNode.setTime(formatTime(req.getApprovedAt()));
            approveNode.setRemark("授权范围：" + req.getScope());
            rejectNode.setStatus("pending");
        } else if ("rejected".equals(status)) {
            applyNode.setStatus("done");
            reviewNode.setStatus("done");
            reviewNode.setOperator(approverName);
            reviewNode.setRole("项目经理");
            reviewNode.setTime(formatTime(req.getApprovedAt()));
            reviewNode.setRemark("审核拒绝");
            approveNode.setStatus("pending");
            rejectNode.setStatus("rejected");
            rejectNode.setOperator(approverName);
            rejectNode.setTime(formatTime(req.getApprovedAt()));
            rejectNode.setRemark(req.getComment());
        }

        nodes.add(reviewNode);
        nodes.add(approveNode);
        nodes.add(rejectNode);

        // 连线
        links.add(buildLink("apply", "review", "提交", null));
        links.add(buildLink("review", "approve", "通过", null));
        links.add(buildLink("review", "reject", "拒绝", "reject"));

        vo.setNodes(nodes);
        vo.setLinks(links);
        return vo;
    }

    // ==================== 任务进度审核流程 ====================

    @Override
    public ApprovalFlowVO getProgressReviewFlow(Long id) {
        BizTaskProgressLog log = progressLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "进度记录不存在");
        }

        ApprovalFlowVO vo = new ApprovalFlowVO();
        vo.setFlowType("progress_review");
        vo.setBizId(id);
        vo.setProjectId(log.getProjectId());
        vo.setProjectName(getProjectName(log.getProjectId()));
        vo.setTitle("任务进度审核流程");

        String reporterName = getUserName(log.getReporterId());
        String reviewerName = getUserName(log.getReviewerId());

        List<ApprovalFlowVO.FlowNode> nodes = new ArrayList<>();
        List<ApprovalFlowVO.FlowLink> links = new ArrayList<>();

        // 节点1：进度填报
        ApprovalFlowVO.FlowNode reportNode = new ApprovalFlowVO.FlowNode();
        reportNode.setId("report");
        reportNode.setName("进度填报");
        reportNode.setOperator(reporterName);
        reportNode.setRole("现场工程师");
        reportNode.setTime(formatTime(log.getReportTime()));
        reportNode.setRemark("进度 " + log.getProgress() + "%：" + (log.getRemark() != null ? log.getRemark() : ""));

        // 节点2：PM审核
        ApprovalFlowVO.FlowNode reviewNode = new ApprovalFlowVO.FlowNode();
        reviewNode.setId("review");
        reviewNode.setName("PM审核");

        // 节点3：通过
        ApprovalFlowVO.FlowNode approveNode = new ApprovalFlowVO.FlowNode();
        approveNode.setId("approve");
        approveNode.setName("审核通过");

        // 节点4：驳回
        ApprovalFlowVO.FlowNode rejectNode = new ApprovalFlowVO.FlowNode();
        rejectNode.setId("reject");
        rejectNode.setName("审核驳回");

        String reviewStatus = log.getReviewStatus();
        if ("pending".equals(reviewStatus)) {
            reportNode.setStatus("done");
            reviewNode.setStatus("current");
            approveNode.setStatus("pending");
            rejectNode.setStatus("pending");
        } else if ("approved".equals(reviewStatus)) {
            reportNode.setStatus("done");
            reviewNode.setStatus("done");
            reviewNode.setOperator(reviewerName);
            reviewNode.setRole("项目经理");
            approveNode.setStatus("done");
            approveNode.setOperator(reviewerName);
            approveNode.setTime(formatTime(log.getReportTime()));
            approveNode.setRemark("任务进度已更新为 " + log.getProgress() + "%");
            rejectNode.setStatus("pending");
        } else if ("rejected".equals(reviewStatus)) {
            reportNode.setStatus("done");
            reviewNode.setStatus("done");
            reviewNode.setOperator(reviewerName);
            reviewNode.setRole("项目经理");
            approveNode.setStatus("pending");
            rejectNode.setStatus("rejected");
            rejectNode.setOperator(reviewerName);
            rejectNode.setRemark("驳回原因请查看原始记录");
        }

        nodes.add(reportNode);
        nodes.add(reviewNode);
        nodes.add(approveNode);
        nodes.add(rejectNode);

        links.add(buildLink("report", "review", "提交", null));
        links.add(buildLink("review", "approve", "通过", null));
        links.add(buildLink("review", "reject", "驳回", "reject"));

        vo.setNodes(nodes);
        vo.setLinks(links);
        return vo;
    }

    // ==================== 项目转移审批流程 ====================

    @Override
    public ApprovalFlowVO getProjectTransferFlow(Long id) {
        BizProjectTransferLog log = transferLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "转移记录不存在");
        }

        ApprovalFlowVO vo = new ApprovalFlowVO();
        vo.setFlowType("project_transfer");
        vo.setBizId(id);
        vo.setProjectId(log.getProjectId());
        vo.setProjectName(getProjectName(log.getProjectId()));
        vo.setTitle("项目转移审批流程");

        String operatorName = getUserName(log.getOperatedBy());
        String fromName = getUserName(log.getFromEngineerId());
        String toName = getUserName(log.getToEngineerId());

        List<ApprovalFlowVO.FlowNode> nodes = new ArrayList<>();
        List<ApprovalFlowVO.FlowLink> links = new ArrayList<>();

        String actionType = log.getActionType();

        // 项目转移只有3个节点：发起 → 审批 → 完成
        ApprovalFlowVO.FlowNode initiateNode = new ApprovalFlowVO.FlowNode();
        initiateNode.setId("initiate");
        initiateNode.setName("发起" + getActionLabel(actionType));
        initiateNode.setOperator(operatorName);
        initiateNode.setTime(formatTime(log.getOperatedAt()));
        initiateNode.setRemark(log.getRemark());

        ApprovalFlowVO.FlowNode approveNode = new ApprovalFlowVO.FlowNode();
        approveNode.setId("approve");
        approveNode.setName("审批");

        ApprovalFlowVO.FlowNode transferNode = new ApprovalFlowVO.FlowNode();
        transferNode.setId("transfer");
        transferNode.setName("完成" + getActionLabel(actionType));

        // 转移日志中的操作都是已完成的（记录的是已完成的事件）
        initiateNode.setStatus("done");
        approveNode.setStatus("done");
        approveNode.setOperator(operatorName);
        approveNode.setRemark("审批通过");
        transferNode.setStatus("done");
        transferNode.setOperator("系统");
        transferNode.setRemark(
            "assign".equals(actionType) ? "已授权给 " + toName :
            "revoke".equals(actionType) ? "已撤销 " + fromName + " 的授权" :
            "已从 " + fromName + " 转移给 " + toName
        );

        nodes.add(initiateNode);
        nodes.add(approveNode);
        nodes.add(transferNode);

        links.add(buildLink("initiate", "approve", "提交", null));
        links.add(buildLink("approve", "transfer", "通过", null));

        vo.setNodes(nodes);
        vo.setLinks(links);
        return vo;
    }

    // ==================== 列表查询 ====================

    @Override
    public List<Map<String, Object>> listAuthRequests() {
        // 查询所有授权申请，按创建时间倒序
        List<BizProjectAuthRequest> requests = authRequestMapper.selectList(
            new LambdaQueryWrapper<BizProjectAuthRequest>()
                .orderByDesc(BizProjectAuthRequest::getCreatedAt)
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizProjectAuthRequest req : requests) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", req.getId());
            item.put("projectId", req.getProjectId());
            item.put("projectName", getProjectName(req.getProjectId()));
            item.put("applicantName", getUserName(req.getApplicantId()));
            item.put("targetName", getUserName(req.getTargetEngineerId()));
            item.put("scope", req.getScope());
            item.put("status", req.getStatus());
            item.put("createdAt", req.getCreatedAt() != null ? req.getCreatedAt().format(DTF) : null);
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> listProgressReviews() {
        List<BizTaskProgressLog> logs = progressLogMapper.selectList(
            new LambdaQueryWrapper<BizTaskProgressLog>()
                .orderByDesc(BizTaskProgressLog::getReportTime)
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizTaskProgressLog log : logs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", log.getId());
            item.put("taskId", log.getTaskId());
            item.put("projectId", log.getProjectId());
            item.put("projectName", getProjectName(log.getProjectId()));
            item.put("reporterName", getUserName(log.getReporterId()));
            item.put("progress", log.getProgress());
            item.put("reviewStatus", log.getReviewStatus());
            item.put("reportTime", log.getReportTime() != null ? log.getReportTime().format(DTF) : null);
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> listProjectTransfers() {
        List<BizProjectTransferLog> logs = transferLogMapper.selectList(
            new LambdaQueryWrapper<BizProjectTransferLog>()
                .orderByDesc(BizProjectTransferLog::getOperatedAt)
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizProjectTransferLog log : logs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", log.getId());
            item.put("projectId", log.getProjectId());
            item.put("projectName", getProjectName(log.getProjectId()));
            item.put("operatorName", getUserName(log.getOperatedBy()));
            item.put("actionType", log.getActionType());
            item.put("fromName", getUserName(log.getFromEngineerId()));
            item.put("toName", getUserName(log.getToEngineerId()));
            item.put("operatedAt", log.getOperatedAt() != null ? log.getOperatedAt().format(DTF) : null);
            result.add(item);
        }
        return result;
    }

    // ==================== 工具方法 ====================

    /** 获取用户名称（null 安全） */
    private String getUserName(Long userId) {
        if (userId == null) return null;
        SysUser user = userMapper.selectById(userId);
        return user != null ? user.getName() : null;
    }

    /** 获取项目名称 */
    private String getProjectName(Long projectId) {
        if (projectId == null) return null;
        BizProject project = projectMapper.selectById(projectId);
        return project != null ? project.getName() : null;
    }

    /** 格式化时间 */
    private String formatTime(TemporalAccessor time) {
        if (time == null) return null;
        return DTF.format(time);
    }

    /** 构建连线 */
    private ApprovalFlowVO.FlowLink buildLink(String source, String target, String label, String type) {
        ApprovalFlowVO.FlowLink link = new ApprovalFlowVO.FlowLink();
        link.setSource(source);
        link.setTarget(target);
        link.setLabel(label);
        link.setType(type);
        return link;
    }

    /** 操作类型中文标签 */
    private String getActionLabel(String actionType) {
        if ("assign".equals(actionType)) return "授权";
        if ("revoke".equals(actionType)) return "撤销";
        if ("transfer".equals(actionType)) return "转移";
        return actionType;
    }
}
