package com.example.hbsz_idc_system_end.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统统一响应状态码枚举
 * <p>
 * 集中定义系统对外返回的业务状态码及其默认提示消息，按区段划分：
 * 成功(200)、客户端错误(4xx)、业务错误(1xxx)、认证授权错误(2xxx)、
 * 通用业务错误(3xxx)、系统错误(5xxx)。错误码定义与 API_DOCS.md 保持一致，
 * 供 {@link Result} 与 {@code BusinessException} 统一引用。
 *
 * @author hbsz
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    /* 成功状态码 */
    SUCCESS(200, "操作成功"),

    /* 客户端错误 4xx */
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或认证已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不被允许"),

    /* 业务错误 1xxx（与 API 文档一致） */
    USERNAME_OR_PASSWORD_ERROR(1001, "用户名或密码错误"),
    ACCOUNT_DISABLED(1002, "账号已被禁用"),
    PROJECT_CODE_EXISTS(1003, "项目编号已存在"),
    PROJECT_NOT_FOUND(1004, "项目不存在"),
    DOCUMENT_NOT_FOUND(1005, "文档不存在"),
    TASK_NOT_FOUND(1006, "任务不存在"),
    FILE_SIZE_EXCEEDED(1007, "文件大小超限"),
    FILE_TYPE_NOT_SUPPORTED(1008, "文件类型不支持"),

    /* 认证授权错误 2xxx */
    TOKEN_INVALID(2001, "Token无效"),
    TOKEN_EXPIRED(2002, "Token已过期"),
    NO_PERMISSION(2003, "无操作权限"),
    OLD_PASSWORD_ERROR(2004, "原密码错误"),
    ACCOUNT_LOCKED(2005, "账号已被锁定，请稍后再试"),
    PASSWORD_TOO_WEAK(2006, "密码强度不足，需包含大小写字母、数字和特殊字符，且长度不少于8位"),

    /* 通用业务错误 3xxx */
    BUSINESS_ERROR(3000, "业务处理异常"),
    PARAM_VALIDATE_FAILED(3001, "参数校验失败"),
    DATA_NOT_FOUND(3002, "数据不存在"),
    DATA_ALREADY_EXISTS(3003, "数据已存在"),
    DATA_DELETE_FAILED(3004, "数据删除失败"),
    TOO_MANY_REQUESTS(3005, "请求过于频繁，请稍后再试"),
    CAPTCHA_ERROR(3006, "验证码错误或已过期"),
    PROJECT_NOT_AUTHORIZED(3007, "无权访问该项目"),
    ENGINEER_NOT_ACTIVE(3008, "目标工程师不存在或已禁用"),
    ENGINEER_NOT_ENGINEER_ROLE(3009, "目标用户不具备工程师角色"),

    /* 系统错误 5xxx */
    SYSTEM_ERROR(5000, "系统内部错误"),
    DATABASE_ERROR(5001, "数据库操作异常"),
    NETWORK_ERROR(5002, "网络异常");

    /** 状态码 */
    private final Integer code;
    /** 默认提示消息 */
    private final String message;

}
