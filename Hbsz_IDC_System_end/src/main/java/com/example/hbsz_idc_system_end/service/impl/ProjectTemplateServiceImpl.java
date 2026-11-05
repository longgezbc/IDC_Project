package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.BizProjectTemplate;
import com.example.hbsz_idc_system_end.mapper.BizProjectTemplateMapper;
import com.example.hbsz_idc_system_end.service.IProjectTemplateService;
import com.example.hbsz_idc_system_end.utils.SqlUtils;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 项目模板服务实现。CRUD 由 ServiceImpl 提供，补充分页查询与启用列表。
 * @author hbsz
 */
@Slf4j
@Service
public class ProjectTemplateServiceImpl
        extends ServiceImpl<BizProjectTemplateMapper, BizProjectTemplate>
        implements IProjectTemplateService {

    @Override
    public PageResult<BizProjectTemplate> queryPage(int pageNum, int pageSize, String keyword, String category) {
        LambdaQueryWrapper<BizProjectTemplate> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = SqlUtils.escapeLike(keyword.trim());
            w.and(q -> q.like(BizProjectTemplate::getName, kw)
                       .or().like(BizProjectTemplate::getCode, kw));
        }
        if (StringUtils.hasText(category)) {
            w.eq(BizProjectTemplate::getCategory, category);
        }
        w.orderByDesc(BizProjectTemplate::getCreatedAt);
        Page<BizProjectTemplate> page = new Page<>(pageNum, pageSize);
        this.page(page, w);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getSize(), page.getCurrent());
    }

    @Override
    public List<BizProjectTemplate> listEnabled() {
        return this.list(new LambdaQueryWrapper<BizProjectTemplate>()
                .eq(BizProjectTemplate::getStatus, 1)
                .orderByDesc(BizProjectTemplate::getCreatedAt));
    }

    @Override
    public BizProjectTemplate getTemplateDetail(Long id) {
        BizProjectTemplate template = this.getById(id);
        if (template == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模板不存在");
        }
        return template;
    }

    @Override
    public BizProjectTemplate createTemplate(BizProjectTemplate template) {
        checkCodeUnique(template.getCode(), null);
        if (template.getStatus() == null) {
            template.setStatus(1);
        }
        template.setCreatedBy(UserContext.getUserId());
        this.save(template);
        log.info("[项目模板] 创建: id={}, code={}, name={}", template.getId(), template.getCode(), template.getName());
        return template;
    }

    @Override
    public BizProjectTemplate updateTemplate(Long id, BizProjectTemplate template) {
        BizProjectTemplate exist = this.getById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模板不存在");
        }
        checkCodeUnique(template.getCode(), id);
        // 保护创建人：接口入参不可覆盖（updateById 对 null 字段不更新，显式置空保证安全）
        template.setId(id);
        template.setCreatedBy(null);
        this.updateById(template);
        log.info("[项目模板] 更新: id={}, code={}", id, template.getCode());
        return this.getById(id);
    }

    /** 编号唯一性校验（更新时排除自身） */
    private void checkCodeUnique(String code, Long excludeId) {
        if (!StringUtils.hasText(code)) return; // 空编号交由 @NotBlank 等入参校验处理
        Long count = this.lambdaQuery()
                .eq(BizProjectTemplate::getCode, code)
                .ne(excludeId != null, BizProjectTemplate::getId, excludeId)
                .count();
        if (count > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "模板编号已存在：" + code);
        }
    }
}
