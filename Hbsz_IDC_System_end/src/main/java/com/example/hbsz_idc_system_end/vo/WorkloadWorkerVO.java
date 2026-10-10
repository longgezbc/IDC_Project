package com.example.hbsz_idc_system_end.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作量填报人员视图对象（项目组在组成员）。
 * <p>
 * 填报人员来源：项目组（biz_project_team）在组成员，
 * 组长置前展示，成员按姓名排序。
 *
 * @author hbsz
 */
@Data
public class WorkloadWorkerVO implements Serializable {

    /** 成员用户ID */
    private Long userId;

    /** 成员姓名 */
    private String name;

    /** 组内角色：leader 组长 / member 成员 */
    private String roleInTeam;

}
