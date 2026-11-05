package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.OperationLog;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.BizComment;
import com.example.hbsz_idc_system_end.service.ICommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 评论控制器。提供评论查询、创建（含 @提及通知）、删除。
 * @author hbsz
 */
@Tag(name = "评论", description = "通用评论与@提及")
@RestController
@RequestMapping("/comments")
public class CommentController {

    private final ICommentService commentService;

    public CommentController(ICommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "按业务对象查询评论")
    @GetMapping
    public Result<PageResult<BizComment>> list(
            @RequestParam String bizType,
            @RequestParam Long bizId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(commentService.listByBiz(bizType, bizId, pageNum, pageSize));
    }

    @Operation(summary = "创建评论（自动解析@提及并发通知）")
    @OperationLog(module = "评论", type = "新增", description = "创建评论")
    @PostMapping
    public Result<BizComment> create(@Valid @RequestBody BizComment comment) {
        return Result.success("评论成功", commentService.createComment(comment));
    }

    @Operation(summary = "删除评论")
    @OperationLog(module = "评论", type = "删除", description = "删除评论")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.removeById(id);
        return Result.success("删除成功", null);
    }
}
