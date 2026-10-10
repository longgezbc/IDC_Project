package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目组成员视图对象
 * <p>
 * 用于返回项目组成员详情，包含用户姓名、电话、岗位等冗余字段。
 *
 * @author hbsz
 */
@Data
public class TeamMemberVO implements Serializable {

    /** 记录ID */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 成员用户ID */
    private Long userId;

    /** 组内角色：leader 组长 / member 成员 / observer 观察者 */
    private String roleInTeam;

    /** 加入时间 */
    private LocalDateTime joinedAt;

    /** 离组时间 */
    private LocalDateTime leftAt;

    /** 是否在组：1是 0否 */
    private Integer isActive;

    /** 备注 */
    private String remark;

    /** 成员姓名（冗余） */
    private String userName;

    /** 成员用户名（冗余） */
    private String userUsername;

    /** 成员手机号（冗余） */
    private String userPhone;

    /** 岗位名称（冗余，关联 sys_position.name） */
    private String positionName;

    /** 项目名称（冗余，关联 biz_project.name） */
    private String projectName;

}
