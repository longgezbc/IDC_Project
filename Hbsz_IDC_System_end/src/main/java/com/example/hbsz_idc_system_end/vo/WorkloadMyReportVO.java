package com.example.hbsz_idc_system_end.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 我的快速填报上下文视图对象。
 * <p>
 * 为"快速填报"卡片流一次性提供全部上下文，让普通工人无需在
 * 106 项清单矩阵中滚动定位：前端进入页面即得到常用项卡片、
 * 昨日数量与今日已有填报，配合"复制昨天"实现 30 秒完成填报。
 * <p>
 * 设计说明：
 * <ul>
 *   <li>projects：当前用户作为在组成员参与的项目（未归档），
 *       单项目时前端自动选中，多项目时让用户选择；</li>
 *   <li>frequentItems：最近 14 天该用户填报过的清单项按频次排序，
 *       实践中施工人员的作业项高度稳定（通常 3-8 项），
 *       卡片流可覆盖绝大多数日常场景；</li>
 *   <li>保存路径复用 POST /workload/records（审核链路/成员校验/upsert
 *       单点不变），本 VO 仅提供查询上下文。</li>
 * </ul>
 *
 * @author hbsz
 */
@Data
public class WorkloadMyReportVO implements Serializable {

    /** 当前用户ID（前端提交 saveRecords 时作为 workerUserId 回传） */
    private Long myUserId;

    /** 当前用户姓名（卡片头部展示） */
    private String myName;

    /** 我参与的项目组（未归档项目），单项目时前端自动选中 */
    private List<ProjectBrief> projects;

    /** 当前上下文项目ID（请求未指定时为空，前端仅展示项目选择） */
    private Long projectId;

    /** 当前上下文项目名称 */
    private String projectName;

    /** 填报日期（上下文日期，默认今天） */
    private LocalDate reportDate;

    /** 我今日已有填报：itemId → 单元格（数量/审核状态/审核意见） */
    private Map<Long, WorkloadCellVO> todayRecords;

    /** 我昨日填报数量：itemId → quantity（供"复制昨天"） */
    private Map<Long, BigDecimal> yesterdayRecords;

    /** 我最近 14 天的常用清单项（按填报频次降序） */
    private List<FrequentItem> frequentItems;

    /** 项目清单总项数（提示还有更多项可通过搜索添加） */
    private Integer totalItemCount;

    /**
     * 我参与的项目简要信息。
     */
    @Data
    public static class ProjectBrief implements Serializable {

        private Long projectId;

        private String projectName;

        /** 我在该项目组的角色（member/leader） */
        private String roleInTeam;
    }

    /**
     * 常用清单项：清单项详情 + 我的填报统计。
     * 前端渲染为卡片（作业类型 · 作业范围 · 单位 · 昨日量 · 填报频次）。
     */
    @Data
    public static class FrequentItem implements Serializable {

        private Long itemId;

        /** 作业类型 */
        private String category;

        /** 作业范围 */
        private String workScope;

        /** 单位 */
        private String unit;

        /** 额定工作量（每人每日配额，提示用） */
        private BigDecimal ratedWorkload;

        /** 最近 14 天内我的填报天数（频次） */
        private Integer fillCount;

        /** 我最后一次填报数量（默认值参考） */
        private BigDecimal lastQuantity;

        /** 最后填报日期（yyyy-MM-dd） */
        private LocalDate lastDate;
    }
}
