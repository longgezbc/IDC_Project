package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 项目组成员添加 DTO
 * <p>
 * 由 PM/admin 调用 POST /api/project-teams/members 添加项目组成员时传入。
 *
 * @author hbsz
 */
@Data
public class TeamMemberAddDTO implements Serializable {

    /** 项目ID（必填） */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 成员用户ID（必填） */
    @NotNull(message = "成员用户ID不能为空")
    private Long userId;

    /** 组内角色：leader 组长 / member 成员 / observer 观察者，默认 member */
    private String roleInTeam;

    /** 备注 */
    private String remark;

}
