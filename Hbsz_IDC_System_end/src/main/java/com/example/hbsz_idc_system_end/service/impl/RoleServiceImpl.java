package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.AssignPermissionDTO;
import com.example.hbsz_idc_system_end.dto.PageQuery;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.RoleSaveDTO;
import com.example.hbsz_idc_system_end.dto.RoleVO;
import com.example.hbsz_idc_system_end.entity.SysRole;
import com.example.hbsz_idc_system_end.entity.SysRolePermission;
import com.example.hbsz_idc_system_end.entity.SysUserRole;
import com.example.hbsz_idc_system_end.mapper.SysRoleMapper;
import com.example.hbsz_idc_system_end.mapper.SysRolePermissionMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserRoleMapper;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.service.IRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色管理服务实现
 * <p>
 * 提供角色定义、权限配置与用户角色关联管理。关键约束：
 * <ul>
 *   <li>事务：create/update/delete/assignPermissions 均为 {@code @Transactional(rollbackFor = Exception.class)}；</li>
 *   <li>权限分配：采用全量覆盖（先删后插）策略，变更后清除该角色下所有用户的权限缓存；</li>
 *   <li>系统保护：管理员角色（admin）禁止修改编码与删除；删除前校验角色无用户关联；</li>
 *   <li>性能：列表转 VO 时批量查询权限与用户数（{@link #convertToVOList}），规避 N+1。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class RoleServiceImpl implements IRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final IPermissionService permissionService;

    public RoleServiceImpl(SysRoleMapper sysRoleMapper, SysRolePermissionMapper sysRolePermissionMapper,
                           SysUserRoleMapper sysUserRoleMapper, IPermissionService permissionService) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.permissionService = permissionService;
    }

    /**
     * 分页查询角色列表
     */
    @Override
    public PageResult<RoleVO> queryPage(PageQuery query) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysRole::getSort);
        Page<SysRole> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<SysRole> result = sysRoleMapper.selectPage(page, wrapper);
        List<RoleVO> voList = convertToVOList(result.getRecords());
        return new PageResult<>(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    /**
     * 查询所有启用角色（下拉选择用）
     */
    @Override
    public List<RoleVO> listAll() {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getStatus, CommonConstant.STATUS_ENABLED)
               .orderByAsc(SysRole::getSort);
        List<SysRole> roles = sysRoleMapper.selectList(wrapper);
        return convertToVOList(roles);
    }

    /**
     * 根据ID查询角色详情
     */
    @Override
    public RoleVO getById(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "角色不存在");
        }
        List<RoleVO> list = convertToVOList(Collections.singletonList(role));
        return list.get(0);
    }

    /**
     * 新增角色
     * <p>
     * 校验角色名称/编码非空及编码唯一后落库；若携带 permissionIds 则在同一事务内完成初始权限分配。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO create(RoleSaveDTO dto) {
        log.info("[角色服务] 新增角色开始: roleName={}, roleCode={}", dto.getRoleName(), dto.getRoleCode());
        if (!StringUtils.hasText(dto.getRoleName())) {
            log.warn("[角色服务] 新增角色失败: 角色名称为空");
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色名称不能为空");
        }
        if (!StringUtils.hasText(dto.getRoleCode())) {
            log.warn("[角色服务] 新增角色失败: 角色编码为空");
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色编码不能为空");
        }
        // 角色编码唯一性校验
        SysRole exists = sysRoleMapper.selectByCode(dto.getRoleCode());
        if (exists != null) {
            log.warn("[角色服务] 新增角色失败: 角色编码已存在, roleCode={}", dto.getRoleCode());
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "角色编码已存在");
        }
        // 角色名称唯一性校验（配合 V20260815 uk_role_name 唯一约束，存量重名库约束被跳过时由此兜底）
        Long nameCount = sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleName, dto.getRoleName()));
        if (nameCount != null && nameCount > 0) {
            log.warn("[角色服务] 新增角色失败: 角色名称已存在, roleName={}", dto.getRoleName());
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "角色名称已存在");
        }

        SysRole role = new SysRole();
        role.setRoleName(dto.getRoleName());
        role.setRoleCode(dto.getRoleCode());
        role.setDescription(dto.getDescription());
        role.setSort(dto.getSort() != null ? dto.getSort() : 0);
        role.setStatus(dto.getStatus() != null ? dto.getStatus() : CommonConstant.STATUS_ENABLED);
        sysRoleMapper.insert(role);
        log.info("[角色服务] 新增角色成功: id={}, code={}, name={}", role.getId(), role.getRoleCode(), role.getRoleName());

        // 分配权限
        if (!CollectionUtils.isEmpty(dto.getPermissionIds())) {
            log.info("[角色服务] 新增角色并分配权限: roleId={}, permissionIds={}", role.getId(), dto.getPermissionIds());
            assignPermissions(role.getId(), dto.getPermissionIds());
        } else {
            log.debug("[角色服务] 新增角色未分配权限: roleId={}", role.getId());
        }

        return getById(role.getId());
    }

    /**
     * 修改角色
     * <p>
     * 系统管理员角色禁止修改编码；仅当传入 permissionIds 时才重新全量分配权限。
     * 修改完成后清除该角色下所有用户的权限缓存，使变更即时生效。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO update(Long id, RoleSaveDTO dto) {
        log.info("[角色服务] 修改角色开始: id={}, dto={}", id, dto);
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            log.warn("[角色服务] 修改角色失败: 角色不存在, id={}", id);
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "角色不存在");
        }
        // 系统预设角色不允许修改编码
        if (CommonConstant.ROLE_CODE_ADMIN.equals(role.getRoleCode()) && dto.getRoleCode() != null
                && !CommonConstant.ROLE_CODE_ADMIN.equals(dto.getRoleCode())) {
            log.warn("[角色服务] 修改角色失败: 系统管理员角色编码不允许修改, id={}", id);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "系统管理员角色编码不允许修改");
        }

        // 角色名称唯一性校验（排除自身；配合 V20260815 uk_role_name 唯一约束）
        if (StringUtils.hasText(dto.getRoleName())) {
            Long nameCount = sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleName, dto.getRoleName())
                    .ne(SysRole::getId, id));
            if (nameCount != null && nameCount > 0) {
                log.warn("[角色服务] 修改角色失败: 角色名称已存在, id={}, roleName={}", id, dto.getRoleName());
                throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "角色名称已存在");
            }
            role.setRoleName(dto.getRoleName());
        }
        if (StringUtils.hasText(dto.getRoleCode())) role.setRoleCode(dto.getRoleCode());
        if (dto.getDescription() != null) role.setDescription(dto.getDescription());
        if (dto.getSort() != null) role.setSort(dto.getSort());
        if (dto.getStatus() != null) role.setStatus(dto.getStatus());
        sysRoleMapper.updateById(role);
        log.info("[角色服务] 修改角色成功: id={}, name={}, code={}, status={}",
                id, role.getRoleName(), role.getRoleCode(), role.getStatus());

        // 重新分配权限（仅当传入 permissionIds 时）
        if (dto.getPermissionIds() != null) {
            log.info("[角色服务] 修改角色并重新分配权限: id={}, permissionIds={}", id, dto.getPermissionIds());
            assignPermissions(id, dto.getPermissionIds());
        }

        // 清除该角色下所有用户的权限缓存
        clearRoleUserCache(id);

        return getById(id);
    }

    /**
     * 删除角色
     * <p>
     * 系统管理员角色禁止删除；删除前校验角色下无用户关联，否则拒绝，
     * 通过后在事务内先清理角色-权限关联再删除角色本身。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        log.info("[角色服务] 删除角色开始: id={}", id);
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            log.warn("[角色服务] 删除角色失败: 角色不存在, id={}", id);
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "角色不存在");
        }
        // 系统预设角色不允许删除
        if (CommonConstant.ROLE_CODE_ADMIN.equals(role.getRoleCode())) {
            log.warn("[角色服务] 删除角色失败: 系统管理员角色不允许删除, id={}", id);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "系统管理员角色不允许删除");
        }
        // 检查是否有用户关联
        List<Long> userIds = sysUserRoleMapper.selectUserIdsByRoleId(id);
        if (!userIds.isEmpty()) {
            log.warn("[角色服务] 删除角色失败: 角色下存在用户关联, id={}, userIds={}", id, userIds);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该角色下存在用户关联，无法删除");
        }

        // 删除角色权限关联
        LambdaQueryWrapper<SysRolePermission> rpWrapper = new LambdaQueryWrapper<>();
        rpWrapper.eq(SysRolePermission::getRoleId, id);
        int rpDeleted = sysRolePermissionMapper.delete(rpWrapper);
        // 删除角色
        sysRoleMapper.deleteById(id);
        log.info("[角色服务] 删除角色成功: id={}, name={}, 关联权限数={}", id, role.getRoleName(), rpDeleted);
    }

    /**
     * 分配权限（全量覆盖）
     * <p>
     * 先删除角色原有的全部权限关联，再逐条插入新的关联，实现全量替换；
     * 完成后清除该角色下所有用户的权限缓存，保证鉴权即时生效。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        log.info("[角色服务] 分配角色权限开始: roleId={}, permissionIds={}", roleId, permissionIds);
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            log.warn("[角色服务] 分配权限失败: 角色不存在, roleId={}", roleId);
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "角色不存在");
        }
        // 先删除原有权限关联
        LambdaQueryWrapper<SysRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRolePermission::getRoleId, roleId);
        int deleted = sysRolePermissionMapper.delete(wrapper);
        log.debug("[角色服务] 清除角色原有权限关联: roleId={}, 删除记录数={}", roleId, deleted);
        // 再插入新的权限关联
        // 【修复M7】原实现循环单条 insert，N 次 DB 交互效率低；改为一次性批量插入。
        int inserted = 0;
        if (!CollectionUtils.isEmpty(permissionIds)) {
            inserted = sysRolePermissionMapper.batchInsert(roleId, permissionIds);
        }
        // 清除该角色下所有用户的权限缓存
        clearRoleUserCache(roleId);
        log.info("[角色服务] 分配角色权限成功: roleId={}, roleName={}, 原权限数={}, 新权限数={}",
                roleId, role.getRoleName(), deleted, inserted);
    }

    /**
     * 分配权限（DTO入口）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, AssignPermissionDTO dto) {
        log.info("[角色服务] 分配权限(DTO入口): roleId={}, dto={}", roleId, dto);
        assignPermissions(roleId, dto.getPermissionIds());
    }

    /**
     * 清除角色下所有用户的权限缓存
     */
    private void clearRoleUserCache(Long roleId) {
        List<Long> userIds = sysUserRoleMapper.selectUserIdsByRoleId(roleId);
        if (userIds.isEmpty()) {
            log.debug("[角色服务] 角色下无关联用户，无需清缓存: roleId={}", roleId);
            return;
        }
        log.info("[角色服务] 清除角色下用户权限缓存: roleId={}, userIds={}", roleId, userIds);
        permissionService.clearUsersCache(userIds);
    }

    /**
     * 转换为VO列表（批量查询权限ID和用户数，避免N+1）
     * <p>
     * 收集所有 roleId 后一次性批量查询角色-权限关联与用户-角色关联，
     * 在内存中按 roleId 分组回填每个角色的权限ID列表与关联用户数，避免逐角色查询。
     */
    private List<RoleVO> convertToVOList(List<SysRole> roles) {
        if (CollectionUtils.isEmpty(roles)) {
            return Collections.emptyList();
        }
        List<Long> roleIds = roles.stream().map(SysRole::getId).collect(Collectors.toList());

        // 批量查询角色权限关联
        LambdaQueryWrapper<SysRolePermission> rpWrapper = new LambdaQueryWrapper<>();
        rpWrapper.in(SysRolePermission::getRoleId, roleIds);
        List<SysRolePermission> rolePerms = sysRolePermissionMapper.selectList(rpWrapper);
        Map<Long, List<Long>> permsByRole = rolePerms.stream()
                .collect(Collectors.groupingBy(SysRolePermission::getRoleId,
                        Collectors.mapping(SysRolePermission::getPermissionId, Collectors.toList())));

        // 批量查询用户角色关联（统计用户数）
        LambdaQueryWrapper<SysUserRole> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.in(SysUserRole::getRoleId, roleIds);
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(urWrapper);
        Map<Long, Long> userCountByRole = userRoles.stream()
                .collect(Collectors.groupingBy(SysUserRole::getRoleId, Collectors.counting()));

        List<RoleVO> result = new ArrayList<>(roles.size());
        for (SysRole role : roles) {
            RoleVO vo = new RoleVO();
            vo.setId(role.getId());
            vo.setRoleName(role.getRoleName());
            vo.setRoleCode(role.getRoleCode());
            vo.setDescription(role.getDescription());
            vo.setSort(role.getSort());
            vo.setStatus(role.getStatus());
            vo.setCreatedAt(role.getCreatedAt());
            vo.setUpdatedAt(role.getUpdatedAt());
            vo.setPermissionIds(permsByRole.getOrDefault(role.getId(), Collections.emptyList()));
            vo.setUserCount(userCountByRole.getOrDefault(role.getId(), 0L).intValue());
            result.add(vo);
        }
        return result;
    }

}
