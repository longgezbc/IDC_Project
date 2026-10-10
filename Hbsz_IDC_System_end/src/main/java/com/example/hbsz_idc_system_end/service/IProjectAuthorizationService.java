package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.ProjectAssignmentVO;
import com.example.hbsz_idc_system_end.dto.ProjectTransferDTO;
import com.example.hbsz_idc_system_end.dto.TransferLogQueryDTO;
import com.example.hbsz_idc_system_end.dto.TransferLogVO;
import com.example.hbsz_idc_system_end.entity.SysUser;

import java.util.List;

/**
 * 项目授权与转移服务接口
 * <p>
 * 负责项目-工程师授权关系的维护与审计：
 * <ul>
 *   <li>转移（transfer）：PM 将项目授权给指定工程师，原授权失效，写入审计日志；</li>
 *   <li>授权（assign）/ 撤销（revoke）：维护项目多工程师授权列表；</li>
 *   <li>查询授权工程师列表、活跃工程师下拉；</li>
 *   <li>审计日志分页查询。</li>
 * </ul>
 * 配合 {@link IProjectService} 实现工程师对项目的数据隔离访问。
 *
 * @author hbsz
 */
public interface IProjectAuthorizationService {

    /**
     * 项目转移：撤销项目当前所有有效授权，授权给目标工程师，并写入审计日志。
     * <p>
     * 操作前校验目标用户存在、启用且具备工程师角色。
     *
     * @param projectId 项目ID
     * @param dto       转移参数（目标工程师ID + 备注）
     * @return 转移后的授权记录 VO
     */
    ProjectAssignmentVO transferProject(Long projectId, ProjectTransferDTO dto);

    /**
     * 项目授权：为项目新增一名工程师授权（不影响其他已授权工程师）。
     *
     * @param projectId   项目ID
     * @param engineerId  工程师ID
     * @param remark      备注
     * @return 新建的授权记录 VO
     */
    ProjectAssignmentVO assignEngineer(Long projectId, Long engineerId, String remark);

    /**
     * 撤销授权：移除项目对指定工程师的授权。
     *
     * @param projectId   项目ID
     * @param engineerId  工程师ID
     * @param remark      备注
     */
    void revokeEngineer(Long projectId, Long engineerId, String remark);

    /**
     * 查询项目下所有有效授权工程师列表。
     *
     * @param projectId 项目ID
     * @return 授权工程师 VO 列表
     */
    List<ProjectAssignmentVO> listProjectEngineers(Long projectId);

    /**
     * 查询所有启用状态的工程师（用于转移/授权下拉选择）。
     *
     * @return 启用工程师列表（精简字段）
     */
    List<SysUser> listActiveEngineers();

    /**
     * 查询所有启用状态的项目经理（用于项目转移时选择新负责人下拉）。
     *
     * @return 启用项目经理列表（精简字段）
     */
    List<SysUser> listActiveManagers();

    /**
     * 分页查询项目转移审计日志。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<TransferLogVO> queryTransferLogs(TransferLogQueryDTO query);

    /**
     * 获取当前工程师可见的项目ID列表（用于项目列表数据隔离）。
     * <p>
     * 若当前用户为管理员或项目经理，返回 null（表示不过滤，可见全部）；
     * 若为工程师或普通用户，返回其被显式授权的项目ID列表。
     *
     * @return 可见项目ID列表，或 null 表示不过滤
     */
    List<Long> getCurrentUserVisibleProjectIds();

    /**
     * 校验当前用户是否有权访问指定项目（用于项目详情接口的权限校验）。
     * <p>
     * 管理员与项目经理直接放行；工程师/普通用户需有显式授权。
     *
     * @param projectId 项目ID
     * @return true 有访问权限
     */
    boolean canAccessProject(Long projectId);

    /**
     * 获取当前用户在指定项目下可见的任务ID列表（用于任务级数据隔离）。
     * <p>
     * 阶段2：任务级数据隔离升级，按角色差异化返回：
     * <ul>
     *   <li>admin / PM：返回 null（不限制，可查看项目下所有任务）；</li>
     *   <li>高级工程师：返回 null（不限制，可审核项目下所有任务）；</li>
     *   <li>工程师：返回自己负责的任务ID列表（assignee_id = userId）；</li>
     *   <li>外包 / 只读：返回 null（可见所有任务，由权限点控制只读）。</li>
     * </ul>
     * 若用户未获得该项目授权，返回空列表。
     *
     * @param projectId 项目ID
     * @return 可见任务ID列表，null 表示不限制，空列表表示无权访问该项目
     */
    List<Long> getCurrentUserVisibleTaskIds(Long projectId);

}
