package com.example.hbsz_idc_system_end.dto;

import com.example.hbsz_idc_system_end.entity.SysOperationLog;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志视图对象。
 * <p>
 * requestParams / responseResult / errorMsg 由切面截断至安全长度（2000/500 字符），
 * 列表页不渲染长文本字段，详情抽屉按 JSON 格式化展示，单页数据量可控。
 *
 * @author hbsz
 */
@Data
public class OperationLogVO implements Serializable {

    /** 日志ID */
    private Long id;

    /** 操作用户ID */
    private Long userId;

    /** 操作用户名 */
    private String username;

    /** 操作模块 */
    private String module;

    /** 业务类型：新增/修改/删除/导出等 */
    private String businessType;

    /** 请求方法（Controller.方法名） */
    private String method;

    /** 请求URL */
    private String requestUrl;

    /** 请求参数（JSON，已截断） */
    private String requestParams;

    /** 响应结果（JSON，可选，已截断） */
    private String responseResult;

    /** 操作人IP */
    private String ip;

    /** 执行状态：1成功 0失败 */
    private Integer status;

    /** 错误消息（失败时） */
    private String errorMsg;

    /** 耗时（毫秒） */
    private Long costTime;

    /** 操作时间 */
    private LocalDateTime createdAt;

    /**
     * 由实体构造 VO（字段一一映射）。
     *
     * @param log 操作日志实体
     * @return 视图对象
     */
    public static OperationLogVO from(SysOperationLog log) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setModule(log.getModule());
        vo.setBusinessType(log.getBusinessType());
        vo.setMethod(log.getMethod());
        vo.setRequestUrl(log.getRequestUrl());
        vo.setRequestParams(log.getRequestParams());
        vo.setResponseResult(log.getResponseResult());
        vo.setIp(log.getIp());
        vo.setStatus(log.getStatus());
        vo.setErrorMsg(log.getErrorMsg());
        vo.setCostTime(log.getCostTime());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }
}
