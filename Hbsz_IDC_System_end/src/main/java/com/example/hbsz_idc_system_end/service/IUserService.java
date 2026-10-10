package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.ChangePasswordDTO;
import com.example.hbsz_idc_system_end.dto.NotificationSettingDTO;
import com.example.hbsz_idc_system_end.dto.UserInfoVO;
import com.example.hbsz_idc_system_end.dto.UserProfileDTO;

/**
 * 用户服务接口
 * <p>
 * 提供当前登录用户的个人资料、密码、通知设置等自助管理能力
 * （与 UserManageService 不同，UserManageService 是管理员视角的全量用户管理）。
 *
 * @author hbsz
 */
public interface IUserService {

    /**
     * 获取当前用户信息
     *
     * @return 当前登录用户信息 VO
     */
    UserInfoVO getCurrentUserInfo();

    /**
     * 更新个人资料
     *
     * @param dto 个人资料参数（昵称、邮箱、头像等）
     * @return 更新后的用户信息 VO
     */
    UserInfoVO updateProfile(UserProfileDTO dto);

    /**
     * 修改密码
     * <p>
     * 需校验原密码正确后再更新为新密码。
     *
     * @param dto 修改密码参数（原密码、新密码）
     */
    void changePassword(ChangePasswordDTO dto);

    /**
     * 获取通知设置
     *
     * @return 当前用户的通知设置
     */
    NotificationSettingDTO getNotificationSettings();

    /**
     * 更新通知设置
     *
     * @param dto 通知设置参数
     * @return 更新后的通知设置
     */
    NotificationSettingDTO updateNotificationSettings(NotificationSettingDTO dto);

}
