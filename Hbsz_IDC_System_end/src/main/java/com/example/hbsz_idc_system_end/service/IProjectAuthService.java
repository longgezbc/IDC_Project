package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.AssignmentQueryDTO;
import com.example.hbsz_idc_system_end.dto.AssignmentVO;
import com.example.hbsz_idc_system_end.dto.AuthRequestApproveDTO;
import com.example.hbsz_idc_system_end.dto.AuthRequestCreateDTO;
import com.example.hbsz_idc_system_end.dto.AuthRequestQueryDTO;
import com.example.hbsz_idc_system_end.dto.AuthRequestVO;
import com.example.hbsz_idc_system_end.dto.PageResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目授权申请与授权管理服务接口
 * <p>
 * 阶段3：项目授权规范化。负责项目授权申请的创建、审批流转，
 * 以及授权关系（biz_project_assignment）的查询、撤销、延期等管理操作。
 * <p>
 * 所有写操作均需写入审计日志（biz_project_transfer_log）。
 *
 * @author hbsz
 */
public interface IProjectAuthService {

    /**
     * 创建项目授权申请（状态 pending）。
     * <p>
     * 校验项目存在、目标工程师存在且具备工程师角色、申请人是否有权申请（admin/PM 或项目本身负责人）。
     *
     * @param dto 申请参数
     * @return 创建后的申请 VO
     */
    AuthRequestVO createAuthRequest(AuthRequestCreateDTO dto);

    /**
     * 审批通过授权申请。
     * <p>
     * 创建 biz_project_assignment 记录（含 permissionScope/expireAt），
     * 更新申请状态为 approved，并写审计日志。
     *
     * @param id  申请ID
     * @param dto 审批参数（approve=true、审批意见）
     * @return 审批后的申请 VO
     */
    AuthRequestVO approveAuthRequest(Long id, AuthRequestApproveDTO dto);

    /**
     * 驳回授权申请。
     * <p>
     * 仅更新申请状态为 rejected，不创建授权记录。
     *
     * @param id  申请ID
     * @param dto 审批参数（approve=false、驳回意见）
     * @return 驳回后的申请 VO
     */
    AuthRequestVO rejectAuthRequest(Long id, AuthRequestApproveDTO dto);

    /**
     * 查询项目对目标工程师是否存在待审批的授权申请。
     * <p>
     * 授权双入口收敛：PM 直接授权入口委托申请-审批链路时，
     * 若已存在待审批申请则复用该申请直接审批，避免重复创建。
     *
     * @param projectId       项目ID
     * @param targetEngineerId 目标工程师用户ID
     * @return 待审批申请ID，不存在时返回 null
     */
    Long findPendingRequestId(Long projectId, Long targetEngineerId);

    /**
     * 分页查询项目授权申请列表。
     *
     * @param query 查询条件（status / projectId / applicantId）
     * @return 分页结果
     */
    PageResult<AuthRequestVO> listAuthRequests(AuthRequestQueryDTO query);

    /**
     * 查询当前用户申请的授权列表。
     *
     * @return 申请 VO 列表
     */
    List<AuthRequestVO> getMyAuthRequests();

    /**
     * 查询待当前用户审批的申请列表。
     * <p>
     * 当前用户为 admin 或 PM 时，返回所有 pending 状态的申请；
     * 其他角色返回空列表。
     *
     * @return 待审批申请 VO 列表
     */
    List<AuthRequestVO> getPendingAuthRequests();

    /**
     * 分页查询项目授权关系列表（biz_project_assignment）。
     *
     * @param query 查询条件（projectId / engineerId / isActive / expireSoon）
     * @return 分页结果
     */
    PageResult<AssignmentVO> listAssignments(AssignmentQueryDTO query);

    /**
     * 撤销授权。
     * <p>
     * 设置 is_active=0、revoked_at、revoke_reason，并写审计日志。
     *
     * @param id     授权记录ID
     * @param reason 撤销原因
     */
    void revokeAssignment(Long id, String reason);

    /**
     * 按项目 + 工程师撤销所有有效授权（CAS 软删单点）。
     * <p>
     * 撤销双入口收敛：PM 撤销入口（revokeEngineer）委托本方法，
     * 与 revokeAssignment（按单条记录撤销）共享 assignment 软删写路径，
     * 统一在 ProjectAuthServiceImpl 内完成 is_active 置 0 + revoke_reason/revoked_at + 审计日志。
     * <p>
     * CAS 语义：仅撤销 is_active=1 的记录，受影响行数为 0 表示无有效授权（抛 DATA_NOT_FOUND）。
     * 注意：撤销场景不校验工程师启用状态（工程师可能已被禁用后追回授权），仅校验存在。
     *
     * @param projectId  项目ID
     * @param engineerId 工程师用户ID
     * @param reason     撤销原因
     */
    void revokeByProjectAndEngineer(Long projectId, Long engineerId, String reason);

    /**
     * 直接授权（代提申请 + 自动审批）。
     * <p>
     * assignment 写路径收敛：transferProject / assignEngineer 等授权入口统一委托本方法，
     * 不再绕过申请-审批链路直写 biz_project_assignment，确保每次授权在
     * biz_project_auth_request 均有留痕（审批待办中心/流程图/审计可见）。
     * <p>
     * 流程：已存在有效授权则幂等跳过（返回 null）；否则复用已有待审批申请或代提新申请（scope 走默认值），
     * 随后自动审批通过。目标工程师完整校验（存在/启用/engineer 角色）由审批环节兜底。
     *
     * @param projectId       项目ID
     * @param targetEngineerId 目标工程师用户ID
     * @param reason          授权理由（透传至申请 reason 与审批 comment，保留操作者意图）
     * @return 审批通过的申请 VO；已存在有效授权跳过时返回 null
     */
    AuthRequestVO grantDirectAuthorization(Long projectId, Long targetEngineerId, String reason);

    /**
     * 项目组场景直写授权（无申请留痕）。
     * <p>
     * 项目组成员可为非工程师用户（前端成员选择器不限角色），无法走申请-审批链路
     * （审批环节强制 engineer 角色），因此提供 assignment 直写原语：
     * 仅负责收敛 INSERT 写路径与查重，不产生 biz_project_auth_request 记录。
     * <p>
     * 查重语义：已存在相同 (project, engineer, is_active=1, scope) 的授权时跳过，
     * 与唯一索引 uk_project_engineer_active_scope 对齐，避免 DuplicateKeyException。
     *
     * @param projectId  项目ID
     * @param userId     成员用户ID
     * @param operatorId 操作人用户ID
     * @param scope      授权范围（空则走默认值）
     * @param remark     授权备注
     */
    void createDirectAssignment(Long projectId, Long userId, Long operatorId, String scope, String remark);

    /**
     * 按项目 + 工程师容错软删有效授权（无审计日志，供调用方组合业务语义）。
     * <p>
     * 与 {@link #revokeByProjectAndEngineer} 共享同一 CAS 软删语义（WHERE is_active=1），
     * 差异在于：无有效授权时不抛错（返回 0），且不写 revoke 审计——
     * 调用方（如项目组移除成员）自带业务审计与上下文，避免审计条目重复。
     *
     * @param projectId  项目ID
     * @param engineerId 工程师/成员用户ID
     * @param reason     撤销原因（写入 revoke_reason）
     * @return 受影响行数（0 表示无有效授权，属正常场景）
     */
    int softDeleteActiveAssignments(Long projectId, Long engineerId, String reason);

    /**
     * 按项目批量软删所有有效授权（项目转移场景）。
     * <p>
     * 撤销该项目下全部 is_active=1 的授权记录，审计由调用方（转移流程）统一记录，
     * 此处不单独写审计，避免一次转移产生 N 条 revoke 日志噪音。
     *
     * @param projectId 项目ID
     * @param reason    撤销原因（写入 revoke_reason）
     * @return 受影响行数
     */
    int softDeleteAllAssignmentsByProject(Long projectId, String reason);

    /**
     * 延期授权。
     * <p>
     * 更新 expire_at 字段。
     *
     * @param id          授权记录ID
     * @param newExpireAt 新的过期时间
     */
    void extendAssignment(Long id, LocalDateTime newExpireAt);

}
