package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.BizWorkloadItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作量清单数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link BizWorkloadItem}，映射数据表 biz_workload_item。
 * 继承 MyBatis-Plus 的 BaseMapper，通用单表操作由框架自动实现；
 * 聚合统计查询由 biz_workload_record 侧按 item_id 分组完成，本接口无需自定义 SQL。
 *
 * @author hbsz
 */
@Mapper
public interface BizWorkloadItemMapper extends BaseMapper<BizWorkloadItem> {

}
