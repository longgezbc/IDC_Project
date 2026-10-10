package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link SysUser}，映射数据表 {@code sys_user}，负责用户账号的增删改查。
 * 除 MyBatis-Plus 通用方法外，提供按用户名查询（区分是否限制启用状态）、查询用户角色编码、
 * 更新最后登录信息等方法，服务于登录认证与权限校验。
 *
 * @author hbsz
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户名查询启用状态（status=1）的用户，用于登录认证。
     *
     * @param username 用户名
     * @return 匹配的启用用户，未命中时返回 {@code null}
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND status = 1")
    SysUser selectByUsername(@Param("username") String username);

    /**
     * 根据用户名查询用户（不限启用状态，用于管理员管理场景）。
     *
     * @param username 用户名
     * @return 匹配的用户（含禁用），未命中时返回 {@code null}
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser selectByUsernameAll(@Param("username") String username);

    /**
     * 根据用户ID查询其拥有的角色编码列表。
     * <p>
     * 通过 {@code sys_role} INNER JOIN {@code sys_user_role} 关联查询，仅返回启用角色
     * （{@code r.status = 1}）。用于登录后加载用户角色以进行鉴权。
     *
     * @param userId 用户ID
     * @return 该用户拥有的启用角色编码列表
     */
    @Select("SELECT r.role_code FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID更新最后登录时间与登录IP。
     *
     * @param userId    用户ID
     * @param loginTime 本次登录时间
     * @param ip        本次登录来源IP
     * @return 受影响行数（通常为 1）
     */
    @Update("UPDATE sys_user SET last_login_time = #{loginTime}, last_login_ip = #{ip} WHERE id = #{userId}")
    int updateLastLoginInfo(@Param("userId") Long userId, @Param("loginTime") java.time.LocalDateTime loginTime, @Param("ip") String ip);

    /**
     * 查询所有启用状态的工程师（用于项目转移/授权下拉选择）。
     * <p>
     * 通过 sys_role + sys_user_role 关联查询，限定 role_code='engineer' 且用户 status=1。
     * 仅返回 id / username / name / phone 字段，密码等敏感字段不查。
     *
     * @return 启用状态的工程师列表（精简字段）
     */
    @Select("SELECT u.id, u.username, u.name, u.phone FROM sys_user u " +
            "INNER JOIN sys_user_role ur ON u.id = ur.user_id " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE r.role_code = 'engineer' AND u.status = 1 " +
            "ORDER BY u.name")
    List<SysUser> selectActiveEngineers();

    /**
     * 查询所有启用状态的项目经理（用于项目转移时选择新负责人下拉）。
     * <p>
     * 通过 sys_role + sys_user_role 关联查询，限定 role_code='project_manager' 且用户 status=1。
     * 仅返回 id / username / name / phone 字段，密码等敏感字段不查。
     *
     * @return 启用状态的项目经理列表（精简字段）
     */
    @Select("SELECT u.id, u.username, u.name, u.phone FROM sys_user u " +
            "INNER JOIN sys_user_role ur ON u.id = ur.user_id " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE r.role_code = 'project_manager' AND u.status = 1 " +
            "ORDER BY u.name")
    List<SysUser> selectActiveManagers();

    /**
     * 查询所有启用状态的管理员（用于定时任务向 admin 推送摘要，如授权申请待审提醒）。
     * <p>
     * 通过 sys_role + sys_user_role 关联查询，限定 role_code='admin' 且用户 status=1。
     * 仅返回 id / username / name / phone 字段，密码等敏感字段不查。
     *
     * @return 启用状态的管理员列表（精简字段）
     */
    @Select("SELECT u.id, u.username, u.name, u.phone FROM sys_user u " +
            "INNER JOIN sys_user_role ur ON u.id = ur.user_id " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE r.role_code = 'admin' AND u.status = 1 " +
            "ORDER BY u.name")
    List<SysUser> selectActiveAdmins();

}
