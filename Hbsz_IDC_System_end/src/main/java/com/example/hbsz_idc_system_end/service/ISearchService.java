package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.SearchResultVO;

/**
 * 全局搜索服务。
 * <p>
 * 聚合查询项目/文档/客户，按关键字模糊匹配，每类限制返回条数。
 * 项目与文档按当前用户可见范围过滤，客户按启用状态过滤。
 *
 * @author hbsz
 */
public interface ISearchService {

    /**
     * 全局搜索。
     *
     * @param keyword 关键字（模糊匹配名称/编号等）
     * @param limit   每类最大返回条数（1-20）
     * @return 分组聚合结果
     */
    SearchResultVO search(String keyword, int limit);
}
