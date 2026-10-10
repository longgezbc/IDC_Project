package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目转移/授权操作审计日志实体
 * <p>
 * 对应数据库表 biz_project_transfer_log。每次 assign / revoke / transfer 操作都会
 * 写入一条记录，保留完整审计链路：操作人（PM）、原工程师、目标工程师、项目、时间、备注。
 * 用于合规审计与操作追溯，禁止删除（仅追加）。
 *
 * @author hbsz
 */
@Data
@TableName("biz_project_transfer_log")
public class BizProjectTransferLog implements Serializable {

    /** 主键ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 项目名称（冗余，便于审计展示） */
    private String projectName;

    /** 操作类型：assign（授权）/ revoke（撤销）/ transfer（转移） */
    private String actionType;

    /** 原工程师ID（assign 时为 null） */
    private Long fromEngineerId;

    /** 原工程师姓名（冗余） */
    private String fromEngineerName;

    /** 目标工程师ID（revoke 时为 null） */
    private Long toEngineerId;

    /** 目标工程师姓名（冗余） */
    private String toEngineerName;

    /** 操作人ID（系统自动操作——如授权过期定时回收——时为 null，见迁移 V20260820.1） */
    private Long operatedBy;

    /** 操作人姓名（冗余） */
    private String operatorName;

    /** 操作时间 */
    private LocalDateTime operatedAt;

    /** 操作备注 */
    private String remark;

}
