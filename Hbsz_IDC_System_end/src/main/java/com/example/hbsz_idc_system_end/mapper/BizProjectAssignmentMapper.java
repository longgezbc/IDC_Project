package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.hbsz_idc_system_end.dto.AssignmentQueryDTO;
import com.example.hbsz_idc_system_end.dto.AssignmentVO;
import com.example.hbsz_idc_system_end.dto.ProjectAssignmentVO;
import com.example.hbsz_idc_system_end.entity.BizProjectAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目授权关系 Mapper
 * <p>
 * 继承 MyBatis-Plus BaseMapper，提供标准 CRUD。补充自定义查询：
 * 工程师授权项目ID列表、项目当前授权工程师列表、批量撤销、过期授权查询等。
 *
 * @author hbsz
 */
@Mapper
public interface BizProjectAssignmentMapper extends BaseMapper<BizProjectAssignment> {

    /**
     * 查询工程师被显式授权的项目ID列表（仅有效且未过期的记录）
     *
     * @param engineerId 工程师用户ID
     * @return 项目ID列表
     */
    @Select("SELECT project_id FROM biz_project_assignment " +
            "WHERE engineer_id = #{engineerId} AND is_active = 1 " +
            "AND (expire_at IS NULL OR expire_at > NOW())")
    List<Long> selectAuthorizedProjectIds(@Param("engineerId") Long engineerId);

    /**
     * 批量撤销项目的所有有效授权（用于 transfer 时清空原授权）。
     * 同时写入 revoke_reason 与 revoked_at 便于审计追溯。
     *
     * @param projectId 项目ID
     * @param reason    撤销原因
     * @return 受影响行数
     */
    @Update("UPDATE biz_project_assignment SET is_active = 0, revoke_reason = #{reason}, revoked_at = NOW() " +
            "WHERE project_id = #{projectId} AND is_active = 1")
    int deactivateAllByProject(@Param("projectId") Long projectId, @Param("reason") String reason);

    /**
     * 检查工程师是否被授权访问指定项目（用于详情接口的权限校验）。
     * 包含过期校验：is_active=1 AND (expire_at IS NULL OR expire_at > NOW())
     *
     * @param projectId   项目ID
     * @param engineerId  工程师ID
     * @return 存在数量（>0 即有权限）
     */
    @Select("SELECT COUNT(1) FROM biz_project_assignment " +
            "WHERE project_id = #{projectId} AND engineer_id = #{engineerId} AND is_active = 1 " +
            "AND (expire_at IS NULL OR expire_at > NOW())")
    int countActiveAssignment(@Param("projectId") Long projectId, @Param("engineerId") Long engineerId);

    /**
     * 查询项目下有效授权工程师列表（带工程师姓名等冗余字段，XML 实现）
     *
     * @param projectId 项目ID
     * @return 授权工程师 VO 列表
     */
    List<ProjectAssignmentVO> selectAssignmentsByProject(@Param("projectId") Long projectId);

    /**
     * 分页查询项目授权关系列表（带工程师姓名、项目名、授权人姓名等冗余字段，XML 实现）。
     *
     * @param page  分页对象
     * @param query 查询条件（projectId / engineerId / isActive / expireSoon）
     * @return 分页结果
     */
    IPage<AssignmentVO> selectAssignmentPage(IPage<AssignmentVO> page,
                                              @Param("query") AssignmentQueryDTO query);

    /**
     * 查询已过期但未回收的有效授权记录（expire_at < now() AND is_active = 1）。
     * <p>
     * 供定时任务逐条回收使用。
     *
     * @param now 当前时间
     * @return 待回收的授权记录列表
     */
    @Select("SELECT * FROM biz_project_assignment " +
            "WHERE is_active = 1 AND expire_at IS NOT NULL AND expire_at < #{now}")
    List<BizProjectAssignment> selectExpiredActiveAssignments(@Param("now") LocalDateTime now);

}
