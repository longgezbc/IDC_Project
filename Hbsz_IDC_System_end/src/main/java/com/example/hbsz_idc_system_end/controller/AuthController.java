package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.annotation.OperationLog;
import com.example.hbsz_idc_system_end.common.annotation.RateLimit;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.LoginDTO;
import com.example.hbsz_idc_system_end.dto.LoginVO;
import com.example.hbsz_idc_system_end.dto.RefreshTokenDTO;
import com.example.hbsz_idc_system_end.dto.UserInfoVO;
import com.example.hbsz_idc_system_end.service.IAuthService;
import com.example.hbsz_idc_system_end.utils.IpUtils;
import com.example.hbsz_idc_system_end.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器。
 * <p>
 * 业务模块：用户认证与会话管理。
 * 基础请求路径：{@code /auth}。
 * 负责用户登录、登出、当前用户信息获取及 JWT Token 刷新等接口。
 *
 * @author hbsz
 */
@Tag(name = "认证管理", description = "用户登录、登出、Token管理")
@Validated
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final IAuthService authService;
    private final IpUtils ipUtils;

    public AuthController(IAuthService authService, IpUtils ipUtils) {
        this.authService = authService;
        this.ipUtils = ipUtils;
    }

    /**
     * 用户登录。
     * <p>HTTP：POST /auth/login。校验用户名密码后签发 JWT 访问令牌。
     * 通过 {@code @RateLimit} 限流（60 秒内最多 5 次），并记录操作日志（不记录参数，避免密码泄露）。
     *
     * @param dto     登录请求参数（用户名、密码、验证码等）
     * @param request HTTP 请求对象，用于获取客户端 IP、UA 等登录上下文
     * @return 登录结果，包含访问 Token、刷新 Token 及用户基础信息
     */
    @Operation(summary = "用户登录", description = "通过用户名密码登录，返回JWT Token")
    @OperationLog(module = "认证管理", type = "登录", description = "用户登录", recordParams = false)
    @RateLimit(key = "login", period = 60, count = 5)
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        // IP 属于 Web 传输细节，在 Controller 层解析后传入，Service 不感知 Servlet API
        LoginVO vo = authService.login(dto, ipUtils.getClientIp(request));
        return Result.success("登录成功", vo);
    }

    /**
     * 用户登出。
     * <p>HTTP：POST /auth/logout。从请求头解析 Bearer Token，使其失效（加入黑名单）。
     *
     * @param request HTTP 请求对象，用于提取 Authorization 头中的 Token
     * @return 登出结果
     */
    @Operation(summary = "用户登出", description = "退出登录")
    @OperationLog(module = "认证管理", type = "登出", description = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader(CommonConstant.AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(CommonConstant.TOKEN_PREFIX)) {
            String token = authHeader.substring(CommonConstant.TOKEN_PREFIX.length());
            authService.logout(token);
        }
        return Result.success("登出成功", null);
    }

    /**
     * 获取当前用户信息。
     * <p>HTTP：GET /auth/info。基于当前登录上下文的用户 ID 查询详细信息。
     *
     * @return 当前登录用户的详细信息
     */
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    @GetMapping("/info")
    public Result<UserInfoVO> info() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        UserInfoVO vo = authService.getCurrentUserInfo(userId);
        return Result.success(vo);
    }

    /**
     * 刷新 Token。
     * <p>HTTP：POST /auth/refresh。使用刷新令牌换取新的访问令牌，refreshToken 为空时返回参数错误。
     *
     * @param dto 请求体 DTO，需包含 {@code refreshToken} 字段
     * @return 刷新后的登录结果（含新的 Token）
     */
    @Operation(summary = "刷新Token", description = "使用refreshToken获取新的访问Token")
    // M2修复：refresh 为公开接口，缺少限流会被恶意刷取消耗服务端资源，添加限流（60秒内最多10次）
    @RateLimit(key = "refresh", period = 60, count = 10)
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        // M2修复：将入参由 Map 改为带 @Valid 的 DTO，通过 @NotBlank 校验 refreshToken 非空
        String refreshToken = dto.getRefreshToken();
        LoginVO vo = authService.refresh(refreshToken);
        return Result.success("刷新成功", vo);
    }

}
