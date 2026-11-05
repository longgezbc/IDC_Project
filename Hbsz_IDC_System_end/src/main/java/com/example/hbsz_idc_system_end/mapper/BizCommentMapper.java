package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.BizComment;
import org.apache.ibatis.annotations.Mapper;

/** 评论 Mapper（CRUD 由 BaseMapper 提供） */
@Mapper
public interface BizCommentMapper extends BaseMapper<BizComment> {
}
