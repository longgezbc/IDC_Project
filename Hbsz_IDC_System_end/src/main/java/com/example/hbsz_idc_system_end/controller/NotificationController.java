package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.NotificationQueryDTO;
import com.example.hbsz_idc_system_end.dto.NotificationVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.service.INotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 站内通知控制器。
 * <p>
 * 业务模块：当前登录用户的站内通知管理。
 * 基础请求路径：{@code /notifications}。
 * 面向登录用户自身，提供通知列表查询、未读数统计、标记已读与删除等接口。
 * 不需要额外权限码——只要登录即可访问自己的通知。
 * </p>
 *
 * @author hbsz
 */
@Tag(name = "站内通知", description = "站内通知列表、未读数、标记已读、删除等接口")
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final INotificationService notificationService;

    public NotificationController(INotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 通知列表查询（分页）。
     * <p>HTTP：GET /notifications。支持按 notifyType 与 isRead 筛选。
     *
     * @param query 查询条件
     * @return 分页通知列表
     */
    @Operation(summary = "通知列表", description = "分页查询当前用户的通知列表，支持类型与已读状态筛选")
    @GetMapping
    public Result<PageResult<NotificationVO>> list(NotificationQueryDTO query) {
        return Result.success(notificationService.queryPage(query));
    }

    /**
     * 获取当前用户未读通知数。
     * <p>HTTP：GET /notifications/unread-count。
     *
     * @return 未读数量
     */
    @Operation(summary = "未读通知数", description = "获取当前用户的未读通知数量")
    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        return Result.success(notificationService.getUnreadCount());
    }

    /**
     * 标记单条通知为已读。
     * <p>HTTP：PUT /notifications/{id}/read。
     *
     * @param id 通知ID
     * @return 操作结果
     */
    @Operation(summary = "标记已读", description = "将指定通知标记为已读")
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@Parameter(description = "通知ID", required = true) @PathVariable Long id) {
        notificationService.markAsRead(id);
        return Result.success("已标记为已读", null);
    }

    /**
     * 将当前用户全部未读通知标记为已读。
     * <p>HTTP：PUT /notifications/read-all。
     *
     * @return 操作结果
     */
    @Operation(summary = "全部标记已读", description = "将当前用户的所有未读通知标记为已读")
    @PutMapping("/read-all")
    public Result<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return Result.success("已全部标记为已读", null);
    }

    /**
     * 删除单条通知。
     * <p>HTTP：DELETE /notifications/{id}。
     *
     * @param id 通知ID
     * @return 操作结果
     */
    @Operation(summary = "删除通知", description = "删除指定通知（仅限本人通知）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "通知ID", required = true) @PathVariable Long id) {
        notificationService.delete(id);
        return Result.success("已删除", null);
    }

}
