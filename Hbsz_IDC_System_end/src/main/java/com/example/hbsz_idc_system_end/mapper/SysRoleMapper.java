package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link SysRole}，映射数据表 {@code sys_role}，负责角色的增删改查。
 * 除 MyBatis-Plus 通用方法外，提供按用户查询角色列表、按角色编码查询等方法，
 * 服务于 RBAC 权限体系。
 *
 * @author hbsz
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 根据用户ID查询其拥有的角色列表。
     * <p>
     * {@code sys_role} INNER JOIN {@code sys_user_role} 关联查询，仅取启用角色
     * （{@code r.status = 1}），并按 {@code sort} 排序。
     *
     * @param userId 用户ID
     * @return 该用户的启用角色列表（按排序号排序）
     */
    @Select("SELECT r.* FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1 " +
            "ORDER BY r.sort")
    List<SysRole> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据角色编码查询启用状态的角色。
     *
     * @param roleCode 角色编码
     * @return 匹配的启用角色，未命中时返回 {@code null}
     */
    @Select("SELECT * FROM sys_role WHERE role_code = #{roleCode} AND status = 1")
    SysRole selectByCode(@Param("roleCode") String roleCode);

}
