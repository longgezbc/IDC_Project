package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.SysNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 站内通知 Mapper
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper} 获得基础 CRUD 能力。
 * 额外提供批量标记已读和批量插入的自定义 SQL，规避逐条操作的性能问题。
 * </p>
 *
 * @author hbsz
 */
@Mapper
public interface SysNotificationMapper extends BaseMapper<SysNotification> {

    /**
     * 批量将指定用户的全部未读通知标记为已读。
     * <p>
     * 使用单条 UPDATE 语句完成批量更新，避免循环逐条更新。
     * readTime 使用数据库当前时间 NOW()，保证同一用户所有通知的阅读时间一致。
     *
     * @param userId 用户 ID
     * @return 受影响行数
     */
    @Update("UPDATE sys_notification SET is_read = 1, read_time = NOW() " +
            "WHERE user_id = #{userId} AND is_read = 0")
    int markAllAsRead(@Param("userId") Long userId);

    /**
     * 批量插入通知记录。
     * <p>
     * 使用单条 INSERT ... VALUES (...),(...) 语句一次性写入多条通知，
     * 避免循环单条插入产生的多次数据库往返，提升批量通知发送性能。
     *
     * @param list 通知实体列表
     * @return 受影响行数
     */
    int batchInsert(@Param("list") List<SysNotification> list);

}
