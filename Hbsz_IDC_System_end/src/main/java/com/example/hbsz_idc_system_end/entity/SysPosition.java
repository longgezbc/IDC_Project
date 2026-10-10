package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 岗位职级实体
 * <p>
 * 对应数据库表 sys_position，定义系统中的岗位体系。岗位是用户权限体系的重要补充：
 * 通过 sys_user.position_id 与用户（sys_user）一对一关联，实现基于岗位的功能权限与数据权限控制。
 * level 字段表示职级（1 最高，9 最低），用于岗位间的层级比较。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("sys_position")
public class SysPosition extends BaseEntity {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位编码（唯一，如 pm/senior_engineer/engineer/junior_engineer/outsourcing/viewer） */
    private String code;

    /** 岗位名称 */
    private String name;

    /** 职级（1最高，9最低） */
    private Integer level;

    /** 岗位描述 */
    private String description;

    /** 状态：1启用 0禁用 */
    private Integer status;

}
