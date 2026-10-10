package com.example.hbsz_idc_system_end.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体类基类
 * <p>
 * 抽象基类，统一封装创建时间与更新时间字段，供各业务实体继承复用。
 * 通过 MyBatis-Plus 的自动填充机制在插入/更新时自动写入审计时间，无需业务代码手动赋值。
 * </p>
 *
 * @author hbsz
 */
@Data
public abstract class BaseEntity implements Serializable {

    /** 创建时间，@TableField 映射 created_at 列，插入时（INSERT）自动填充 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，@TableField 映射 updated_at 列，插入与更新时（INSERT_UPDATE）自动填充 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}
