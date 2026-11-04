package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.SearchResultVO;
import com.example.hbsz_idc_system_end.service.ISearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局搜索控制器。
 * <p>
 * 提供跨模块（项目/文档/客户）的聚合搜索接口，供前端顶部搜索框调用。
 * 基础请求路径：{@code /search}。
 *
 * @author hbsz
 */
@Tag(name = "全局搜索", description = "跨模块聚合搜索接口")
@RestController
@RequestMapping("/search")
public class SearchController {

    private final ISearchService searchService;

    public SearchController(ISearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 全局搜索。
     * <p>HTTP：GET /search?q=keyword&limit=5。
     * 按 项目/文档/客户 三类分组返回命中条目，每类最多 limit 条。
     * 权限：登录用户即可访问（搜索结果按用户可见范围过滤）。
     *
     * @param q     关键字
     * @param limit 每类最大返回条数（默认 5，上限 20）
     * @return 分组聚合搜索结果
     */
    @Operation(summary = "全局搜索", description = "按关键字聚合搜索项目/文档/客户")
    @GetMapping
    public Result<SearchResultVO> search(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return Result.success(searchService.search(q, limit));
    }
}
