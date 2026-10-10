package com.example.hbsz_idc_system_end.aspect;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.entity.SysPosition;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.service.IPositionService;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 权限校验切面
 * 拦截标注了 @RequiresPermission 的方法，执行权限校验
 * <p>
 * 校验流程：
 * <ol>
 *   <li>功能权限校验：基于 {@link IPermissionService} 校验用户拥有的权限标识；</li>
 *   <li>岗位职级校验（可选）：当注解 {@link RequiresPermission#requirePosition()} 非空时，
 *       额外校验当前用户绑定的岗位编码是否在允许列表中，不在则抛 FORBIDDEN 异常。</li>
 * </ol>
 * <p>
 * 日志策略（已优化，避免生产环境日志膨胀）：
 * - DEBUG：记录每次拦截的方法、所需权限、当前用户、校验通过、业务耗时
 * - INFO ：业务方法异常（非权限拒绝）保留，便于排查业务问题
 * - WARN ：权限拒绝事件
 * - ERROR：切面执行异常
 * <p>
 * 优化前每个权限校验通过会打印 2 条 INFO 日志，高 QPS 系统会产生海量日志，
 * 优化后常规通过路径改为 DEBUG，仅保留异常和拒绝日志，预计可减少 50%+ 生产日志量。
 *
 * @author hbsz
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    private final IPermissionService permissionService;
    private final SysUserMapper sysUserMapper;
    private final IPositionService positionService;

    public PermissionAspect(IPermissionService permissionService,
                             SysUserMapper sysUserMapper,
                             IPositionService positionService) {
        this.permissionService = permissionService;
        this.sysUserMapper = sysUserMapper;
        this.positionService = positionService;
    }

    /**
     * 环绕通知：拦截标注了 {@link RequiresPermission} 的方法，先鉴权后执行业务。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>反射读取方法上的注解，取出所需权限标识数组与逻辑关系（AND/OR）；</li>
     *   <li>从 {@link UserContext} 获取当前用户，调用
     *       {@code permissionService.checkPermissions} 进行权限校验；</li>
     *   <li>校验不通过时抛出异常（WARN 记录拒绝事件），不再执行业务方法；</li>
     *   <li>校验通过后执行目标业务方法并返回其结果。</li>
     * </ol>
     *
     * @param joinPoint 连接点，封装被拦截的目标方法与参数
     * @return 目标业务方法的返回值
     * @throws Throwable 权限校验失败或业务方法执行过程中抛出的异常
     */
    @Around("@annotation(com.example.hbsz_idc_system_end.common.annotation.RequiresPermission)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);

        // 拦截到带 @RequiresPermission 的方法
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = method.getName();
        Long userId = UserContext.getUserId();
        String username = UserContext.getUsername();

        if (annotation != null) {
            String[] requiredPerms = annotation.value();
            boolean logicalAnd = annotation.logicalAnd();

            // 优化：常规拦截信息降为 DEBUG，避免每个请求都打印 INFO 日志
            log.debug("[权限切面] 拦截到权限校验请求: user={}({}), method={}.{}, required={}, logicalAnd={}",
                    username, userId, className, methodName,
                    Arrays.toString(requiredPerms), logicalAnd);
            // 安全：对方法参数做脱敏处理后再打印，避免 DTO 的 toString 泄露
            // password/phone/token 等敏感字段（如 UserSaveDTO、ResetPasswordDTO 等使用 @Data）
            log.debug("[权限切面] 方法参数（脱敏后）: {}", sanitizeArgs(joinPoint.getArgs()));

            long startMs = System.currentTimeMillis();
            try {
                // 执行功能权限校验
                permissionService.checkPermissions(requiredPerms, logicalAnd);
                long costMs = System.currentTimeMillis() - startMs;
                // 优化：权限校验通过的常规路径降为 DEBUG，避免日志膨胀
                log.debug("[权限切面] 权限校验通过: user={}({}), method={}.{} (耗时 {}ms)",
                        username, userId, className, methodName, costMs);
            } catch (Exception e) {
                long costMs = System.currentTimeMillis() - startMs;
                // 权限拒绝为重要事件，保留 WARN 级别
                log.warn("[权限切面] 权限校验拒绝: user={}({}), method={}.{}, required={}, reason={}, 耗时={}ms",
                        username, userId, className, methodName,
                        Arrays.toString(requiredPerms), e.getMessage(), costMs);
                // 拒绝后直接抛出，不执行业务方法
                throw e;
            }

            // 岗位职级校验（可选）：当注解声明了 requirePosition 时，额外校验用户岗位
            String[] requiredPositions = annotation.requirePosition();
            if (requiredPositions != null && requiredPositions.length > 0) {
                checkPosition(userId, username, className, methodName, requiredPositions);
            }
        }

        // 执行业务方法
        long bizStartMs = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long bizCostMs = System.currentTimeMillis() - bizStartMs;
            log.debug("[权限切面] 业务方法执行完成: method={}.{} (耗时 {}ms)", className, methodName, bizCostMs);
            return result;
        } catch (Exception e) {
            long bizCostMs = System.currentTimeMillis() - bizStartMs;
            // 业务方法异常保留 ERROR 级别，便于排查
            log.error("[权限切面] 业务方法执行异常: method={}.{} (耗时 {}ms), 异常: {}",
                    className, methodName, bizCostMs, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 岗位职级校验（可选）。
     * <p>
     * 校验流程：
     * <ol>
     *   <li>从 {@link UserContext} 获取当前用户 ID；</li>
     *   <li>超级管理员（role_code=admin）直接放行，跳过岗位校验；</li>
     *   <li>查询用户绑定的 position_id，再查询对应岗位编码；</li>
     *   <li>若岗位编码不在 {@code requiredPositions} 列表中，抛出 FORBIDDEN 异常。</li>
     * </ol>
     *
     * @param userId            当前用户ID
     * @param username         当前用户名
     * @param className        拦截到的类名
     * @param methodName       拦截到的方法名
     * @param requiredPositions 允许的岗位编码列表
     */
    private void checkPosition(Long userId, String username, String className,
                                String methodName, String[] requiredPositions) {
        if (userId == null) {
            log.warn("[权限切面] 岗位校验失败: UserContext 中 userId 为空, method={}.{}", className, methodName);
            throw new BusinessException(ResultCode.FORBIDDEN, "岗位职级不足，无权操作");
        }
        // 超级管理员跳过岗位校验
        Set<String> roles = permissionService.getRolesByUserId(userId);
        if (roles != null && roles.contains(CommonConstant.ROLE_CODE_ADMIN)) {
            log.debug("[权限切面] 岗位校验跳过(超级管理员): user={}({}), method={}.{}",
                    username, userId, className, methodName);
            return;
        }
        // 查询用户绑定的岗位
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getPositionId() == null) {
            log.warn("[权限切面] 岗位校验拒绝: 用户未绑定岗位, user={}({}), method={}.{}, required={}",
                    username, userId, className, methodName, Arrays.toString(requiredPositions));
            throw new BusinessException(ResultCode.FORBIDDEN, "岗位职级不足，无权操作");
        }
        SysPosition position = positionService.getPositionById(user.getPositionId());
        if (position == null) {
            log.warn("[权限切面] 岗位校验拒绝: 用户绑定的岗位不存在, user={}({}), positionId={}, method={}.{}",
                    username, userId, user.getPositionId(), className, methodName);
            throw new BusinessException(ResultCode.FORBIDDEN, "岗位职级不足，无权操作");
        }
        List<String> allowedPositions = Arrays.asList(requiredPositions);
        if (!allowedPositions.contains(position.getCode())) {
            log.warn("[权限切面] 岗位校验拒绝: user={}({}), position={}, required={}, method={}.{}",
                    username, userId, position.getCode(), Arrays.toString(requiredPositions),
                    className, methodName);
            throw new BusinessException(ResultCode.FORBIDDEN, "岗位职级不足，无权操作");
        }
        log.debug("[权限切面] 岗位校验通过: user={}({}), position={}, method={}.{}",
                username, userId, position.getCode(), className, methodName);
    }

    /**
     * 对方法参数做脱敏处理后返回字符串表示。
     * <p>
     * 脱敏策略：
     * <ul>
     *   <li>null：直接返回 "null"；</li>
     *   <li>HttpServletRequest/HttpServletResponse/MultipartFile 等 Web 容器对象：
     *       只返回类型简单名，避免打印 Servlet 内部状态；</li>
     *   <li>String/Number/Boolean/Character/枚举等简单类型：直接返回值，
     *       便于调试（这些类型通常不含敏感字段）；</li>
     *   <li>其他复杂对象（DTO/Entity 等）：只返回类型简单名，<b>不调用 toString()</b>，
     *       因为 Lombok @Data 生成的 toString 会包含 password、phone、token 等敏感字段，
     *       在 DEBUG 级别开启时存在敏感信息泄露风险。</li>
     * </ul>
     *
     * @param args 方法原始参数数组
     * @return 脱敏后的参数字符串表示
     */
    private String sanitizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Object arg = args[i];
            sb.append(sanitizeArg(arg));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 对单个参数做脱敏处理。
     *
     * @param arg 原始参数
     * @return 脱敏后的字符串表示
     */
    private String sanitizeArg(Object arg) {
        if (arg == null) {
            return "null";
        }
        Class<?> clazz = arg.getClass();
        // Web 容器/二进制对象，只打印类型名
        if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                || arg instanceof MultipartFile) {
            return "<" + clazz.getSimpleName() + ">";
        }
        // 简单类型/包装类/枚举，直接打印值（这些类型不含敏感字段）
        if (arg instanceof CharSequence || arg instanceof Number
                || arg instanceof Boolean || arg instanceof Character
                || arg instanceof java.util.Date || arg instanceof Enum) {
            return String.valueOf(arg);
        }
        // 复杂对象（DTO/Entity 等）：只打印类型简单名，不调用 toString，
        // 避免 @Data 生成的 toString 泄露 password/phone/token 等敏感字段
        return "<" + clazz.getSimpleName() + ">";
    }

}
