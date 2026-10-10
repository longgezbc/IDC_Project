package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.NotificationQueryDTO;
import com.example.hbsz_idc_system_end.dto.NotificationSendDTO;
import com.example.hbsz_idc_system_end.dto.NotificationVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.SysNotification;
import com.example.hbsz_idc_system_end.entity.SysNotificationConfig;
import com.example.hbsz_idc_system_end.mapper.SysNotificationConfigMapper;
import com.example.hbsz_idc_system_end.mapper.SysNotificationMapper;
import com.example.hbsz_idc_system_end.service.INotificationService;
import com.example.hbsz_idc_system_end.service.NotificationPushService;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内通知服务实现
 * <p>
 * 站内通知的核心服务，关注点：
 * <ul>
 *   <li>当前用户隔离：列表/未读数/标记已读/删除均按 {@link UserContext#getUserId()} 限定，
 *       防止越权查看他人通知；</li>
 *   <li>偏好检查：{@link #sendNotification} 在写入前查询目标用户的
 *       {@code sys_notification_config}，若用户关闭了站内通知或对应类型开关，则跳过；</li>
 *   <li>实时推送：写入数据库后通过 {@link NotificationPushService} 推送到 WebSocket，
 *       推送失败不影响主业务（用户离线时消息已落库，下次拉取可见）；</li>
 *   <li>静默投递：{@code sendNotification} 捕获所有异常，避免通知逻辑影响业务主流程。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class NotificationServiceImpl implements INotificationService {

    private final SysNotificationMapper notificationMapper;
    private final SysNotificationConfigMapper configMapper;
    private final NotificationPushService pushService;

    public NotificationServiceImpl(SysNotificationMapper notificationMapper,
                                   SysNotificationConfigMapper configMapper,
                                   NotificationPushService pushService) {
        this.notificationMapper = notificationMapper;
        this.configMapper = configMapper;
        this.pushService = pushService;
    }

    @Override
    public PageResult<NotificationVO> queryPage(NotificationQueryDTO query) {
        Long userId = currentUserId();
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getUserId, userId);
        if (StringUtils.hasText(query.getNotifyType())) {
            wrapper.eq(SysNotification::getNotifyType, query.getNotifyType());
        }
        if (query.getIsRead() != null) {
            wrapper.eq(SysNotification::getIsRead, query.getIsRead() ? 1 : 0);
        }
        wrapper.orderByDesc(SysNotification::getCreatedAt);

        Page<SysNotification> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<SysNotification> result = notificationMapper.selectPage(page, wrapper);
        List<NotificationVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return new PageResult<>(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public int getUnreadCount() {
        Long userId = currentUserId();
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, 0));
        return count != null ? count.intValue() : 0;
    }

    @Override
    public void markAsRead(Long id) {
        Long userId = currentUserId();
        SysNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "通知不存在");
        }
        // 越权校验：只能标记自己的通知
        if (!userId.equals(notification.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作他人通知");
        }
        if (notification.getIsRead() != null && notification.getIsRead() == 1) {
            return; // 已读，幂等返回
        }
        notification.setIsRead(1);
        notification.setReadTime(LocalDateTime.now());
        notificationMapper.updateById(notification);
    }

    @Override
    public void markAllAsRead() {
        Long userId = currentUserId();
        notificationMapper.markAllAsRead(userId);
        log.info("[通知服务] 批量标记已读: userId={}", userId);
    }

    @Override
    public void delete(Long id) {
        Long userId = currentUserId();
        SysNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "通知不存在");
        }
        if (!userId.equals(notification.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除他人通知");
        }
        notificationMapper.deleteById(id);
    }

    /**
     * 投递一条通知（业务模块统一调用入口）。
     * <p>
     * 此方法为静默投递：捕获所有异常，不向调用方抛出，
     * 避免通知逻辑（如 WebSocket 推送失败、配置查询异常）影响业务主流程。
     * <p>
     * 事务修复 #26：原实现使用默认 PROPAGATION_REQUIRED，会加入父事务，
     * 通知 DB 写入失败会导致父事务回滚，影响主业务流程。
     * 改为 REQUIRES_NEW 开启独立事务：通知失败仅回滚通知自身事务，
     * 配合方法内 try-catch 吞掉异常，确保主流程不受影响。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void sendNotification(NotificationSendDTO dto) {
        try {
            if (dto.getUserId() == null) {
                log.warn("[通知服务] 投递失败：userId为空, title={}", dto.getTitle());
                return;
            }
            // 检查用户通知偏好
            if (!isNotificationEnabled(dto.getUserId(), dto.getNotifyType())) {
                log.debug("[通知服务] 用户已关闭该类型通知，跳过: userId={}, type={}",
                        dto.getUserId(), dto.getNotifyType());
                return;
            }
            // 写入数据库
            SysNotification notification = new SysNotification();
            notification.setUserId(dto.getUserId());
            notification.setTitle(dto.getTitle());
            notification.setContent(dto.getContent());
            notification.setNotifyType(dto.getNotifyType());
            notification.setBizType(dto.getBizType());
            notification.setBizId(dto.getBizId());
            notification.setPriority(dto.getPriority() == null ? 1 : dto.getPriority());
            notification.setIsRead(0);
            notification.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(notification);
            log.info("[通知服务] 通知已写入: id={}, userId={}, type={}, title={}",
                    notification.getId(), dto.getUserId(), dto.getNotifyType(), dto.getTitle());
            // 实时推送（用户离线时推送会被丢弃，但消息已在数据库）
            NotificationVO vo = convertToVO(notification);
            pushService.pushToUser(dto.getUserId(), vo);
        } catch (Exception e) {
            // 通知投递失败不应影响业务主流程，仅记录错误日志
            log.error("[通知服务] 投递异常: userId={}, type={}, title={}, error={}",
                    dto.getUserId(), dto.getNotifyType(), dto.getTitle(), e.getMessage(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void sendNotifications(List<NotificationSendDTO> dtoList) {
        try {
            if (dtoList == null || dtoList.isEmpty()) {
                return;
            }
            List<SysNotification> notifications = dtoList.stream()
                    .filter(dto -> {
                        if (dto.getUserId() == null) {
                            log.warn("[通知服务] 批量投递跳过：userId为空, title={}", dto.getTitle());
                            return false;
                        }
                        if (!isNotificationEnabled(dto.getUserId(), dto.getNotifyType())) {
                            log.debug("[通知服务] 用户已关闭该类型通知，跳过: userId={}, type={}",
                                    dto.getUserId(), dto.getNotifyType());
                            return false;
                        }
                        return true;
                    })
                    .map(dto -> {
                        SysNotification n = new SysNotification();
                        n.setUserId(dto.getUserId());
                        n.setTitle(dto.getTitle());
                        n.setContent(dto.getContent());
                        n.setNotifyType(dto.getNotifyType());
                        n.setBizType(dto.getBizType());
                        n.setBizId(dto.getBizId());
                        n.setPriority(dto.getPriority() == null ? 1 : dto.getPriority());
                        n.setIsRead(0);
                        n.setCreatedAt(LocalDateTime.now());
                        return n;
                    })
                    .collect(Collectors.toList());

            if (notifications.isEmpty()) {
                return;
            }
            int inserted = notificationMapper.batchInsert(notifications);
            log.info("[通知服务] 批量通知已写入: 总数={}, 实际插入={}", dtoList.size(), inserted);
            notifications.parallelStream().forEach(n -> {
                try {
                    NotificationVO vo = convertToVO(n);
                    pushService.pushToUser(n.getUserId(), vo);
                } catch (Exception e) {
                    log.warn("[通知服务] WebSocket推送失败: userId={}, error={}", n.getUserId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("[通知服务] 批量投递异常: size={}, error={}",
                    dtoList.size(), e.getMessage(), e);
        }
    }

    /**
     * 检查目标用户是否开启了指定类型的通知。
     * <p>
     * 规则：
     * <ul>
     *   <li>若用户无配置记录（sys_notification_config），默认开启所有类型；</li>
     *   <li>inAppNotify=0 时，所有站内通知关闭，返回 false；</li>
     *   <li>system 类型不检查具体开关，只要 inAppNotify=1 即发送；</li>
     *   <li>其他类型检查对应字段：progress→progressNotify, task_due→taskDueNotify,
     *       doc_update→docUpdateNotify, archive→archiveNotify。</li>
     * </ul>
     *
     * @param userId     目标用户ID
     * @param notifyType 通知类型
     * @return 允许发送返回 true，否则 false
     */
    private boolean isNotificationEnabled(Long userId, String notifyType) {
        SysNotificationConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<SysNotificationConfig>()
                        .eq(SysNotificationConfig::getUserId, userId));
        // 无配置记录时默认开启
        if (config == null) {
            return true;
        }
        // 站内通知总开关
        if (config.getInAppNotify() != null && config.getInAppNotify() == 0) {
            return false;
        }
        // system 类型不检查具体开关
        if ("system".equals(notifyType)) {
            return true;
        }
        Integer typeSwitch = switch (notifyType) {
            case "progress" -> config.getProgressNotify();
            case "task_due" -> config.getTaskDueNotify();
            case "doc_update" -> config.getDocUpdateNotify();
            case "archive" -> config.getArchiveNotify();
            default -> 1; // 未知类型默认发送
        };
        return typeSwitch == null || typeSwitch == 1;
    }

    /**
     * 获取当前登录用户ID，未登录抛 401。
     */
    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 实体转 VO
     */
    private NotificationVO convertToVO(SysNotification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setUserId(n.getUserId());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setNotifyType(n.getNotifyType());
        vo.setBizType(n.getBizType());
        vo.setBizId(n.getBizId());
        vo.setPriority(n.getPriority());
        vo.setIsRead(n.getIsRead());
        vo.setReadTime(n.getReadTime());
        vo.setCreatedAt(n.getCreatedAt());
        return vo;
    }

}
