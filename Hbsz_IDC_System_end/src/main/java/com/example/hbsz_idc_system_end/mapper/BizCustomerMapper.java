package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hbsz_idc_system_end.entity.BizCustomer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户数据访问层（Mapper）。
 * <p>
 * 对应实体 {@code BizCustomer}，映射数据表 {@code biz_customer}，负责客户档案信息的
 * 增删改查等基础数据访问。本接口仅继承 MyBatis-Plus 的 {@code BaseMapper}，
 * 未声明自定义 SQL，通用单表操作由框架自动实现。
 *
 * @author hbsz
 */
@Mapper
public interface BizCustomerMapper extends BaseMapper<BizCustomer> {
}
