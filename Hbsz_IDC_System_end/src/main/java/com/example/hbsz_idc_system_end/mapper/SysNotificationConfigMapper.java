package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.SysNotificationConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知配置数据访问层（Mapper）。
 * <p>
 * 对应实体 {@code SysNotificationConfig}，映射数据表 {@code sys_notification_config}，
 * 负责系统消息通知配置的增删改查等基础数据访问。本接口仅继承 MyBatis-Plus 的 {@code BaseMapper}，
 * 未声明自定义 SQL，通用单表操作由框架自动实现。
 *
 * @author hbsz
 */
@Mapper
public interface SysNotificationConfigMapper extends BaseMapper<SysNotificationConfig> {
}
