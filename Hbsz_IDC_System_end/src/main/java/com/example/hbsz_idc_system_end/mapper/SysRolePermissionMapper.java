package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色权限关联数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link SysRolePermission}，映射角色与权限的多对多关联表 {@code sys_role_permission}，
 * 负责维护角色与权限的绑定关系。除 MyBatis-Plus 通用方法外，提供按角色反查权限ID的方法，
 * 用于 RBAC 权限体系的授权配置。
 *
 * @author hbsz
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    /**
     * 根据角色ID查询其关联的权限ID列表。
     *
     * @param roleId 角色ID
     * @return 该角色绑定的权限ID列表
     */
    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量插入角色权限关联（XML 实现，使用 foreach 拼接 VALUES）。
     * <p>
     * 【修复M7】替代原 RoleServiceImpl 中循环单条 insert 的低效实现，一次性批量插入。
     *
     * @param roleId        角色ID
     * @param permissionIds 权限ID列表
     * @return 插入行数
     */
    int batchInsert(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);

}
