package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.LoginDTO;
import com.example.hbsz_idc_system_end.dto.LoginVO;
import com.example.hbsz_idc_system_end.dto.UserInfoVO;

/**
 * 认证服务接口
 *
 * @author hbsz
 */
public interface IAuthService {

    /**
     * 用户登录
     *
     * @param dto      登录请求参数
     * @param clientIp 客户端 IP（由 Web 层解析后传入）
     * @return 登录响应（含 token、refreshToken、用户信息）
     */
    LoginVO login(LoginDTO dto, String clientIp);

    /**
     * 用户登出
     * <p>
     * 将 Token 加入 Redis 黑名单使其立即失效，并清除该用户的权限缓存。
     *
     * @param token 待登出的 JWT
     */
    void logout(String token);

    /**
     * 获取当前用户信息
     *
     * @param userId 用户ID
     * @return 用户信息 VO（含角色、权限标识列表）
     */
    UserInfoVO getCurrentUserInfo(Long userId);

    /**
     * 刷新 Token
     *
     * @param refreshToken 刷新令牌
     * @return 新的登录响应（含新的 access token）
     */
    LoginVO refresh(String refreshToken);

    /**
     * 检查 Token 是否在黑名单中
     *
     * @param token JWT
     * @return true 表示在黑名单中（已登出），false 表示有效
     */
    boolean isTokenBlacklisted(String token);

}
