package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目组成员实体
 * <p>
 * 对应数据库表 biz_project_team。一个项目可组建团队，成员包含 leader/member/observer 三种角色，
 * 用于项目可见范围 team_scope=team 时的数据权限控制，支持跨部门协作。
 * <p>
 * 字段说明：
 * <ul>
 *   <li>{@link #roleInTeam}：组内角色，leader=组长 / member=成员 / observer=观察者；</li>
 *   <li>{@link #joinedAt}：加入时间；</li>
 *   <li>{@link #leftAt}：离组时间，离组后置 {@link #isActive}=0；</li>
 *   <li>{@link #isActive}：是否在组：1是 0否。</li>
 * </ul>
 *
 * @author hbsz
 */
@Data
@TableName("biz_project_team")
public class BizProjectTeam implements Serializable {

    /** 主键ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID（FK→biz_project.id） */
    private Long projectId;

    /** 成员用户ID（FK→sys_user.id） */
    private Long userId;

    /** 组内角色：leader 组长 / member 成员 / observer 观察者 */
    private String roleInTeam;

    /** 加入时间 */
    private LocalDateTime joinedAt;

    /** 离组时间（仅 is_active=0 时有值） */
    private LocalDateTime leftAt;

    /** 是否在组：1是 0否 */
    private Integer isActive;

    /** 备注 */
    private String remark;

}
