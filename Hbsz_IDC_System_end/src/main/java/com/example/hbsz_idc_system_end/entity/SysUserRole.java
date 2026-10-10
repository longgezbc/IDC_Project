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
 * 用户角色关联实体
 * <p>
 * 对应数据库表 sys_user_role，是用户（sys_user）与角色（sys_role）多对多关系的中间表，
 * 每条记录表示某用户被授予某角色。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("sys_user_role")
public class SysUserRole implements Serializable {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID，关联 sys_user.id */
    private Long userId;

    /** 角色ID，关联 sys_role.id */
    private Long roleId;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}
