package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.entity.SysPermission;
import com.example.hbsz_idc_system_end.entity.SysRole;
import com.example.hbsz_idc_system_end.mapper.SysPermissionMapper;
import com.example.hbsz_idc_system_end.mapper.SysRoleMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.utils.RedisUtils;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限服务实现
 * 负责权限校验、用户权限缓存（Redis）等核心能力
 * <p>
 * 与 MCP Redis 工具集成的关键点：
 * - 用户权限标识列表缓存到 Redis，键格式：hbsz:user:perms:{userId}
 * - 用户角色编码列表缓存到 Redis，键格式：hbsz:user:roles:{userId}
 * - 角色/权限变更时主动清除对应用户的缓存
 *
 * @author hbsz
 */
@Slf4j
@Service
public class PermissionServiceImpl implements IPermissionService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final RedisUtils redisUtils;

    public PermissionServiceImpl(SysUserMapper sysUserMapper, SysRoleMapper sysRoleMapper,
                                 SysPermissionMapper sysPermissionMapper, RedisUtils redisUtils) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.redisUtils = redisUtils;
    }

    /**
     * 获取当前登录用户的角色编码集合（带 Redis 缓存）
     */
    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getCurrentUserRoles() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            log.warn("[权限服务] 获取用户角色失败: UserContext 中 userId 为空");
            return Collections.emptySet();
        }
        return getRolesByUserId(userId);
    }

    /**
     * 根据用户ID获取角色编码集合（带 Redis 缓存）
     * <p>
     * 抽取此重载方法便于在非请求上下文（如登录流程、定时任务）中复用，
     * 避免临时设置 ThreadLocal。
     *
     * @param userId 用户ID
     * @return 角色编码集合
     */
    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getRolesByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        String cacheKey = CommonConstant.REDIS_USER_ROLES_PREFIX + userId;
        Object cached = redisUtils.get(cacheKey);
        if (cached instanceof Set) {
            log.debug("[权限服务] 角色缓存命中: userId={}, key={}, roles={}", userId, cacheKey, cached);
            return (Set<String>) cached;
        }
        log.info("[权限服务] 角色缓存未命中，从DB加载: userId={}, key={}", userId, cacheKey);
        List<String> roleCodes = sysUserMapper.selectRoleCodesByUserId(userId);
        Set<String> roles = new HashSet<>(roleCodes != null ? roleCodes : Collections.emptyList());
        redisUtils.set(cacheKey, roles, CommonConstant.REDIS_USER_PERMS_EXPIRE);
        // 优化：roles 集合可能含敏感信息，降为 DEBUG 避免生产环境泄露
        log.debug("[权限服务] 角色加载完成并写入缓存: userId={}, roles={}, expire={}s",
                userId, roles, CommonConstant.REDIS_USER_PERMS_EXPIRE);
        return roles;
    }

    /**
     * 获取当前登录用户的权限标识集合（带 Redis 缓存）
     */
    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getCurrentUserPermissions() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            log.warn("[权限服务] 获取用户权限失败: UserContext 中 userId 为空");
            return Collections.emptySet();
        }
        return getPermissionsByUserId(userId);
    }

    /**
     * 根据用户ID获取权限标识集合（带 Redis 缓存）
     * <p>
     * 优化说明：抽取此重载方法后，AuthService.login / getCurrentUserInfo 等场景
     * 不必再通过临时设置 ThreadLocal 来复用 getCurrentUserPermissions，
     * 避免 ThreadLocal 在登录流程中被污染（潜在内存泄漏风险）。
     *
     * @param userId 用户ID
     * @return 权限标识集合
     */
    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getPermissionsByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        // 超级管理员直接返回全部权限标识
        Set<String> roles = getRolesByUserId(userId);
        if (roles.contains(CommonConstant.ROLE_CODE_ADMIN)) {
            // 优化：超级管理员放行路径频繁，降为 DEBUG
            log.debug("[权限服务] 超级管理员直接放行: userId={}, roles={}", userId, roles);
            return new HashSet<>(Collections.singletonList(CommonConstant.ALL_PERMISSION));
        }
        String cacheKey = CommonConstant.REDIS_USER_PERMS_PREFIX + userId;
        Object cached = redisUtils.get(cacheKey);
        if (cached instanceof Set) {
            log.debug("[权限服务] 权限缓存命中: userId={}, key={}, permsCount={}", userId, cacheKey, ((Set<?>) cached).size());
            return (Set<String>) cached;
        }
        // 缓存未命中是较罕见事件，保留 INFO 便于监控缓存命中率
        log.info("[权限服务] 权限缓存未命中，从DB加载: userId={}, key={}", userId, cacheKey);
        List<String> codes = sysPermissionMapper.selectPermissionCodesByUserId(userId);
        Set<String> perms = new HashSet<>(codes != null ? codes : Collections.emptyList());
        redisUtils.set(cacheKey, perms, CommonConstant.REDIS_USER_PERMS_EXPIRE);
        // 优化：完整 perms 集合含敏感信息，降为 DEBUG；INFO 仅记录数量
        log.info("[权限服务] 权限加载完成并写入缓存: userId={}, permsCount={}, expire={}s",
                userId, perms.size(), CommonConstant.REDIS_USER_PERMS_EXPIRE);
        log.debug("[权限服务] 权限详情: userId={}, perms={}", userId, perms);
        return perms;
    }

    /**
     * 校验当前用户是否拥有指定权限
     *
     * @param permissionCode 权限标识
     * @throws BusinessException 无权限时抛出
     */
    @Override
    public void checkPermission(String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            log.debug("[权限服务] 权限标识为空，跳过校验");
            return;
        }
        Long userId = UserContext.getUserId();
        Set<String> perms = getCurrentUserPermissions();
        if (perms.contains(CommonConstant.ALL_PERMISSION)) {
            // 优化：超级管理员校验通过频繁，降为 DEBUG
            log.debug("[权限服务] 权限校验通过(超级管理员): userId={}, required={}", userId, permissionCode);
            return;
        }
        if (perms.contains(permissionCode)) {
            // 优化：常规校验通过频繁，降为 DEBUG
            log.debug("[权限服务] 权限校验通过: userId={}, required={}", userId, permissionCode);
            return;
        }
        // 拒绝事件保留 WARN，含 userPerms 便于排查
        log.warn("[权限服务] 权限校验拒绝: userId={}, required={}, userPerms={}", userId, permissionCode, perms);
        throw new BusinessException(ResultCode.NO_PERMISSION);
    }

    /**
     * 校验当前用户是否拥有指定权限（多个权限 AND/OR 关系）
     * <p>
     * 超级管理员（持有 {@link CommonConstant#ALL_PERMISSION}）直接放行；
     * logicalAnd=true 时要求同时具备全部权限，false 时具备任意其一即可，校验不通过抛出无权限异常。
     */
    @Override
    public void checkPermissions(String[] codes, boolean logicalAnd) {
        if (codes == null || codes.length == 0) {
            log.debug("[权限服务] 权限标识数组为空，跳过校验");
            return;
        }
        Long userId = UserContext.getUserId();
        Set<String> perms = getCurrentUserPermissions();
        if (perms.contains(CommonConstant.ALL_PERMISSION)) {
            // 优化：超级管理员校验通过频繁，降为 DEBUG
            log.debug("[权限服务] 权限校验通过(超级管理员): userId={}, required={}, logicalAnd={}",
                    userId, Arrays.toString(codes), logicalAnd);
            return;
        }
        log.debug("[权限服务] 开始权限校验: userId={}, required={}, logicalAnd={}, userPerms={}",
                userId, Arrays.toString(codes), logicalAnd, perms);
        if (logicalAnd) {
            // AND 关系：必须拥有全部权限
            for (String code : codes) {
                if (!perms.contains(code)) {
                    log.warn("[权限服务] 权限校验拒绝(AND): userId={}, missing={}, required={}, userPerms={}",
                            userId, code, Arrays.toString(codes), perms);
                    throw new BusinessException(ResultCode.NO_PERMISSION);
                }
            }
            // 优化：常规校验通过频繁，降为 DEBUG
            log.debug("[权限服务] 权限校验通过(AND): userId={}, required={}", userId, Arrays.toString(codes));
        } else {
            // OR 关系：拥有任意一个即可
            boolean hasAny = false;
            String matchedCode = null;
            for (String code : codes) {
                if (perms.contains(code)) {
                    hasAny = true;
                    matchedCode = code;
                    break;
                }
            }
            if (!hasAny) {
                log.warn("[权限服务] 权限校验拒绝(OR): userId={}, required any of={}, userPerms={}",
                        userId, Arrays.toString(codes), perms);
                throw new BusinessException(ResultCode.NO_PERMISSION);
            }
            // 优化：常规校验通过频繁，降为 DEBUG
            log.debug("[权限服务] 权限校验通过(OR): userId={}, matched={}, required={}",
                    userId, matchedCode, Arrays.toString(codes));
        }
    }

    /**
     * 清除指定用户的权限/角色缓存（角色或权限变更时调用）
     */
    @Override
    public void clearUserCache(Long userId) {
        if (userId == null) {
            log.warn("[权限服务] clearUserCache 入参 userId 为空，跳过");
            return;
        }
        String permsKey = CommonConstant.REDIS_USER_PERMS_PREFIX + userId;
        String rolesKey = CommonConstant.REDIS_USER_ROLES_PREFIX + userId;
        redisUtils.delete(permsKey);
        redisUtils.delete(rolesKey);
        log.info("[权限服务] 清除用户权限/角色缓存: userId={}, permsKey={}, rolesKey={}", userId, permsKey, rolesKey);
    }

    /**
     * 批量清除用户缓存
     */
    @Override
    public void clearUsersCache(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            log.debug("[权限服务] clearUsersCache 入参为空，跳过");
            return;
        }
        log.info("[权限服务] 批量清除用户缓存: userIds={}", userIds);
        userIds.forEach(this::clearUserCache);
    }

    /**
     * 清除所有用户的权限缓存（权限项变更时调用）
     * <p>
     * 优化说明：原实现使用 KEYS * 扫描所有匹配 key，
     * Redis 单线程模型下 KEYS 会阻塞主线程，key 数量较大时（如数万）会导致请求超时。
     * 改为 SCAN 迭代后，分批读取 key 不阻塞主线程，生产环境更安全。
     */
    @Override
    public void clearAllUserCache() {
        Set<String> permsKeys = redisUtils.scan(CommonConstant.REDIS_USER_PERMS_PREFIX + "*");
        Set<String> rolesKeys = redisUtils.scan(CommonConstant.REDIS_USER_ROLES_PREFIX + "*");
        int permsCount = permsKeys != null ? permsKeys.size() : 0;
        int rolesCount = rolesKeys != null ? rolesKeys.size() : 0;
        if (permsKeys != null && !permsKeys.isEmpty()) {
            redisUtils.delete(permsKeys);
        }
        if (rolesKeys != null && !rolesKeys.isEmpty()) {
            redisUtils.delete(rolesKeys);
        }
        log.info("[权限服务] 清除全部用户权限缓存: permsKeys={}, rolesKeys={}", permsCount, rolesCount);
    }

    /**
     * 根据用户ID查询关联的角色列表
     */
    @Override
    public List<SysRole> listRolesByUserId(Long userId) {
        return sysRoleMapper.selectByUserId(userId);
    }

    /**
     * 根据角色ID查询权限列表
     */
    @Override
    public List<SysPermission> listPermissionsByRoleId(Long roleId) {
        return sysPermissionMapper.selectByRoleId(roleId);
    }

    /**
     * 根据多个角色ID批量查询权限列表（避免N+1）
     */
    @Override
    public List<SysPermission> listPermissionsByRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysPermissionMapper.selectByRoleIds(roleIds);
    }

    /**
     * 查询所有启用的权限列表
     */
    @Override
    public List<SysPermission> listAllPermissions() {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermission::getStatus, CommonConstant.STATUS_ENABLED)
               .orderByAsc(SysPermission::getParentId)
               .orderByAsc(SysPermission::getSort);
        return sysPermissionMapper.selectList(wrapper);
    }

    /**
     * 根据用户ID查询角色ID列表
     */
    @Override
    public List<Long> listRoleIdsByUserId(Long userId) {
        List<SysRole> roles = sysRoleMapper.selectByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream().map(SysRole::getId).collect(Collectors.toList());
    }

}
