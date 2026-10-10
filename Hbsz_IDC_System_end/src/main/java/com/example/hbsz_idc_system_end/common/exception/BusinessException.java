package com.example.hbsz_idc_system_end.common.exception;

import com.example.hbsz_idc_system_end.common.result.ResultCode;
import lombok.Getter;

/**
 * 自定义业务异常
 * <p>
 * 继承 {@link RuntimeException}，用于在业务逻辑中主动抛出可预期的错误
 * （如参数不合法、数据不存在、无权限等）。异常携带业务错误码 {@code code}，
 * 由 {@code GlobalExceptionHandler} 统一捕获并转换为标准响应返回给前端。
 * 提供多个构造器以适配直接传消息、传错误码、或使用 {@link ResultCode} 枚举等场景。
 *
 * @author hbsz
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 业务错误码，对应 {@link ResultCode} 中定义的 code
     */
    private final Integer code;

    /**
     * 使用自定义消息构造，错误码默认为 {@link ResultCode#BUSINESS_ERROR}
     *
     * @param message 错误消息
     */
    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
    }

    /**
     * 使用自定义错误码与消息构造
     *
     * @param code    业务错误码
     * @param message 错误消息
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 使用 {@link ResultCode} 枚举构造，消息取枚举默认消息
     *
     * @param resultCode 响应状态码枚举
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 使用 {@link ResultCode} 枚举的错误码 + 自定义消息构造
     *
     * @param resultCode 响应状态码枚举（仅取其 code）
     * @param message    自定义错误消息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

}
