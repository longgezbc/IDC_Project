package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.ChangePasswordDTO;
import com.example.hbsz_idc_system_end.dto.NotificationSettingDTO;
import com.example.hbsz_idc_system_end.dto.UserInfoVO;
import com.example.hbsz_idc_system_end.dto.UserProfileDTO;
import com.example.hbsz_idc_system_end.entity.SysNotificationConfig;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.SysNotificationConfigMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.service.IUserService;
import com.example.hbsz_idc_system_end.utils.PasswordUtils;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 用户服务实现（用户自助视角）
 * <p>
 * 面向登录用户本人，提供个人信息查询、资料更新、密码修改及通知设置管理。
 * <p>
 * 关键设计要点：
 * <ul>
 *   <li>用户身份统一从 {@link UserContext} 获取，避免越权操作他人数据；</li>
 *   <li>获取用户信息时经 {@link IPermissionService} 拉取角色与权限（带 Redis 缓存）；</li>
 *   <li>资料更新、改密、通知设置更新均标注 {@code @Transactional}；</li>
 *   <li>通知配置采用懒创建策略：不存在时按默认值初始化。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class UserServiceImpl implements IUserService {

    private final SysUserMapper sysUserMapper;
    private final SysNotificationConfigMapper notificationConfigMapper;
    private final PasswordUtils passwordUtils;
    private final IPermissionService permissionService;

    public UserServiceImpl(SysUserMapper sysUserMapper, SysNotificationConfigMapper notificationConfigMapper,
                           PasswordUtils passwordUtils, IPermissionService permissionService) {
        this.sysUserMapper = sysUserMapper;
        this.notificationConfigMapper = notificationConfigMapper;
        this.passwordUtils = passwordUtils;
        this.permissionService = permissionService;
    }

    /**
     * 获取当前用户信息
     */
    @Override
    public UserInfoVO getCurrentUserInfo() {
        Long userId = UserContext.getUserId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
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

        // 通过 PermissionService 获取角色和权限（带 Redis 缓存）
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(userId);
        vo.setRoles(roles != null ? roles : Collections.emptyList());
        // 使用权限服务获取权限标识列表（支持缓存）
        vo.setPermissions(new java.util.ArrayList<>(permissionService.getCurrentUserPermissions()));

        return vo;
    }

    /**
     * 更新个人资料
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO updateProfile(UserProfileDTO dto) {
        Long userId = UserContext.getUserId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }

        if (StringUtils.hasText(dto.getName())) user.setName(dto.getName());
        if (dto.getNickname() != null) user.setNickname(dto.getNickname());
        if (dto.getGender() != null) user.setGender(dto.getGender());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getBio() != null) user.setBio(dto.getBio());

        sysUserMapper.updateById(user);
        log.info("更新个人资料成功: {}", userId);
        return getCurrentUserInfo();
    }

    /**
     * 修改密码
     * <p>
     * 先通过 {@link PasswordUtils#matches(String, String)} 校验原密码，
     * 不匹配则抛出 {@code OLD_PASSWORD_ERROR}；校验通过后将新密码加密后落库。
     * 事务内执行。
     *
     * @param dto 改密参数（原密码、新密码）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordDTO dto) {
        Long userId = UserContext.getUserId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }

        if (!passwordUtils.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.OLD_PASSWORD_ERROR, "原密码错误");
        }

        user.setPassword(passwordUtils.encode(dto.getNewPassword()));
        sysUserMapper.updateById(user);
        log.info("修改密码成功: {}", userId);
    }

    /**
     * 获取通知设置
     */
    @Override
    public NotificationSettingDTO getNotificationSettings() {
        Long userId = UserContext.getUserId();
        SysNotificationConfig config = getOrCreateConfig(userId);

        NotificationSettingDTO dto = new NotificationSettingDTO();
        dto.setInApp(toBool(config.getInAppNotify()));
        dto.setEmail(toBool(config.getEmailNotify()));
        dto.setSms(toBool(config.getSmsNotify()));
        dto.setProgress(toBool(config.getProgressNotify()));
        dto.setTaskDue(toBool(config.getTaskDueNotify()));
        dto.setDocUpdate(toBool(config.getDocUpdateNotify()));
        dto.setArchive(toBool(config.getArchiveNotify()));
        return dto;
    }

    /**
     * 更新通知设置
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationSettingDTO updateNotificationSettings(NotificationSettingDTO dto) {
        Long userId = UserContext.getUserId();
        SysNotificationConfig config = getOrCreateConfig(userId);

        if (dto.getInApp() != null) config.setInAppNotify(toInt(dto.getInApp()));
        if (dto.getEmail() != null) config.setEmailNotify(toInt(dto.getEmail()));
        if (dto.getSms() != null) config.setSmsNotify(toInt(dto.getSms()));
        if (dto.getProgress() != null) config.setProgressNotify(toInt(dto.getProgress()));
        if (dto.getTaskDue() != null) config.setTaskDueNotify(toInt(dto.getTaskDue()));
        if (dto.getDocUpdate() != null) config.setDocUpdateNotify(toInt(dto.getDocUpdate()));
        if (dto.getArchive() != null) config.setArchiveNotify(toInt(dto.getArchive()));

        notificationConfigMapper.updateById(config);
        log.info("更新通知设置成功: {}", userId);
        return getNotificationSettings();
    }

    /**
     * 获取或创建通知配置
     * <p>
     * 懒创建策略：按用户ID查询通知配置，若不存在则以默认值
     * （站内信/邮件/进度/任务到期/归档提醒开启，短信/文档更新提醒关闭）初始化并落库，
     * 保证调用方总能拿到可用的配置对象。
     *
     * @param userId 用户ID
     * @return 该用户的通知配置（不存在时为新建的默认配置）
     */
    @Transactional(rollbackFor = Exception.class)
    private SysNotificationConfig getOrCreateConfig(Long userId) {
        LambdaQueryWrapper<SysNotificationConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotificationConfig::getUserId, userId);
        SysNotificationConfig config = notificationConfigMapper.selectOne(wrapper);
        if (config == null) {
            config = new SysNotificationConfig();
            config.setUserId(userId);
            config.setInAppNotify(1);
            config.setEmailNotify(1);
            config.setSmsNotify(0);
            config.setProgressNotify(1);
            config.setTaskDueNotify(1);
            config.setDocUpdateNotify(0);
            config.setArchiveNotify(1);
            // 【修复M12】原 select-then-insert 无并发保护，高并发下可能重复插入。
            // 捕获 DuplicateKeyException（唯一键冲突）后重新查询，保证最终拿到已存在的配置。
            try {
                notificationConfigMapper.insert(config);
            } catch (DuplicateKeyException e) {
                log.warn("[用户服务] 通知配置并发插入冲突，重新查询: userId={}", userId);
                config = notificationConfigMapper.selectOne(wrapper);
            }
        }
        return config;
    }

    private Boolean toBool(Integer value) {
        return value != null && value == 1;
    }

    private Integer toInt(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

}
