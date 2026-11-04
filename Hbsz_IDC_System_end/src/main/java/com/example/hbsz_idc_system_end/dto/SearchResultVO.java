package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.util.List;

/**
 * 全局搜索聚合结果。
 * <p>
 * 按 项目/文档/客户 三类分组返回命中条目，每类最多 limit 条（默认 5）。
 *
 * @author hbsz
 */
@Data
public class SearchResultVO {

    /** 项目命中 */
    private List<SearchItemVO> projects;

    /** 文档命中 */
    private List<SearchItemVO> documents;

    /** 客户命中 */
    private List<SearchItemVO> customers;

    /** 总命中数（三类之和） */
    private int total;
}
