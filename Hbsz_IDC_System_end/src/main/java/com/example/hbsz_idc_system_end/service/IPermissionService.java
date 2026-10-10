package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.entity.SysPermission;
import com.example.hbsz_idc_system_end.entity.SysRole;

import java.util.List;
import java.util.Set;

/**
 * 权限服务接口
 * 负责权限校验、用户权限缓存（Redis）等核心能力
 * <p>
 * 与 MCP Redis 工具集成的关键点：
 * - 用户权限标识列表缓存到 Redis，键格式：hbsz:user:perms:{userId}
 * - 用户角色编码列表缓存到 Redis，键格式：hbsz:user:roles:{userId}
 * - 角色/权限变更时主动清除对应用户的缓存
 *
 * @author hbsz
 */
public interface IPermissionService {

    /**
     * 获取当前登录用户的角色编码集合（带 Redis 缓存）
     *
     * @return 角色编码集合
     */
    Set<String> getCurrentUserRoles();

    /**
     * 根据用户ID获取角色编码集合（带 Redis 缓存）
     *
     * @param userId 用户ID
     * @return 角色编码集合
     */
    Set<String> getRolesByUserId(Long userId);

    /**
     * 获取当前登录用户的权限标识集合（带 Redis 缓存）
     *
     * @return 权限标识集合
     */
    Set<String> getCurrentUserPermissions();

    /**
     * 根据用户ID获取权限标识集合（带 Redis 缓存）
     *
     * @param userId 用户ID
     * @return 权限标识集合
     */
    Set<String> getPermissionsByUserId(Long userId);

    /**
     * 校验当前用户是否拥有指定权限
     * <p>
     * 校验不通过时抛出无权限异常（不返回布尔值）。
     *
     * @param permissionCode 权限标识
     */
    void checkPermission(String permissionCode);

    /**
     * 校验当前用户是否拥有指定权限（多个权限 AND/OR 关系）
     *
     * @param codes      权限标识数组
     * @param logicalAnd true=AND 关系，false=OR 关系
     */
    void checkPermissions(String[] codes, boolean logicalAnd);

    /**
     * 清除指定用户的权限/角色缓存（角色或权限变更时调用）
     *
     * @param userId 用户ID
     */
    void clearUserCache(Long userId);

    /**
     * 批量清除用户缓存
     *
     * @param userIds 用户ID列表
     */
    void clearUsersCache(List<Long> userIds);

    /**
     * 清除所有用户的权限缓存（权限项变更时调用）
     */
    void clearAllUserCache();

    /**
     * 根据用户ID查询关联的角色列表
     *
     * @param userId 用户ID
     * @return 角色实体列表
     */
    List<SysRole> listRolesByUserId(Long userId);

    /**
     * 根据角色ID查询权限列表
     *
     * @param roleId 角色ID
     * @return 权限实体列表
     */
    List<SysPermission> listPermissionsByRoleId(Long roleId);

    /**
     * 根据多个角色ID批量查询权限列表（避免N+1）
     * <p>
     * 一次性按 roleIds 批量查询，替代逐个角色查询，规避 N+1 查询性能问题。
     *
     * @param roleIds 角色ID列表
     * @return 权限实体列表（多角色权限的并集，可能含重复，由调用方去重）
     */
    List<SysPermission> listPermissionsByRoleIds(List<Long> roleIds);

    /**
     * 查询所有启用的权限列表
     *
     * @return 启用状态的权限实体列表
     */
    List<SysPermission> listAllPermissions();

    /**
     * 根据用户ID查询角色ID列表
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    List<Long> listRoleIdsByUserId(Long userId);

}
