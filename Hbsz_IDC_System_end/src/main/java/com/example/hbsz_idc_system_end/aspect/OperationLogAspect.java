package com.example.hbsz_idc_system_end.aspect;

import com.example.hbsz_idc_system_end.common.annotation.OperationLog;
import com.example.hbsz_idc_system_end.entity.SysOperationLog;
import com.example.hbsz_idc_system_end.mapper.SysOperationLogMapper;
import com.example.hbsz_idc_system_end.utils.IpUtils;
import com.example.hbsz_idc_system_end.utils.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 操作日志切面
 * <p>
 * 拦截标注了 {@link OperationLog} 的方法，通过 AOP 自动采集操作模块、类型、请求 URL、
 * IP、方法名、请求参数、耗时、执行状态与响应结果等信息，并持久化到数据库操作日志表，
 * 实现对关键业务操作的审计追踪，避免在业务代码中侵入式地埋点记录日志。
 *
 * @author hbsz
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    private final SysOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;
    private final IpUtils ipUtils;

    public OperationLogAspect(SysOperationLogMapper operationLogMapper, ObjectMapper objectMapper, IpUtils ipUtils) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
        this.ipUtils = ipUtils;
    }

    /**
     * 环绕通知：执行业务方法并记录操作日志。
     * <p>
     * 方法前构造日志实体并填充请求/用户信息；执行成功置状态为 1（可选记录响应结果），
     * 抛异常置状态为 0 并记录错误信息后原样抛出；finally 中计算耗时，
     * 根据 recordOnError 决定异常场景是否入库，最终写入数据库并打印一条汇总 INFO 日志。
     *
     * @param joinPoint    连接点
     * @param operationLog 方法上的操作日志注解（通过注解参数绑定注入）
     * @return 目标业务方法的返回值
     * @throws Throwable 业务方法抛出的异常
     */
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        SysOperationLog logEntity = new SysOperationLog();
        logEntity.setModule(operationLog.module());
        logEntity.setBusinessType(operationLog.type());
        logEntity.setCreatedAt(LocalDateTime.now());

        // 填充请求信息
        fillRequestInfo(logEntity, joinPoint, operationLog);

        // 填充用户信息
        logEntity.setUserId(UserContext.getUserId());
        logEntity.setUsername(UserContext.getUsername());

        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            logEntity.setStatus(1);
            // 记录响应结果（可选）
            if (operationLog.recordResult() && result != null) {
                try {
                    logEntity.setResponseResult(truncate(objectMapper.writeValueAsString(result), 2000));
                } catch (Exception e) {
                    logEntity.setResponseResult("序列化响应失败: " + e.getMessage());
                }
            }
            return result;
        } catch (Throwable throwable) {
            error = throwable;
            logEntity.setStatus(0);
            logEntity.setErrorMsg(truncate(throwable.getMessage(), 500));
            throw throwable;
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            logEntity.setCostTime(costTime);
            // 异常时是否记录
            if (error == null || operationLog.recordOnError()) {
                try {
                    operationLogMapper.insert(logEntity);
                } catch (Exception e) {
                    log.error("[操作日志] 日志记录失败: module={}, type={}", operationLog.module(), operationLog.type(), e);
                }
            }
            log.info("[操作日志] module={}, type={}, user={}, status={}, cost={}ms",
                    operationLog.module(), operationLog.type(),
                    logEntity.getUsername(), logEntity.getStatus(), costTime);
        }
    }

    /**
     * 填充请求相关信息（请求 URL、客户端 IP、方法名、请求参数）到日志实体。
     *
     * @param logEntity    日志实体
     * @param joinPoint    连接点
     * @param operationLog 操作日志注解
     */
    private void fillRequestInfo(SysOperationLog logEntity, ProceedingJoinPoint joinPoint, OperationLog operationLog) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            logEntity.setRequestUrl(request.getRequestURI());
            logEntity.setIp(ipUtils.getClientIp(request));
        }

        // 方法名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        logEntity.setMethod(signature.getDeclaringType().getSimpleName() + "." + signature.getName());

        // 请求参数
        if (operationLog.recordParams()) {
            try {
                String params = serializeArgs(joinPoint.getArgs(), signature.getParameterNames());
                logEntity.setRequestParams(truncate(params, 2000));
            } catch (Exception e) {
                logEntity.setRequestParams("参数序列化失败: " + e.getMessage());
            }
        }
    }

    /**
     * 将方法参数序列化为 JSON 字符串，并跳过不可序列化的对象。
     * <p>
     * 对 {@link MultipartFile}、{@link HttpServletRequest}、{@link HttpServletResponse}
     * 等对象仅记录类型占位符，避免序列化异常或产生超大日志。
     *
     * @param args       方法实参数组
     * @param paramNames 形参名数组，可能为 null
     * @return 参数的 JSON 字符串，无参数时返回 "{}"
     */
    private String serializeArgs(Object[] args, String[] paramNames) {
        if (args == null || args.length == 0) {
            return "{}";
        }
        Map<String, Object> paramMap = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            String name = (paramNames != null && i < paramNames.length) ? paramNames[i] : "arg" + i;
            // 跳过不可序列化的对象
            if (arg instanceof MultipartFile || arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse) {
                paramMap.put(name, "[" + arg.getClass().getSimpleName() + "]");
            } else {
                paramMap.put(name, arg);
            }
        }
        try {
            return objectMapper.writeValueAsString(paramMap);
        } catch (Exception e) {
            return paramMap.toString();
        }
    }

    /**
     * 截断字符串到指定长度，超长时追加 "...(truncated)" 标记。
     * <p>
     * 用于限制请求参数、响应结果、错误信息等入库字段的长度，避免超出列容量。
     * 注意：返回字符串总长度（含后缀）不会超过 maxLength。
     *
     * @param str       原始字符串
     * @param maxLength 最大保留长度（含后缀）
     * @return 截断后的字符串；入参为 null 时返回 null
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        String suffix = "...(truncated)";
        return str.substring(0, maxLength - suffix.length()) + suffix;
    }

}
