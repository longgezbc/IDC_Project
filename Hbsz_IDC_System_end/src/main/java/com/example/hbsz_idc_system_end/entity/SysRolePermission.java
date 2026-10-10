package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色权限关联实体
 * <p>
 * 对应数据库表 sys_role_permission，是角色（sys_role）与权限（sys_permission）多对多关系的中间表，
 * 每条记录表示某角色被授予某权限。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission implements Serializable {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID，关联 sys_role.id */
    private Long roleId;

    /** 权限ID，关联 sys_permission.id */
    private Long permissionId;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}
