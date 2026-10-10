package com.example.hbsz_idc_system_end.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 响应结果封装
 * <p>
 * 所有 Controller 接口统一返回该结构，保证前后端交互格式一致。
 * 通过私有构造 + 静态工厂方法创建实例，避免手动 new，并自动写入时间戳。
 *
 * @param <T> 业务数据类型
 * @author hbsz
 */
@Data
public class Result<T> implements Serializable {

    /**
     * 响应状态码，取值参见 {@link ResultCode}
     */
    private Integer code;

    /**
     * 响应提示消息
     */
    private String message;

    /**
     * 业务数据载体，失败时通常为 null
     */
    private T data;

    /**
     * 响应生成时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * 私有构造器，创建时自动记录当前时间戳
     */
    private Result() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 成功返回（无数据）
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
        return result;
    }

    /**
     * 成功返回（带数据）
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 携带数据的成功响应
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    /**
     * 成功返回（自定义消息 + 数据）
     *
     * @param message 自定义提示消息
     * @param data    业务数据
     * @param <T>     数据类型
     * @return 携带自定义消息与数据的成功响应
     */
    public static <T> Result<T> success(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    /**
     * 失败返回（自定义消息），错误码默认为 {@link ResultCode#BUSINESS_ERROR}
     *
     * @param message 错误提示消息
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.BUSINESS_ERROR.getCode());
        result.setMessage(message);
        return result;
    }

    /**
     * 失败返回（自定义状态码 + 消息）
     *
     * @param code    错误状态码
     * @param message 错误提示消息
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /**
     * 失败返回（使用 {@link ResultCode} 枚举，取其默认消息）
     *
     * @param resultCode 响应状态码枚举
     * @param <T>        数据类型
     * @return 失败响应
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMessage(resultCode.getMessage());
        return result;
    }

    /**
     * 失败返回（使用 {@link ResultCode} 枚举的错误码 + 自定义消息）
     *
     * @param resultCode 响应状态码枚举（仅取其 code）
     * @param message    自定义错误消息
     * @param <T>        数据类型
     * @return 失败响应
     */
    public static <T> Result<T> error(ResultCode resultCode, String message) {
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMessage(message);
        return result;
    }

}
