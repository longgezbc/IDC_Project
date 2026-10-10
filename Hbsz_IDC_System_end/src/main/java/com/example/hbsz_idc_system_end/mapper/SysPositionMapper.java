package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.SysPosition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 岗位职级数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link SysPosition}，映射数据表 {@code sys_position}，负责岗位的增删改查。
 * 继承 MyBatis-Plus 的 {@link BaseMapper} 即可获得通用 CRUD 能力，
 * 当前无需额外的自定义 SQL，所有查询均通过 LambdaQueryWrapper 完成。
 *
 * @author hbsz
 */
@Mapper
public interface SysPositionMapper extends BaseMapper<SysPosition> {
}
