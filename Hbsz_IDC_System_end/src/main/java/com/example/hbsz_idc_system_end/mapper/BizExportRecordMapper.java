package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.BizExportRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导出历史记录数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link BizExportRecord}，映射数据表 {@code biz_export_record}，
 * 负责导出历史记录的增删改查。本接口仅继承 MyBatis-Plus 的 {@code BaseMapper}，
 * 通用单表操作（含逻辑删除）由框架自动实现。
 *
 * @author hbsz
 */
@Mapper
public interface BizExportRecordMapper extends BaseMapper<BizExportRecord> {
}
