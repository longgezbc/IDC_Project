package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.TeamMemberAddDTO;
import com.example.hbsz_idc_system_end.dto.TeamMemberQueryDTO;
import com.example.hbsz_idc_system_end.dto.TeamMemberVO;

import java.util.List;

/**
 * 项目组管理服务接口
 * <p>
 * 阶段4：项目组跨部门协作。负责项目组成员的添加、移除、查询、转让组长等管理操作。
 * <p>
 * 所有写操作均需写入审计日志（biz_project_transfer_log）。
 *
 * @author hbsz
 */
public interface IProjectTeamService {

    /**
     * 添加项目组成员。
     * <p>
     * 校验项目存在、用户存在、不重复加入；同时创建 biz_project_assignment
     * （permissionScope=read_write）；写审计日志。
     *
     * @param dto 添加参数
     * @return 添加后的成员 VO
     */
    TeamMemberVO addMember(TeamMemberAddDTO dto);

    /**
     * 移除项目组成员。
     * <p>
     * 设置 left_at、is_active=0；同时撤销 biz_project_assignment；写审计日志。
     *
     * @param id     记录ID
     * @param reason 移除原因
     */
    void removeMember(Long id, String reason);

    /**
     * 分页查询项目组成员列表。
     *
     * @param query 查询条件（projectId / userId / roleInTeam / isActive）
     * @return 分页结果
     */
    PageResult<TeamMemberVO> listMembers(TeamMemberQueryDTO query);

    /**
     * 查询当前用户参与的所有项目组。
     *
     * @return 项目组成员 VO 列表
     */
    List<TeamMemberVO> listMyTeams();

    /**
     * 转让组长。
     * <p>
     * 旧 leader 设为 member，新 leader 设为 leader；更新 biz_project.manager_id；写审计日志。
     *
     * @param projectId    项目ID
     * @param newLeaderId  新组长用户ID
     */
    void transferLeader(Long projectId, Long newLeaderId);

}
