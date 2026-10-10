package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目转移日志视图对象
 * <p>
 * 审计日志展示用，已包含项目名、工程师姓名等冗余字段，便于前端直接渲染。
 *
 * @author hbsz
 */
@Data
public class TransferLogVO implements Serializable {

    /** 日志ID */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** 操作类型：assign / revoke / transfer */
    private String actionType;

    /** 原工程师ID */
    private Long fromEngineerId;

    /** 原工程师姓名 */
    private String fromEngineerName;

    /** 目标工程师ID */
    private Long toEngineerId;

    /** 目标工程师姓名 */
    private String toEngineerName;

    /** 操作人ID */
    private Long operatedBy;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作时间 */
    private LocalDateTime operatedAt;

    /** 操作备注 */
    private String remark;

}
