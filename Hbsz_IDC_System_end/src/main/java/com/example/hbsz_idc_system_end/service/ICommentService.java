package com.example.hbsz_idc_system_end.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.BizComment;

/**
 * 评论服务。提供评论查询、创建（含 @提及解析与通知）、删除。
 * @author hbsz
 */
public interface ICommentService extends IService<BizComment> {

    /** 按业务对象分页查询评论 */
    PageResult<BizComment> listByBiz(String bizType, Long bizId, int pageNum, int pageSize);

    /** 创建评论（自动解析 @用户名 并发送提及通知） */
    BizComment createComment(BizComment comment);
}
