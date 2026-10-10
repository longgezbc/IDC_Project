package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统配置实体
 * <p>
 * 对应数据库表 sys_system_config，采用 key-value 设计存储系统级参数配置，
 * 支持按配置项键名动态读取业务参数。
 * </p>
 *
 * @author hbsz
 */
@Data
@TableName("sys_system_config")
public class SysSystemConfig extends BaseEntity {

    /** 主键ID，@TableId 采用数据库自增策略（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置项键名（唯一标识） */
    private String configKey;

    /** 配置项值 */
    private String configValue;

    /** 配置项名称（中文含义） */
    private String configName;

    /** 配置值类型：如 string/number/boolean/json */
    private String configType;

    /** 备注 */
    private String remark;

}
