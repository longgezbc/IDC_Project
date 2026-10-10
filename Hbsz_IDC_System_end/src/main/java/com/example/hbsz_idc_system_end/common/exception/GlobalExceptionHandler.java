package com.example.hbsz_idc_system_end.common.exception;

import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 基于 {@code @RestControllerAdvice}，统一拦截 Controller 层抛出的各类异常，
 * 转换为标准化的 {@link Result} 错误响应并设置合适的 HTTP 状态码，
 * 避免异常堆栈直接暴露给前端，同时按级别记录日志。
 * <p>
 * 覆盖范围：业务异常、参数校验/绑定/约束异常、请求参数缺失、参数类型不匹配、
 * 404 无处理器、文件上传超限，以及兜底的未知异常。
 *
 * @author hbsz
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常，返回异常自带的错误码与消息（WARN 级别）
     *
     * @param e 业务异常
     * @return 标准错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理 {@code @RequestBody @Valid} 参数校验异常，汇总各字段错误信息返回
     *
     * @param e 方法参数校验异常
     * @return 参数校验失败响应（HTTP 400）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.error(ResultCode.PARAM_VALIDATE_FAILED, message);
    }

    /**
     * 处理表单参数绑定异常，汇总各字段错误信息返回
     *
     * @param e 参数绑定异常
     * @return 参数校验失败响应（HTTP 400）
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", message);
        return Result.error(ResultCode.PARAM_VALIDATE_FAILED, message);
    }

    /**
     * 处理约束违反异常（{@code @Validated} 标注在 Controller 上时的方法级校验）
     *
     * @param e 约束违反异常
     * @return 参数校验失败响应（HTTP 400）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: {}", message);
        return Result.error(ResultCode.PARAM_VALIDATE_FAILED, message);
    }

    /**
     * 处理请求必填参数缺失异常
     *
     * @param e 请求参数缺失异常
     * @return 提示缺失参数名的错误响应（HTTP 400）
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("请求参数缺失: {}", e.getParameterName());
        return Result.error(ResultCode.BAD_REQUEST, "请求参数缺失: " + e.getParameterName());
    }

    /**
     * 处理参数类型不匹配异常（如路径变量/查询参数无法转换为目标类型）
     *
     * @param e 参数类型不匹配异常
     * @return 提示出错参数名的错误响应（HTTP 400）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST, "参数类型不匹配: " + e.getName());
    }

    /**
     * 处理 404（未找到匹配的处理器）异常
     *
     * @param e 无处理器异常
     * @return 资源不存在响应（HTTP 404）
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("请求路径不存在: {}", e.getRequestURL());
        return Result.error(ResultCode.NOT_FOUND);
    }

    /**
     * 处理文件上传大小超限异常
     *
     * @param e 上传大小超限异常
     * @return 文件超限响应（HTTP 413）
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件上传超过大小限制: {}", e.getMessage());
        return Result.error(ResultCode.FILE_SIZE_EXCEEDED, "上传文件过大，最大支持 100MB");
    }

    /**
     * 处理 405（请求方法不支持）异常
     * <p>
     * 客户端错误，WARN 级别记录，包含请求 URL 和方法，便于定位接口。
     *
     * @param e 请求方法不支持异常
     * @return 方法不支持响应（HTTP 405）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("请求方法不支持: {} {} (支持: {})", request.getMethod(), request.getRequestURI(), e.getSupportedHttpMethods());
        return Result.error(ResultCode.BAD_REQUEST, "请求方法不支持: " + request.getMethod());
    }

    /**
     * 处理 415（Content-Type 不支持）异常
     * <p>
     * 客户端错误，WARN 级别记录，包含请求 URL 和 Content-Type，便于定位接口。
     *
     * @param e Content-Type 不支持异常
     * @return 不支持的媒体类型响应（HTTP 415）
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<Void> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        log.warn("Content-Type不支持: {} {} (收到: {})", request.getMethod(), request.getRequestURI(), e.getContentType());
        return Result.error(ResultCode.BAD_REQUEST, "不支持的Content-Type: " + e.getContentType());
    }

    /**
     * 兜底处理所有未被上述处理器捕获的异常，记录 ERROR 日志并返回通用系统错误，
     * 不向前端暴露内部堆栈细节
     *
     * @param e 未捕获的异常
     * @return 系统内部错误响应（HTTP 500）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {} {} | {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.error(ResultCode.SYSTEM_ERROR, "系统内部错误，请联系管理员");
    }

}
