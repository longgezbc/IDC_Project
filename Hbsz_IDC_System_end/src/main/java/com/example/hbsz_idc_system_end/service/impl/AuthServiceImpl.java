package com.example.hbsz_idc_system_end.service.impl;

import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.constant.JwtConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.LoginDTO;
import com.example.hbsz_idc_system_end.dto.LoginVO;
import com.example.hbsz_idc_system_end.dto.UserInfoVO;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.IAuthService;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.utils.JwtUtils;
import com.example.hbsz_idc_system_end.utils.PasswordUtils;
import com.example.hbsz_idc_system_end.utils.RedisUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 * <p>
 * 承担系统身份认证的核心逻辑：
 * <ul>
 *   <li>登录：密码校验 + 基于 Redis 的登录失败计数与账号锁定，防止暴力破解；</li>
 *   <li>令牌：签发 access/refresh JWT，登出时写入 Redis 黑名单实现无状态令牌的即时失效；</li>
 *   <li>缓存：登录/登出时联动 {@link IPermissionService} 加载或清理用户权限缓存，保证权限变更即时生效。</li>
 * </ul>
 * 说明：JWT 为无状态令牌，服务端不保存会话，注销依赖黑名单 + 令牌剩余 TTL 精确失效。
 *
 * @author hbsz
 */
@Slf4j
@Service
public class AuthServiceImpl implements IAuthService {

    private final SysUserMapper sysUserMapper;
    private final JwtUtils jwtUtils;
    private final PasswordUtils passwordUtils;
    private final IPermissionService permissionService;
    private final RedisUtils redisUtils;

    public AuthServiceImpl(SysUserMapper sysUserMapper, JwtUtils jwtUtils, PasswordUtils passwordUtils,
                           IPermissionService permissionService, RedisUtils redisUtils) {
        this.sysUserMapper = sysUserMapper;
        this.jwtUtils = jwtUtils;
        this.passwordUtils = passwordUtils;
        this.permissionService = permissionService;
        this.redisUtils = redisUtils;
    }

    /**
     * 用户登录
     * <p>
     * 核心流程：
     * <ol>
     *   <li>校验图形验证码（一次性使用，无论成功失败均删除，防止重放）；</li>
     *   <li>查 Redis 锁定标记，账号被锁定则直接拒绝（防暴力破解）；</li>
     *   <li>校验用户名与密码，任一失败即累加失败计数，达阈值锁定账号；</li>
     *   <li>校验账号是否被禁用；</li>
     *   <li>成功后清除失败计数、更新最后登录时间与客户端 IP；</li>
     *   <li>签发 access/refresh Token，加载角色与权限并组装返回。</li>
     * </ol>
     * 安全提示：
     * <ul>
     *   <li>验证码置于首位：未通过验证码的请求不会消耗数据库查询与密码 BCrypt 计算资源；</li>
     *   <li>验证码失败不累加登录失败计数，避免攻击者通过提交错误验证码触发账号锁定；</li>
     *   <li>完整权限集合含敏感信息，仅以 DEBUG 级别输出，INFO 只记录权限数量。</li>
     * </ul>
     */
    @Override
    public LoginVO login(LoginDTO dto, String clientIp) {
        log.info("[认证服务] 用户登录开始: username={}", dto.getUsername());

        // P0-4.1：图形验证码校验（置于首位，未通过则不消耗后续资源）
        // 一次性使用：无论校验成功或失败均从 Redis 删除，防止同一 captchaId 被重放
        validateCaptcha(dto.getCaptchaId(), dto.getCaptcha());

        // 检查账号是否被锁定
        String lockKey = CommonConstant.REDIS_LOGIN_LOCK_PREFIX + dto.getUsername();
        if (redisUtils.hasKey(lockKey)) {
            long remainSeconds = redisUtils.getExpire(lockKey);
            log.warn("[认证服务] 登录拒绝，账号已锁定: username={}, 剩余锁定时间={}s", dto.getUsername(), remainSeconds);
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED);
        }

        SysUser user = sysUserMapper.selectByUsername(dto.getUsername());
        if (user == null) {
            log.warn("[认证服务] 登录失败，用户不存在: {}", dto.getUsername());
            recordLoginFailure(dto.getUsername());
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (!passwordUtils.matches(dto.getPassword(), user.getPassword())) {
            log.warn("[认证服务] 登录失败，密码错误: {}", dto.getUsername());
            recordLoginFailure(dto.getUsername());
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            log.warn("[认证服务] 登录失败，账号已禁用: {}", dto.getUsername());
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 登录成功，清除失败计数
        clearLoginFailure(dto.getUsername());

        // 更新最后登录信息
        sysUserMapper.updateLastLoginInfo(user.getId(), LocalDateTime.now(), clientIp);

        // 生成Token
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getName());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getUsername());

        // 查询角色（走缓存，避免后续 getPermissionsByUserId 内部 getRolesByUserId 重复查 DB）
        java.util.Set<String> roleSet = permissionService.getRolesByUserId(user.getId());
        List<String> roles = roleSet.isEmpty()
                ? Collections.singletonList("user")
                : new java.util.ArrayList<>(roleSet);
        log.info("[认证服务] 用户角色加载: userId={}, username={}, roles={}", user.getId(), user.getUsername(), roles);

        // 组装响应
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setRefreshToken(refreshToken);
        vo.setExpiresIn((int) jwtUtils.getExpirationSeconds());

        LoginVO.UserInfoVO userInfo = new LoginVO.UserInfoVO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setName(user.getName());
        userInfo.setAvatar(user.getAvatar());
        userInfo.setRoles(roles);
        // 优化：直接调用 getPermissionsByUserId 重载方法，不再临时设置 ThreadLocal
        // 原实现 setUserId/setUsername + try/finally clear 模式存在两个风险：
        // 1. 登录流程未经过 JwtAuthInterceptor，ThreadLocal 本不应被使用
        // 2. 若 clear 异常或被遗漏，会导致 ThreadLocal 数据残留（内存泄漏 + 用户身份错乱）
        java.util.List<String> perms = new java.util.ArrayList<>(permissionService.getPermissionsByUserId(user.getId()));
        userInfo.setPermissions(perms);
        // 优化：完整 perms 集合含敏感信息，降为 DEBUG；INFO 仅记录数量
        log.info("[认证服务] 用户权限加载完成: userId={}, username={}, permsCount={}",
                user.getId(), user.getUsername(), perms.size());
        log.debug("[认证服务] 用户权限详情: userId={}, perms={}", user.getId(), perms);
        vo.setUserInfo(userInfo);

        log.info("[认证服务] 用户登录成功: userId={}, username={}, ip={}", user.getId(), user.getUsername(), clientIp);
        return vo;
    }

    /**
     * 用户登出
     * <p>
     * 优化说明：原实现先调 getUsernameFromToken、getUserIdFromToken，又调 parseToken，
     * 同一 token 被解析 3 次。改为只 parseToken 一次后，从 Claims 中直接取 userId / username，
     * 减少不必要的 JWT 解析开销（每次解析涉及 Base64 解码 + HMAC 签名校验）。
     * <p>
     * 安全修复：原实现仅将 access token 加入黑名单，refreshToken 在剩余有效期内（最长 7 天）
     * 仍可换取新 accessToken，登出形同虚设。现登出时同时记录用户级登出时间戳
     * （TTL = refreshToken 最大有效期），{@link #refresh} 中拒绝该时间点之前签发的 refreshToken，
     * 保证登出后完整会话终止；登出之后重新登录签发的 refreshToken 不受影响。
     */
    @Override
    public void logout(String token) {
        // JWT 无状态，登出时将Token加入Redis黑名单，使其立即失效
        // 只解析一次 token，复用 Claims 取 username / userId / expiration
        Claims claims = jwtUtils.parseToken(token);
        if (claims == null) {
            log.warn("[认证服务] 登出失败：Token 无效");
            return;
        }
        String username = claims.getSubject();
        Long userId = null;
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Integer) {
            userId = ((Integer) userIdObj).longValue();
        } else if (userIdObj instanceof Long) {
            userId = (Long) userIdObj;
        }
        log.info("[认证服务] 用户登出开始: userId={}, username={}", userId, username);
        // 将Token加入黑名单，TTL设为Token剩余有效时间
        if (claims.getExpiration() != null) {
            long remainingSeconds = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
            if (remainingSeconds > 0) {
                String blacklistKey = CommonConstant.REDIS_JWT_BLACKLIST_PREFIX + token;
                redisUtils.set(blacklistKey, "1", remainingSeconds);
                log.info("[认证服务] Token已加入黑名单: username={}, 剩余有效={}s", username, remainingSeconds);
            }
        }
        // 记录用户级登出时间戳，使登出前签发的所有 refreshToken 失效
        if (userId != null) {
            String logoutKey = CommonConstant.REDIS_USER_LOGOUT_PREFIX + userId;
            redisUtils.set(logoutKey, String.valueOf(System.currentTimeMillis()),
                    jwtUtils.getRefreshExpirationSeconds());
            permissionService.clearUserCache(userId);
        }
        log.info("[认证服务] 用户登出完成: userId={}, username={}", userId, username);
    }

    /**
     * 获取当前用户信息
     */
    @Override
    public UserInfoVO getCurrentUserInfo(Long userId) {
        log.info("[认证服务] 获取当前用户信息: userId={}", userId);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            log.warn("[认证服务] 获取用户信息失败: 用户不存在, userId={}", userId);
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }

        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getName());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setBio(user.getBio());

        // 查询角色（走缓存，避免 getPermissionsByUserId 内部重复查 DB）
        java.util.Set<String> roleSet = permissionService.getRolesByUserId(userId);
        vo.setRoles(roleSet.isEmpty() ? Collections.emptyList() : new java.util.ArrayList<>(roleSet));
        // 优化：直接调用 getPermissionsByUserId 重载方法，不再临时设置 ThreadLocal
        java.util.List<String> perms = new java.util.ArrayList<>(permissionService.getPermissionsByUserId(userId));
        vo.setPermissions(perms);
        log.info("[认证服务] 当前用户权限加载: userId={}, username={}, roles={}, permsCount={}",
                userId, user.getUsername(), vo.getRoles(), perms.size());

        return vo;
    }

    /**
     * 刷新Token
     * <p>
     * 严格校验令牌类型必须为 refresh，防止 access token 被误用于刷新；
     * 同时复核用户存在且未被禁用，最后仅签发新的 access token（refresh token 不变）。
     */
    @Override
    public LoginVO refresh(String refreshToken) {
        Claims claims = jwtUtils.parseToken(refreshToken);
        if (claims == null) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        // 校验Token类型必须为refresh
        Object typeClaim = claims.get("type");
        if (!"refresh".equals(typeClaim)) {
            log.warn("[认证服务] 刷新Token失败：Token类型不匹配, type={}", typeClaim);
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        // 修复 #11：复用已解析的 Claims，避免重复解析 token（原代码 parseToken 调用 3 次）
        // userId 和 username 直接从 Claims 提取，省去 2 次 Base64 解码 + HMAC 校验
        Object userIdRaw = claims.get(JwtConstant.CLAIM_USER_ID);
        Long userId = null;
        if (userIdRaw instanceof Integer) {
            userId = ((Integer) userIdRaw).longValue();
        } else if (userIdRaw instanceof Long) {
            userId = (Long) userIdRaw;
        }
        String username = claims.getSubject();
        if (userId == null || !StringUtils.hasText(username)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        // 修复 refreshToken 重放问题：使用 SETNX 标记 refreshToken 为已使用，实现一次性使用
        // 原实现中同一个 refreshToken 可被并发多次调用，生成多个有效 access token，存在安全风险
        // 改为：首次刷新时在 Redis 中写入"已使用"标记（TTL = refreshToken 剩余有效期），
        // 后续使用同一 refreshToken 刷新时检测到标记存在即拒绝
        String refreshUsedKey = CommonConstant.REDIS_REFRESH_TOKEN_USED_PREFIX + refreshToken;
        long remainingSeconds = 0;
        if (claims.getExpiration() != null) {
            remainingSeconds = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
        }
        if (remainingSeconds <= 0) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        boolean firstUse = redisUtils.setIfAbsent(refreshUsedKey, "1", remainingSeconds, TimeUnit.SECONDS);
        if (!firstUse) {
            log.warn("[认证服务] refreshToken 重复使用，已拒绝: userId={}, username={}", userId, username);
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        // 安全修复：校验用户级登出时间戳。登出后（登出时间点之前签发的）refreshToken 一律拒绝，
        // 防止登出仅拉黑 accessToken 后 refreshToken 仍可换取新令牌绕过登出。
        // 注意顺序：该检查在 SETNX 占位之后执行，被拒绝的请求不会消耗一次性标记语义
        //（占位 key TTL 与 refreshToken 剩余期一致，即使被占位该 token 本也已作废）。
        Object logoutTsObj = redisUtils.get(CommonConstant.REDIS_USER_LOGOUT_PREFIX + userId);
        if (logoutTsObj != null && claims.getIssuedAt() != null) {
            long logoutTs;
            try {
                logoutTs = Long.parseLong(logoutTsObj.toString().trim());
            } catch (NumberFormatException e) {
                logoutTs = 0L;
            }
            // refreshToken 签发时间不晚于登出时间 → 属于登出前的会话，拒绝刷新
            if (logoutTs > 0 && claims.getIssuedAt().getTime() <= logoutTs) {
                log.warn("[认证服务] 登出后的 refreshToken 尝试刷新，已拒绝: userId={}, username={}", userId, username);
                throw new BusinessException(ResultCode.TOKEN_INVALID);
            }
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        String newToken = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getName());

        LoginVO vo = new LoginVO();
        vo.setToken(newToken);
        vo.setExpiresIn((int) jwtUtils.getExpirationSeconds());
        return vo;
    }

    /**
     * 图形验证码校验
     * <p>
     * 校验流程：
     * <ol>
     *   <li>从 Redis 读取 captchaId 对应的答案；</li>
     *   <li>不存在（已过期或被使用）则抛 CAPTCHA_ERROR；</li>
     *   <li>与用户输入比对（trim 后字符串相等），不匹配则抛 CAPTCHA_ERROR；</li>
     *   <li>无论成功失败均删除 Redis 中的验证码，确保一次性使用，防止重放。</li>
     * </ol>
     * <p>
     * 安全考虑：
     * <ul>
     *   <li>验证码失败不累加登录失败计数，避免攻击者通过提交错误验证码触发账号锁定 DoS；</li>
     *   <li>验证码校验置于登录流程首位，未通过则不消耗后续数据库查询与 BCrypt 计算；</li>
     *   <li>用户输入与答案均来自可控来源（Redis 缓存 / 请求体），无 SQL 注入风险。</li>
     * </ul>
     *
     * @param captchaId 验证码标识（由 GET /captcha 接口返回）
     * @param captcha   用户输入的验证码答案
     */
    private void validateCaptcha(String captchaId, String captcha) {
        String captchaKey = CommonConstant.REDIS_CAPTCHA_PREFIX + captchaId;
        Object storedObj = redisUtils.get(captchaKey);
        // 无论后续比对结果如何，先删除验证码确保一次性使用
        redisUtils.delete(captchaKey);

        if (storedObj == null) {
            log.warn("[认证服务] 验证码校验失败：captchaId 不存在或已过期, captchaId={}", captchaId);
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }
        // Redis 存储时为 String，反序列化后用 toString 兜底处理（兼容数字类型的边界情况）
        String storedAnswer = storedObj.toString();
        // trim 防止用户误输入前后空格；忽略大小写比对（图片验证码字母大小写难以分辨）
        if (captcha == null || !captcha.trim().equalsIgnoreCase(storedAnswer.trim())) {
            log.warn("[认证服务] 验证码校验失败：答案不匹配, captchaId={}", captchaId);
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }
        log.debug("[认证服务] 验证码校验通过: captchaId={}", captchaId);
    }

    /**
     * 记录登录失败次数，超过阈值则锁定账号
     * <p>
     * 使用 Redis 原子自增计数，首次失败时设置计数窗口过期时间；
     * 计数达到上限即写入锁定标记并清空计数，锁定期内禁止登录。
     */
    private void recordLoginFailure(String username) {
        String failKey = CommonConstant.REDIS_LOGIN_FAIL_PREFIX + username;
        // 修复 #10：使用 Lua 脚本原子执行 INCR + EXPIRE，避免进程崩溃导致计数 key 永不过期
        // 原实现为 increment + expire 两步非原子，进程在两步之间崩溃会导致 failKey 永不过期，
        // 失败计数永久累加，正常用户也可能被锁定。改为 incrementWithExpire 后无此风险。
        long failCount = redisUtils.incrementWithExpire(failKey, CommonConstant.LOGIN_FAIL_EXPIRE);
        if (failCount >= CommonConstant.LOGIN_MAX_FAIL_COUNT) {
            // 锁定账号
            String lockKey = CommonConstant.REDIS_LOGIN_LOCK_PREFIX + username;
            redisUtils.set(lockKey, "1", CommonConstant.LOGIN_LOCK_EXPIRE);
            // 清除失败计数
            redisUtils.delete(failKey);
            log.warn("[认证服务] 账号已锁定: username={}, 失败次数={}, 锁定时长={}s",
                    username, failCount, CommonConstant.LOGIN_LOCK_EXPIRE);
        } else {
            log.warn("[认证服务] 登录失败计数: username={}, 当前失败次数={}/{}",
                    username, failCount, CommonConstant.LOGIN_MAX_FAIL_COUNT);
        }
    }

    /**
     * 登录成功时清除失败计数
     */
    private void clearLoginFailure(String username) {
        String failKey = CommonConstant.REDIS_LOGIN_FAIL_PREFIX + username;
        if (redisUtils.hasKey(failKey)) {
            redisUtils.delete(failKey);
            log.debug("[认证服务] 登录成功，清除失败计数: username={}", username);
        }
    }

    /**
     * 检查Token是否在黑名单中
     */
    @Override
    public boolean isTokenBlacklisted(String token) {
        String blacklistKey = CommonConstant.REDIS_JWT_BLACKLIST_PREFIX + token;
        return redisUtils.hasKey(blacklistKey);
    }

}
