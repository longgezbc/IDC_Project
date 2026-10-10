package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.PermissionSaveDTO;
import com.example.hbsz_idc_system_end.dto.PermissionVO;
import com.example.hbsz_idc_system_end.entity.SysPermission;
import com.example.hbsz_idc_system_end.entity.SysRolePermission;
import com.example.hbsz_idc_system_end.mapper.SysPermissionMapper;
import com.example.hbsz_idc_system_end.mapper.SysRolePermissionMapper;
import com.example.hbsz_idc_system_end.service.IPermissionManageService;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限管理服务实现
 * <p>
 * 提供权限项的精细化管理：平铺/树形查询、增删改，支持权限继承（parentId 树形结构）。
 * 关键约束：
 * <ul>
 *   <li>事务：create/update/delete 均为 {@code @Transactional(rollbackFor = Exception.class)}，
 *       删除时联动清理角色-权限关联表；</li>
 *   <li>缓存联动：权限项变更会影响所有用户，写操作成功后统一调用
 *       {@link IPermissionService#clearAllUserCache()} 清空全部用户权限缓存；</li>
 *   <li>完整性：新增校验权限标识唯一，删除前校验无子权限，防止破坏树形结构。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class PermissionManageServiceImpl implements IPermissionManageService {

    private final SysPermissionMapper sysPermissionMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final IPermissionService permissionService;

    public PermissionManageServiceImpl(SysPermissionMapper sysPermissionMapper,
                                       SysRolePermissionMapper sysRolePermissionMapper,
                                       IPermissionService permissionService) {
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.permissionService = permissionService;
    }

    /**
     * 查询所有权限（平铺列表）
     */
    @Override
    public List<PermissionVO> listAll() {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysPermission::getParentId)
               .orderByAsc(SysPermission::getSort);
        List<SysPermission> list = sysPermissionMapper.selectList(wrapper);
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 查询权限树形结构
     */
    @Override
    public List<PermissionVO> listTree() {
        List<PermissionVO> all = listAll();
        return buildTree(all);
    }

    /**
     * 根据ID查询权限详情
     */
    @Override
    public PermissionVO getById(Long id) {
        SysPermission perm = sysPermissionMapper.selectById(id);
        if (perm == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "权限不存在");
        }
        return convertToVO(perm);
    }

    /**
     * 新增权限
     * <p>
     * 先校验名称/标识非空及权限标识全局唯一，落库后因权限项影响所有用户，
     * 立即清空全部用户权限缓存以使变更即时生效。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionVO create(PermissionSaveDTO dto) {
        if (!StringUtils.hasText(dto.getPermissionName())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "权限名称不能为空");
        }
        if (!StringUtils.hasText(dto.getPermissionCode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "权限标识不能为空");
        }
        // 权限标识唯一性校验
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermission::getPermissionCode, dto.getPermissionCode());
        Long count = sysPermissionMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "权限标识已存在");
        }

        SysPermission perm = new SysPermission();
        perm.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        perm.setPermissionName(dto.getPermissionName());
        perm.setPermissionCode(dto.getPermissionCode());
        perm.setPermissionType(dto.getPermissionType() != null ? dto.getPermissionType() : CommonConstant.PERMISSION_TYPE_MENU);
        perm.setPath(dto.getPath());
        perm.setComponent(dto.getComponent());
        perm.setIcon(dto.getIcon());
        perm.setSort(dto.getSort() != null ? dto.getSort() : 0);
        perm.setIsVisible(dto.getIsVisible() != null ? dto.getIsVisible() : CommonConstant.YES);
        perm.setStatus(dto.getStatus() != null ? dto.getStatus() : CommonConstant.STATUS_ENABLED);
        perm.setRemark(dto.getRemark());
        sysPermissionMapper.insert(perm);
        log.info("新增权限成功: id={}, code={}", perm.getId(), perm.getPermissionCode());

        // 权限项变更可能影响所有用户，清除全部缓存
        permissionService.clearAllUserCache();

        return convertToVO(perm);
    }

    /**
     * 修改权限
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionVO update(Long id, PermissionSaveDTO dto) {
        SysPermission perm = sysPermissionMapper.selectById(id);
        if (perm == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "权限不存在");
        }
        // 不允许将自己设为自己的父节点
        if (dto.getParentId() != null && dto.getParentId().equals(id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能将自身设为父权限");
        }

        if (dto.getParentId() != null) perm.setParentId(dto.getParentId());
        if (StringUtils.hasText(dto.getPermissionName())) perm.setPermissionName(dto.getPermissionName());
        if (StringUtils.hasText(dto.getPermissionCode())) perm.setPermissionCode(dto.getPermissionCode());
        if (dto.getPermissionType() != null) perm.setPermissionType(dto.getPermissionType());
        if (dto.getPath() != null) perm.setPath(dto.getPath());
        if (dto.getComponent() != null) perm.setComponent(dto.getComponent());
        if (dto.getIcon() != null) perm.setIcon(dto.getIcon());
        if (dto.getSort() != null) perm.setSort(dto.getSort());
        if (dto.getIsVisible() != null) perm.setIsVisible(dto.getIsVisible());
        if (dto.getStatus() != null) perm.setStatus(dto.getStatus());
        if (dto.getRemark() != null) perm.setRemark(dto.getRemark());
        sysPermissionMapper.updateById(perm);
        log.info("修改权限成功: id={}", id);

        permissionService.clearAllUserCache();
        return convertToVO(perm);
    }

    /**
     * 删除权限
     * <p>
     * 删除前校验：存在子权限则禁止删除（保证树形结构完整）；
     * 事务内先清理角色-权限关联再删除权限本身，最后清空全部用户权限缓存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysPermission perm = sysPermissionMapper.selectById(id);
        if (perm == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "权限不存在");
        }
        // 检查是否有子权限
        LambdaQueryWrapper<SysPermission> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(SysPermission::getParentId, id);
        Long childCount = sysPermissionMapper.selectCount(childWrapper);
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "存在子权限，无法删除");
        }
        // 删除角色权限关联
        LambdaQueryWrapper<SysRolePermission> rpWrapper = new LambdaQueryWrapper<>();
        rpWrapper.eq(SysRolePermission::getPermissionId, id);
        sysRolePermissionMapper.delete(rpWrapper);
        // 删除权限
        sysPermissionMapper.deleteById(id);
        log.info("删除权限成功: id={}", id);

        permissionService.clearAllUserCache();
    }

    /**
     * 构建树形结构
     * <p>
     * 先按 parentId 分组（顶层归入 0L），再为每个节点挂载其子节点列表，
     * 最终返回顶层节点。时间复杂度 O(n)，避免递归查询数据库。
     */
    private List<PermissionVO> buildTree(List<PermissionVO> all) {
        Map<Long, List<PermissionVO>> byParent = all.stream()
                .collect(Collectors.groupingBy(p -> p.getParentId() == null ? 0L : p.getParentId()));
        all.forEach(p -> p.setChildren(byParent.getOrDefault(p.getId(), Collections.emptyList())));
        return byParent.getOrDefault(0L, Collections.emptyList());
    }

    /**
     * 实体转VO
     */
    private PermissionVO convertToVO(SysPermission perm) {
        PermissionVO vo = new PermissionVO();
        vo.setId(perm.getId());
        vo.setParentId(perm.getParentId());
        vo.setPermissionName(perm.getPermissionName());
        vo.setPermissionCode(perm.getPermissionCode());
        vo.setPermissionType(perm.getPermissionType());
        vo.setPath(perm.getPath());
        vo.setComponent(perm.getComponent());
        vo.setIcon(perm.getIcon());
        vo.setSort(perm.getSort());
        vo.setIsVisible(perm.getIsVisible());
        vo.setStatus(perm.getStatus());
        vo.setRemark(perm.getRemark());
        vo.setCreatedAt(perm.getCreatedAt());
        return vo;
    }

}
