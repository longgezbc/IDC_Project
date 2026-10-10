package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.PermissionSaveDTO;
import com.example.hbsz_idc_system_end.dto.PermissionVO;

import java.util.List;

/**
 * 权限管理服务接口
 * 提供权限项的精细化管理，支持权限粒度控制、权限继承（树形结构）、权限分配
 *
 * @author hbsz
 */
public interface IPermissionManageService {

    /**
     * 查询所有权限（平铺列表）
     *
     * @return 全部权限项列表
     */
    List<PermissionVO> listAll();

    /**
     * 查询权限树形结构
     * <p>
     * 按父子关系（parentId）组织为树，用于前端权限分配树形展示。
     *
     * @return 树形权限列表（顶层节点，子节点挂在 children 上）
     */
    List<PermissionVO> listTree();

    /**
     * 根据ID查询权限详情
     *
     * @param id 权限ID
     * @return 权限详情 VO
     */
    PermissionVO getById(Long id);

    /**
     * 新增权限
     *
     * @param dto 权限保存参数（含权限标识、名称、父级、类型等）
     * @return 新增后的权限 VO
     */
    PermissionVO create(PermissionSaveDTO dto);

    /**
     * 修改权限
     *
     * @param id  权限ID
     * @param dto 权限保存参数
     * @return 修改后的权限 VO
     */
    PermissionVO update(Long id, PermissionSaveDTO dto);

    /**
     * 删除权限
     *
     * @param id 权限ID
     */
    void delete(Long id);

}
