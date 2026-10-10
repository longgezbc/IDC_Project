package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.AssignPermissionDTO;
import com.example.hbsz_idc_system_end.dto.PageQuery;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.RoleSaveDTO;
import com.example.hbsz_idc_system_end.dto.RoleVO;

import java.util.List;

/**
 * 角色管理服务接口
 * 提供角色定义、角色分配、权限配置等功能
 *
 * @author hbsz
 */
public interface IRoleService {

    /**
     * 分页查询角色列表
     *
     * @param query 分页查询参数
     * @return 角色分页结果
     */
    PageResult<RoleVO> queryPage(PageQuery query);

    /**
     * 查询所有启用角色（下拉选择用）
     *
     * @return 启用状态的角色列表
     */
    List<RoleVO> listAll();

    /**
     * 根据ID查询角色详情
     *
     * @param id 角色ID
     * @return 角色详情 VO（含已分配的权限）
     */
    RoleVO getById(Long id);

    /**
     * 新增角色
     *
     * @param dto 角色保存参数
     * @return 新增后的角色 VO
     */
    RoleVO create(RoleSaveDTO dto);

    /**
     * 修改角色
     *
     * @param id  角色ID
     * @param dto 角色保存参数
     * @return 修改后的角色 VO
     */
    RoleVO update(Long id, RoleSaveDTO dto);

    /**
     * 删除角色
     *
     * @param id 角色ID
     */
    void delete(Long id);

    /**
     * 分配权限（全量覆盖）
     * <p>
     * 以传入的权限ID列表全量替换角色原有权限，需同步清除相关用户的权限缓存。
     *
     * @param roleId        角色ID
     * @param permissionIds 权限ID列表
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);

    /**
     * 分配权限（DTO入口）
     *
     * @param roleId 角色ID
     * @param dto    权限分配参数
     */
    void assignPermissions(Long roleId, AssignPermissionDTO dto);

}
