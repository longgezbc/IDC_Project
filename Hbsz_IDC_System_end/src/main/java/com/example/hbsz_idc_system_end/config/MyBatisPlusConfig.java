package com.example.hbsz_idc_system_end.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.example.hbsz_idc_system_end.utils.PinyinUtils;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置类
 * <p>
 * 集中配置 MyBatis-Plus 的核心插件与全局行为，包括分页插件、乐观锁插件，
 * 以及公共审计字段（创建时间/更新时间）的自动填充，减少业务层重复代码。
 *
 * @author hbsz
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus 核心拦截器。
     * <p>
     * 注册两个内部拦截器：
     * <ul>
     *   <li>{@link PaginationInnerInterceptor}：MySQL 物理分页支持</li>
     *   <li>{@link OptimisticLockerInnerInterceptor}：配合 {@code @Version} 字段实现乐观锁</li>
     * </ul>
     * 注意：分页插件需在乐观锁插件之前添加，顺序影响拦截链执行。
     *
     * @return 已注册内部插件的 MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件（MySQL 类型）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 公共字段自动填充处理器。
     * <p>
     * 插入时自动填充 createdAt、updatedAt；更新时自动填充 updatedAt，
     * 依赖实体字段上的 {@code @TableField(fill = ...)} 声明生效。
     * <p>
     * 拼音冗余列（namePinyinFull / namePinyinInitials）：
     * 对 BizProject / BizDocument / BizCustomer 三类实体，随 name 自动生成小写全拼与首字母，
     * 供全局搜索拼音匹配。更新时若 name 为空（部分字段更新）则跳过，保留原拼音值。
     *
     * @return 元对象字段填充处理器
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
                fillPinyinFields(metaObject);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
                fillPinyinFields(metaObject);
            }

            /**
             * 按 name 生成拼音冗余字段。
             * name 为空（未随本次写入提交）时跳过，避免把已有拼音覆盖为 null。
             */
            private void fillPinyinFields(MetaObject metaObject) {
                Object entity = metaObject.getOriginalObject();
                if (!(entity instanceof com.example.hbsz_idc_system_end.entity.BizProject
                        || entity instanceof com.example.hbsz_idc_system_end.entity.BizDocument
                        || entity instanceof com.example.hbsz_idc_system_end.entity.BizCustomer)) {
                    return;
                }
                Object nameVal = metaObject.getValue("name");
                if (!(nameVal instanceof String name) || name.isBlank()) {
                    return;
                }
                String full = PinyinUtils.toFullPinyin(name);
                String initials = PinyinUtils.toInitials(name);
                if (full != null) {
                    this.setFieldValByName("namePinyinFull", full, metaObject);
                }
                if (initials != null) {
                    this.setFieldValByName("namePinyinInitials", initials, metaObject);
                }
            }
        };
    }

}
