package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.ChangePasswordDTO;
import com.example.hbsz_idc_system_end.dto.NotificationSettingDTO;
import com.example.hbsz_idc_system_end.dto.UserInfoVO;
import com.example.hbsz_idc_system_end.dto.UserProfileDTO;
import com.example.hbsz_idc_system_end.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 用户设置控制器。
 * <p>
 * 业务模块：个人中心（当前登录用户的自助设置）。
 * 基础请求路径：{@code /user}。
 * 面向登录用户自身，提供个人资料、登录密码及通知设置的查询与更新接口。
 *
 * @author hbsz
 */
@Tag(name = "用户设置", description = "个人资料、密码、通知设置等接口")
@RestController
@RequestMapping("/user")
public class UserController {

    private final IUserService userService;

    public UserController(IUserService userService) {
        this.userService = userService;
    }

    /**
     * 获取当前用户信息。
     * <p>HTTP：GET /user/profile。返回当前登录用户的详细资料。
     *
     * @return 当前登录用户信息
     */
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    @GetMapping("/profile")
    public Result<UserInfoVO> profile() {
        return Result.success(userService.getCurrentUserInfo());
    }

    /**
     * 更新个人资料。
     * <p>HTTP：PUT /user/profile。更新当前登录用户的个人资料。
     *
     * @param dto 个人资料更新数据
     * @return 更新后的用户信息
     */
    @Operation(summary = "更新个人资料", description = "更新当前用户的个人资料")
    @PutMapping("/profile")
    // 启用 DTO 参数校验，防止非法输入
    public Result<UserInfoVO> updateProfile(@Valid @RequestBody UserProfileDTO dto) {
        return Result.success("个人资料更新成功", userService.updateProfile(dto));
    }

    /**
     * 修改密码。
     * <p>HTTP：PUT /user/password。校验原密码后修改当前用户的登录密码。
     *
     * @param dto 修改密码请求参数（原密码、新密码等），经参数校验
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "修改密码", description = "修改当前用户的登录密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(dto);
        return Result.success("密码修改成功", null);
    }

    /**
     * 获取通知设置。
     * <p>HTTP：GET /user/notifications。读取当前用户的通知偏好配置。
     *
     * @return 当前用户的通知设置
     */
    @Operation(summary = "获取通知设置", description = "获取当前用户的通知配置")
    @GetMapping("/notifications")
    public Result<NotificationSettingDTO> getNotificationSettings() {
        return Result.success(userService.getNotificationSettings());
    }

    /**
     * 更新通知设置。
     * <p>HTTP：PUT /user/notifications。更新当前用户的通知偏好配置。
     *
     * @param dto 通知设置数据
     * @return 更新后的通知设置
     */
    @Operation(summary = "更新通知设置", description = "更新当前用户的通知配置")
    @PutMapping("/notifications")
    public Result<NotificationSettingDTO> updateNotificationSettings(@Valid @RequestBody NotificationSettingDTO dto) {
        return Result.success("通知设置更新成功", userService.updateNotificationSettings(dto));
    }

}
