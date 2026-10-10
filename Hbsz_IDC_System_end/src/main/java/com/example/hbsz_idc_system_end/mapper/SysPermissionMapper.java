package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link SysPermission}，映射数据表 {@code sys_permission}，负责权限/菜单资源的
 * 增删改查。通过关联 {@code sys_role_permission}、{@code sys_user_role} 提供按角色、按用户
 * 维度的权限查询，服务于 RBAC 鉴权与前端菜单树构建。
 *
 * @author hbsz
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 根据角色ID查询其拥有的权限列表。
     * <p>
     * {@code sys_permission} INNER JOIN {@code sys_role_permission} 关联查询，仅取启用权限
     * （{@code p.status = 1}），并按 {@code parent_id, sort} 排序以便直接组装菜单树。
     *
     * @param roleId 角色ID
     * @return 该角色的启用权限列表（按父节点、排序号排序）
     */
    @Select("SELECT p.* FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "WHERE rp.role_id = #{roleId} AND p.status = 1 " +
            "ORDER BY p.parent_id, p.sort")
    List<SysPermission> selectByRoleId(@Param("roleId") Long roleId);

    /**
     * 根据多个角色ID批量查询权限列表（避免 N+1 查询）。
     * <p>
     * 使用 {@code role_id IN (...)} 配合 {@code <foreach>} 一次性取回多个角色的权限，
     * 并以 {@code DISTINCT} 去除多角色间的重复权限，避免逐角色查询产生 N 次数据库往返。
     * 建议 {@code sys_role_permission.role_id} 建立索引以命中范围查找。
     *
     * @param roleIds 角色ID列表，不应为空
     * @return 去重后的启用权限列表（按父节点、排序号排序）
     */
    @Select("<script>" +
            "SELECT DISTINCT p.* FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "WHERE p.status = 1 AND rp.role_id IN " +
            "<foreach item='id' collection='roleIds' open='(' separator=',' close=')'>#{id}</foreach> " +
            "ORDER BY p.parent_id, p.sort" +
            "</script>")
    List<SysPermission> selectByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 根据用户ID查询其拥有的权限标识（permission_code）列表。
     * <p>
     * 经 {@code sys_permission -> sys_role_permission -> sys_user_role} 三表 INNER JOIN
     * 由用户回溯到权限，{@code DISTINCT} 去重后返回权限编码，用于接口级鉴权判定。
     *
     * @param userId 用户ID
     * @return 去重后的启用权限标识列表
     */
    @Select("SELECT DISTINCT p.permission_code FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND p.status = 1")
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询其拥有的权限列表（含菜单信息）。
     * <p>
     * 经 {@code sys_permission -> sys_role_permission -> sys_user_role} 三表 INNER JOIN
     * 由用户回溯到权限，{@code DISTINCT} 去重并按 {@code parent_id, sort} 排序，
     * 便于前端直接构建用户菜单树。
     *
     * @param userId 用户ID
     * @return 去重后的启用权限列表（按父节点、排序号排序）
     */
    @Select("SELECT DISTINCT p.* FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND p.status = 1 " +
            "ORDER BY p.parent_id, p.sort")
    List<SysPermission> selectByUserId(@Param("userId") Long userId);

}
