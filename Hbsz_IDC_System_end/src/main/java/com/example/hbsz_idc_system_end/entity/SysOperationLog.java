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
 * 系统操作日志实体
 * <p>
 * 对应数据库表 sys_operation_log，记录用户在系统中的关键操作行为，用于审计与问题追溯。
 * userId 关联操作用户（sys_user）。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog implements Serializable {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作用户ID */
    private Long userId;

    /** 操作用户名 */
    private String username;

    /** 操作模块 */
    private String module;

    /** 业务类型：login/create/update/delete/export等 */
    private String businessType;

    /** 请求方法（如 Controller.方法名） */
    private String method;

    /** 请求URL */
    private String requestUrl;

    /** 请求参数（JSON），@TableField 映射数据库列 request_param */
    @TableField("request_param")
    private String requestParams;

    /** 响应结果（JSON，可选） */
    private String responseResult;

    /** 操作人IP */
    private String ip;

    /** 操作状态：1成功 0失败 */
    private Integer status;

    /** 错误消息（失败时） */
    private String errorMsg;

    /** 耗时（毫秒） */
    private Long costTime;

    /** 操作时间，@TableField 映射数据库列 created_at */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}
