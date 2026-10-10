package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.hbsz_idc_system_end.dto.TeamMemberQueryDTO;
import com.example.hbsz_idc_system_end.dto.TeamMemberVO;
import com.example.hbsz_idc_system_end.entity.BizProjectTeam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 项目组成员数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link BizProjectTeam}，映射数据表 {@code biz_project_team}，
 * 负责项目组成员的增删改查。继承 MyBatis-Plus 的 {@link BaseMapper} 提供通用 CRUD，
 * 额外提供分页查询（带用户姓名/电话/岗位等冗余字段，XML 实现）。
 *
 * @author hbsz
 */
@Mapper
public interface BizProjectTeamMapper extends BaseMapper<BizProjectTeam> {

    /**
     * 分页查询项目组成员列表（带用户姓名、电话、岗位等冗余字段，XML 实现）。
     *
     * @param page  分页对象
     * @param query 查询条件（projectId / userId / roleInTeam / isActive）
     * @return 分页结果
     */
    IPage<TeamMemberVO> selectTeamMemberPage(IPage<TeamMemberVO> page,
                                              @Param("query") TeamMemberQueryDTO query);

    /**
     * 根据 ID 查询项目组成员 VO（含用户姓名、电话、岗位等冗余字段，XML 实现）。
     * <p>
     * 【修复M9】替代原 pageSize=100 分页查询后内存 filter 的低效实现，直接按 id 精确查询，避免丢数据。
     *
     * @param id 成员记录ID
     * @return 成员视图对象，未命中返回 null
     */
    TeamMemberVO selectTeamMemberById(@Param("id") Long id);

}
