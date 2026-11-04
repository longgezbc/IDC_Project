package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hbsz_idc_system_end.dto.SearchItemVO;
import com.example.hbsz_idc_system_end.dto.SearchResultVO;
import com.example.hbsz_idc_system_end.entity.BizCustomer;
import com.example.hbsz_idc_system_end.entity.BizDocument;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.mapper.BizCustomerMapper;
import com.example.hbsz_idc_system_end.mapper.BizDocumentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.service.IProjectAuthorizationService;
import com.example.hbsz_idc_system_end.service.ISearchService;
import com.example.hbsz_idc_system_end.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局搜索服务实现。
 * <p>
 * 三类查询均用 MyBatis-Plus LambdaQueryWrapper + like() 参数绑定（防 SQL 注入），
 * 关键字经 {@link SqlUtils#escapeLike} 转义 % _ \ 通配符。
 * <p>
 * 数据隔离：项目与文档按当前用户可见项目过滤（admin/PM 不过滤；普通用户仅可见授权项目）；
 * 客户按启用状态过滤，不按项目隔离（客户为共享主数据）。
 *
 * @author hbsz
 */
@Slf4j
@Service
public class SearchServiceImpl implements ISearchService {

    private final BizProjectMapper projectMapper;
    private final BizDocumentMapper documentMapper;
    private final BizCustomerMapper customerMapper;
    private final IProjectAuthorizationService projectAuthorizationService;

    public SearchServiceImpl(BizProjectMapper projectMapper, BizDocumentMapper documentMapper,
                              BizCustomerMapper customerMapper,
                              IProjectAuthorizationService projectAuthorizationService) {
        this.projectMapper = projectMapper;
        this.documentMapper = documentMapper;
        this.customerMapper = customerMapper;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Override
    public SearchResultVO search(String keyword, int limit) {
        SearchResultVO vo = new SearchResultVO();
        if (!StringUtils.hasText(keyword)) {
            vo.setProjects(Collections.emptyList());
            vo.setDocuments(Collections.emptyList());
            vo.setCustomers(Collections.emptyList());
            return vo;
        }
        // 限制 limit 范围，防止过大查询
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String kw = SqlUtils.escapeLike(keyword.trim());

        // 当前用户可见项目ID（null=不过滤；空列表=无可见项目）
        List<Long> visibleProjectIds = projectAuthorizationService.getCurrentUserVisibleProjectIds();

        List<SearchItemVO> projects = searchProjects(kw, safeLimit, visibleProjectIds);
        List<SearchItemVO> documents = searchDocuments(kw, safeLimit, visibleProjectIds);
        List<SearchItemVO> customers = searchCustomers(kw, safeLimit);

        vo.setProjects(projects);
        vo.setDocuments(documents);
        vo.setCustomers(customers);
        vo.setTotal(projects.size() + documents.size() + customers.size());
        log.debug("[全局搜索] keyword={}, limit={}, 命中: 项目={}, 文档={}, 客户={}",
                keyword, safeLimit, projects.size(), documents.size(), customers.size());
        return vo;
    }

    /** 搜索项目：name/projectCode 模糊匹配，未归档，按可见项目过滤 */
    private List<SearchItemVO> searchProjects(String kw, int limit, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null && visibleProjectIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<BizProject> w = new LambdaQueryWrapper<BizProject>()
                .and(q -> q.like(BizProject::getName, kw)
                        .or().like(BizProject::getProjectCode, kw)
                        .or().like(BizProject::getNamePinyinFull, kw)
                        .or().like(BizProject::getNamePinyinInitials, kw))
                .eq(BizProject::getIsArchived, 0);
        if (visibleProjectIds != null) {
            w.in(BizProject::getId, visibleProjectIds);
        }
        w.last("LIMIT " + limit);
        List<BizProject> list = projectMapper.selectList(w);
        return list.stream().map(p -> {
            SearchItemVO item = new SearchItemVO();
            item.setId(p.getId());
            item.setName(p.getName());
            item.setExtra(p.getProjectCode());
            return item;
        }).collect(Collectors.toList());
    }

    /** 搜索文档：name 模糊匹配，按可见项目过滤 */
    private List<SearchItemVO> searchDocuments(String kw, int limit, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null && visibleProjectIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<BizDocument> w = new LambdaQueryWrapper<BizDocument>()
                .and(q -> q.like(BizDocument::getName, kw)
                        .or().like(BizDocument::getNamePinyinFull, kw)
                        .or().like(BizDocument::getNamePinyinInitials, kw));
        if (visibleProjectIds != null) {
            w.in(BizDocument::getProjectId, visibleProjectIds);
        }
        w.last("LIMIT " + limit);
        List<BizDocument> list = documentMapper.selectList(w);
        return list.stream().map(d -> {
            SearchItemVO item = new SearchItemVO();
            item.setId(d.getId());
            item.setName(d.getName());
            item.setExtra(d.getCategory());
            return item;
        }).collect(Collectors.toList());
    }

    /** 搜索客户：name/contactPerson 模糊匹配，仅启用状态 */
    private List<SearchItemVO> searchCustomers(String kw, int limit) {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<BizCustomer>()
                .and(q -> q.like(BizCustomer::getName, kw)
                        .or().like(BizCustomer::getContactPerson, kw)
                        .or().like(BizCustomer::getNamePinyinFull, kw)
                        .or().like(BizCustomer::getNamePinyinInitials, kw))
                .eq(BizCustomer::getStatus, 1)
                .last("LIMIT " + limit);
        List<BizCustomer> list = customerMapper.selectList(w);
        return list.stream().map(c -> {
            SearchItemVO item = new SearchItemVO();
            item.setId(c.getId());
            item.setName(c.getName());
            item.setExtra(c.getContactPerson() != null ? c.getContactPerson() + " · " + c.getContactPhone() : c.getContactPhone());
            return item;
        }).collect(Collectors.toList());
    }
}
