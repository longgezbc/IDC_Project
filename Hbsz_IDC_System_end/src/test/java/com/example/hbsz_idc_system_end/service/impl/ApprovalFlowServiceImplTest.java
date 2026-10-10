package com.example.hbsz_idc_system_end.service.impl;

import com.example.hbsz_idc_system_end.common.exception.BusinessException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalFlowServiceImpl} 特征测试（Characterization Tests）。
 * <p>
 * 该逻辑由 ApprovalFlowController 越层代码下沉而来，测试目的：
 * 锁定流程图组装规则在迁移前后保持一致，防止后续重构改变以下契约：
 * <ul>
 *   <li>记录不存在必须抛 DATA_NOT_FOUND 业务异常（由全局处理器统一转 Result）；</li>
 *   <li>三种审核状态（pending/approved/rejected）对应节点状态机正确；</li>
 *   <li>转移日志三种动作（assign/revoke/transfer）的完成节点 remark 文案正确；</li>
 *   <li>列表查询字段映射与时间格式化（yyyy-MM-dd HH:mm）正确。</li>
 * </ul>
 * 全部使用 Mockito 纯单元测试，不依赖数据库/Redis，可独立运行。
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("审批流程图服务特征测试")
class ApprovalFlowServiceImplTest {

    private static final Long REQ_ID = 10L;
    private static final Long LOG_ID = 20L;
    private static final Long TRANSFER_ID = 30L;
    private static final Long PROJECT_ID = 100L;
    private static final Long APPLICANT_ID = 200L;
    private static final Long TARGET_ID = 300L;
    private static final Long APPROVER_ID = 400L;

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 19, 10, 30);

    @Mock
    private BizProjectAuthRequestMapper authRequestMapper;
    @Mock
    private BizTaskProgressLogMapper progressLogMapper;
    @Mock
    private BizProjectTransferLogMapper transferLogMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private BizProjectMapper projectMapper;

    private ApprovalFlowServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ApprovalFlowServiceImpl(
            authRequestMapper, progressLogMapper, transferLogMapper, userMapper, projectMapper);

        // 公共 stub：项目名 / 用户名（LENIENT：各用例按需覆盖）
        BizProject project = new BizProject();
        project.setId(PROJECT_ID);
        project.setName("测试项目");
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        SysUser applicant = new SysUser();
        applicant.setId(APPLICANT_ID);
        applicant.setName("申请人");
        SysUser target = new SysUser();
        target.setId(TARGET_ID);
        target.setName("目标工程师");
        SysUser approver = new SysUser();
        approver.setId(APPROVER_ID);
        approver.setName("审批人");
        when(userMapper.selectById(APPLICANT_ID)).thenReturn(applicant);
        when(userMapper.selectById(TARGET_ID)).thenReturn(target);
        when(userMapper.selectById(APPROVER_ID)).thenReturn(approver);
    }

    private BizProjectAuthRequest buildAuthRequest(String status) {
        BizProjectAuthRequest req = new BizProjectAuthRequest();
        req.setId(REQ_ID);
        req.setProjectId(PROJECT_ID);
        req.setApplicantId(APPLICANT_ID);
        req.setTargetEngineerId(TARGET_ID);
        req.setApproverId(APPROVER_ID);
        req.setStatus(status);
        req.setScope("read_write");
        req.setReason("施工需要");
        req.setComment("同意");
        req.setCreatedAt(CREATED_AT);
        return req;
    }

    // ==================== 记录不存在：统一抛业务异常 ====================

    @Test
    @DisplayName("授权申请不存在：抛 DATA_NOT_FOUND 业务异常")
    void getAuthRequestFlow_notFound_throwsBusinessException() {
        when(authRequestMapper.selectById(REQ_ID)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getAuthRequestFlow(REQ_ID));
        assertEquals("授权申请不存在", ex.getMessage());
    }

    @Test
    @DisplayName("进度记录不存在：抛 DATA_NOT_FOUND 业务异常")
    void getProgressReviewFlow_notFound_throwsBusinessException() {
        when(progressLogMapper.selectById(LOG_ID)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getProgressReviewFlow(LOG_ID));
        assertEquals("进度记录不存在", ex.getMessage());
    }

    @Test
    @DisplayName("转移记录不存在：抛 DATA_NOT_FOUND 业务异常")
    void getProjectTransferFlow_notFound_throwsBusinessException() {
        when(transferLogMapper.selectById(TRANSFER_ID)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getProjectTransferFlow(TRANSFER_ID));
        assertEquals("转移记录不存在", ex.getMessage());
    }

    // ==================== 授权审批流程：三态节点状态机 ====================

    @Test
    @DisplayName("授权申请 pending：申请节点 done、审核节点 current、通过/拒绝节点 pending")
    void getAuthRequestFlow_pending_nodeStatusMachine() {
        when(authRequestMapper.selectById(REQ_ID)).thenReturn(buildAuthRequest("pending"));

        ApprovalFlowVO vo = service.getAuthRequestFlow(REQ_ID);

        assertEquals("auth_request", vo.getFlowType());
        assertEquals("测试项目", vo.getProjectName());
        Map<String, ApprovalFlowVO.FlowNode> nodes = nodesById(vo);
        assertEquals("done", nodes.get("apply").getStatus());
        assertEquals("current", nodes.get("review").getStatus());
        assertEquals("pending", nodes.get("approve").getStatus());
        assertEquals("pending", nodes.get("reject").getStatus());
        // 申请节点填充申请人信息与格式化时间
        assertEquals("申请人", nodes.get("apply").getOperator());
        assertEquals("2026-08-19 10:30", nodes.get("apply").getTime());
        // 连线：apply→review→(approve|reject)
        assertEquals(3, vo.getLinks().size());
    }

    @Test
    @DisplayName("授权申请 approved：审核/通过节点 done 且带审批人与授权范围 remark")
    void getAuthRequestFlow_approved_fillsReviewerInfo() {
        when(authRequestMapper.selectById(REQ_ID)).thenReturn(buildAuthRequest("approved"));

        ApprovalFlowVO vo = service.getAuthRequestFlow(REQ_ID);

        Map<String, ApprovalFlowVO.FlowNode> nodes = nodesById(vo);
        assertEquals("done", nodes.get("review").getStatus());
        assertEquals("审批人", nodes.get("review").getOperator());
        assertEquals("项目经理", nodes.get("review").getRole());
        assertEquals("done", nodes.get("approve").getStatus());
        assertEquals("授权范围：read_write", nodes.get("approve").getRemark());
        assertEquals("pending", nodes.get("reject").getStatus());
    }

    @Test
    @DisplayName("授权申请 rejected：拒绝节点 rejected 且带审批意见，通过节点保持 pending")
    void getAuthRequestFlow_rejected_rejectNodeCarriesComment() {
        when(authRequestMapper.selectById(REQ_ID)).thenReturn(buildAuthRequest("rejected"));

        ApprovalFlowVO vo = service.getAuthRequestFlow(REQ_ID);

        Map<String, ApprovalFlowVO.FlowNode> nodes = nodesById(vo);
        assertEquals("rejected", nodes.get("reject").getStatus());
        assertEquals("同意", nodes.get("reject").getRemark());
        assertEquals("审批人", nodes.get("reject").getOperator());
        assertEquals("pending", nodes.get("approve").getStatus());
    }

    // ==================== 进度审核流程：三态节点状态机 ====================

    @Test
    @DisplayName("进度审核 rejected：驳回节点 rejected 带审核人，任务进度不影响状态机")
    void getProgressReviewFlow_rejected_nodeStatusMachine() {
        BizTaskProgressLog log = new BizTaskProgressLog();
        log.setId(LOG_ID);
        log.setProjectId(PROJECT_ID);
        log.setTaskId(50L);
        log.setReporterId(APPLICANT_ID);
        log.setReviewerId(APPROVER_ID);
        log.setProgress(80);
        log.setReviewStatus("rejected");
        log.setReportTime(CREATED_AT);
        log.setRemark("已完工八成");
        when(progressLogMapper.selectById(LOG_ID)).thenReturn(log);

        ApprovalFlowVO vo = service.getProgressReviewFlow(LOG_ID);

        Map<String, ApprovalFlowVO.FlowNode> nodes = nodesById(vo);
        assertEquals("done", nodes.get("report").getStatus());
        assertEquals("done", nodes.get("review").getStatus());
        assertEquals("审批人", nodes.get("review").getOperator());
        assertEquals("rejected", nodes.get("reject").getStatus());
        assertEquals("驳回原因请查看原始记录", nodes.get("reject").getRemark());
        // 填报节点 remark 含进度百分比
        assertTrue(nodes.get("report").getRemark().contains("80%"));
    }

    // ==================== 项目转移流程：三种动作文案 ====================

    @Test
    @DisplayName("转移日志 assign：完成节点 remark 为「已授权给 目标工程师」")
    void getProjectTransferFlow_assign_remarkText() {
        assertTransferRemark("assign", "已授权给 目标工程师");
    }

    @Test
    @DisplayName("转移日志 revoke：完成节点 remark 为「已撤销 申请人 的授权」")
    void getProjectTransferFlow_revoke_remarkText() {
        assertTransferRemark("revoke", "已撤销 申请人 的授权");
    }

    @Test
    @DisplayName("转移日志 transfer：完成节点 remark 为「已从 申请人 转移给 目标工程师」")
    void getProjectTransferFlow_transfer_remarkText() {
        assertTransferRemark("transfer", "已从 申请人 转移给 目标工程师");
    }

    private void assertTransferRemark(String actionType, String expectedRemark) {
        BizProjectTransferLog log = new BizProjectTransferLog();
        log.setId(TRANSFER_ID);
        log.setProjectId(PROJECT_ID);
        log.setOperatedBy(APPROVER_ID);
        log.setFromEngineerId(APPLICANT_ID);
        log.setToEngineerId(TARGET_ID);
        log.setActionType(actionType);
        log.setOperatedAt(CREATED_AT);
        log.setRemark("备注");
        when(transferLogMapper.selectById(TRANSFER_ID)).thenReturn(log);

        ApprovalFlowVO vo = service.getProjectTransferFlow(TRANSFER_ID);

        Map<String, ApprovalFlowVO.FlowNode> nodes = nodesById(vo);
        // 转移日志记录的都是已完成事件：三节点全部 done
        assertEquals("done", nodes.get("initiate").getStatus());
        assertEquals("done", nodes.get("approve").getStatus());
        assertEquals("done", nodes.get("transfer").getStatus());
        assertEquals(expectedRemark, nodes.get("transfer").getRemark());
        assertEquals(2, vo.getLinks().size());
    }

    // ==================== 列表查询：字段映射与时间格式化 ====================

    @Test
    @DisplayName("授权申请列表：字段映射齐全，createdAt 格式化为 yyyy-MM-dd HH:mm")
    void listAuthRequests_mapsFieldsAndFormatsTime() {
        when(authRequestMapper.selectList(any())).thenReturn(List.of(buildAuthRequest("pending")));

        List<Map<String, Object>> result = service.listAuthRequests();

        assertEquals(1, result.size());
        Map<String, Object> item = result.get(0);
        assertEquals(REQ_ID, item.get("id"));
        assertEquals("测试项目", item.get("projectName"));
        assertEquals("申请人", item.get("applicantName"));
        assertEquals("目标工程师", item.get("targetName"));
        assertEquals("read_write", item.get("scope"));
        assertEquals("pending", item.get("status"));
        assertEquals("2026-08-19 10:30", item.get("createdAt"));
    }

    // ==================== null 安全 ====================

    @Test
    @DisplayName("projectId 为 null：项目名返回 null 不抛 NPE")
    void getAuthRequestFlow_nullProjectId_projectNameNull() {
        BizProjectAuthRequest req = buildAuthRequest("pending");
        req.setProjectId(null);
        when(authRequestMapper.selectById(REQ_ID)).thenReturn(req);

        ApprovalFlowVO vo = service.getAuthRequestFlow(REQ_ID);

        assertNull(vo.getProjectName());
    }

    /** 将节点列表按 id 索引，便于断言 */
    private Map<String, ApprovalFlowVO.FlowNode> nodesById(ApprovalFlowVO vo) {
        Map<String, ApprovalFlowVO.FlowNode> map = new java.util.HashMap<>();
        for (ApprovalFlowVO.FlowNode node : vo.getNodes()) {
            map.put(node.getId(), node);
        }
        return map;
    }
}
