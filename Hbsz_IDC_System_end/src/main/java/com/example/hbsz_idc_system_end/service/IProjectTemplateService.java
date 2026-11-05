package com.example.hbsz_idc_system_end.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.BizProjectTemplate;

import java.util.List;

/**
 * 项目模板服务。提供模板 CRUD 与按分类查询。
 * @author hbsz
 */
public interface IProjectTemplateService extends IService<BizProjectTemplate> {

    /** 分页查询模板（支持按名称/分类筛选） */
    PageResult<BizProjectTemplate> queryPage(int pageNum, int pageSize, String keyword, String category);

    /** 查询启用的模板列表（供新建项目时下拉选择） */
    List<BizProjectTemplate> listEnabled();

    /** 模板详情（不存在抛 404 业务异常） */
    BizProjectTemplate getTemplateDetail(Long id);

    /** 新建模板：校验编号唯一，填充创建人与默认状态 */
    BizProjectTemplate createTemplate(BizProjectTemplate template);

    /** 更新模板：校验存在性与编号唯一（排除自身），保护创建人字段不被覆盖 */
    BizProjectTemplate updateTemplate(Long id, BizProjectTemplate template);
}
