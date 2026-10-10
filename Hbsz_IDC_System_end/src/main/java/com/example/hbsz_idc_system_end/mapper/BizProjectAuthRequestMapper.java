package com.example.hbsz_idc_system_end.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.hbsz_idc_system_end.dto.AuthRequestQueryDTO;
import com.example.hbsz_idc_system_end.dto.AuthRequestVO;
import com.example.hbsz_idc_system_end.entity.BizProjectAuthRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 项目授权申请数据访问层（Mapper）。
 * <p>
 * 对应实体 {@link BizProjectAuthRequest}，映射数据表 {@code biz_project_auth_request}，
 * 负责项目授权申请的增删改查。继承 MyBatis-Plus 的 {@link BaseMapper} 提供通用 CRUD，
 * 额外提供分页查询（含申请人/目标工程师/项目名/审批人姓名等冗余字段，XML 实现）。
 *
 * @author hbsz
 */
@Mapper
public interface BizProjectAuthRequestMapper extends BaseMapper<BizProjectAuthRequest> {

    /**
     * 分页查询项目授权申请列表（按创建时间倒序，含申请人/目标工程师/项目名/审批人姓名）。
     *
     * @param page  分页对象
     * @param query 查询条件（status / projectId / applicantId）
     * @return 分页结果
     */
    IPage<AuthRequestVO> selectAuthRequestPage(IPage<AuthRequestVO> page,
                                                @Param("query") AuthRequestQueryDTO query);

}
