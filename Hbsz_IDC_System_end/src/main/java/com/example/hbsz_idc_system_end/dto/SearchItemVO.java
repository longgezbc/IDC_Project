package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

/**
 * 全局搜索单条结果项。
 * <p>
 * 统一表示项目/文档/客户的搜索命中，前端按 type 路由到对应详情页。
 *
 * @author hbsz
 */
@Data
public class SearchItemVO {

    /** 业务主键ID */
    private Long id;

    /** 主显示名称（项目名/文档名/客户名） */
    private String name;

    /** 附加信息（项目编号/文档分类/客户联系人），前端副标题展示 */
    private String extra;
}
