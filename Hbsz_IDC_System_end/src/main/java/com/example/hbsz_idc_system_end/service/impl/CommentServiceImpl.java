package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.hbsz_idc_system_end.dto.NotificationSendDTO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.BizComment;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.BizCommentMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.ICommentService;
import com.example.hbsz_idc_system_end.service.INotificationService;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 评论服务实现。
 * <p>
 * 核心逻辑：创建评论时用正则解析 content 中的 @用户名，查 sys_user 表获取 userId，
 * 存入 mentions 字段并向被提及用户（排除评论人自己）发送 mention 类型站内通知。
 * @author hbsz
 */
@Slf4j
@Service
public class CommentServiceImpl extends ServiceImpl<BizCommentMapper, BizComment> implements ICommentService {

    private final SysUserMapper sysUserMapper;
    private final INotificationService notificationService;

    /** 匹配 @用户名（中文字符/字母/数字/下划线，到空格或标点止） */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\u4e00-\\u9fa5\\w]+)");

    public CommentServiceImpl(SysUserMapper sysUserMapper, INotificationService notificationService) {
        this.sysUserMapper = sysUserMapper;
        this.notificationService = notificationService;
    }

    @Override
    public PageResult<BizComment> listByBiz(String bizType, Long bizId, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizComment> w = new LambdaQueryWrapper<BizComment>()
                .eq(BizComment::getBizType, bizType)
                .eq(BizComment::getBizId, bizId)
                .orderByAsc(BizComment::getCreatedAt);
        Page<BizComment> page = new Page<>(pageNum, pageSize);
        this.page(page, w);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getSize(), page.getCurrent());
    }

    @Override
    public BizComment createComment(BizComment comment) {
        // 设置评论人信息
        comment.setAuthorId(UserContext.getUserId());
        comment.setAuthorName(UserContext.getUsername());
        // 先保存评论（获取 id）
        this.save(comment);
        // 解析 @提及并发通知
        processMentions(comment);
        // 更新 mentions 字段
        if (StringUtils.hasText(comment.getMentions())) {
            this.updateById(comment);
        }
        log.info("[评论] 创建成功: id={}, bizType={}, bizId={}, author={}",
                comment.getId(), comment.getBizType(), comment.getBizId(), comment.getAuthorName());
        return comment;
    }

    /** 解析 content 中的 @用户名，查用户并发送提及通知 */
    private void processMentions(BizComment comment) {
        String content = comment.getContent();
        if (!StringUtils.hasText(content)) return;

        List<String> names = new ArrayList<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!names.contains(name)) names.add(name);
        }
        if (names.isEmpty()) return;

        // 按 name 批量查用户
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getName, names));
        if (users.isEmpty()) return;

        // 存 mentions（userId 逗号分隔）
        comment.setMentions(users.stream()
                .map(u -> u.getId().toString())
                .collect(Collectors.joining(",")));

        // 发送提及通知（排除评论人自己）
        List<NotificationSendDTO> notifications = users.stream()
                .filter(u -> !u.getId().equals(comment.getAuthorId()))
                .map(u -> {
                    NotificationSendDTO n = new NotificationSendDTO();
                    n.setUserId(u.getId());
                    n.setTitle("你被提及了");
                    n.setContent(comment.getAuthorName() + " 在评论中提及了你："
                            + (content.length() > 50 ? content.substring(0, 50) + "..." : content));
                    n.setNotifyType("mention");
                    n.setBizType(comment.getBizType());
                    n.setBizId(comment.getBizId());
                    n.setPriority(2);
                    return n;
                })
                .collect(Collectors.toList());
        if (!notifications.isEmpty()) {
            notificationService.sendNotifications(notifications);
            log.info("[评论] @提及通知已发送: commentId={}, 通知{}人", comment.getId(), notifications.size());
        }
    }
}
