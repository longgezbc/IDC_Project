package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一审批待办计数视图对象。
 * <p>
 * 按来源分别统计待审数量，供待办中心统计卡片与角标使用。
 *
 * @author hbsz
 */
@Data
public class TodoCountVO implements Serializable {

    /** 进度填报待审数 */
    private long progress;

    /** 工作量填报待审数 */
    private long workload;

    /** 文档上传待审数 */
    private long document;

    /** 项目授权申请待审数 */
    private long auth;

    /** 合计（四类之和） */
    private long total;

    /**
     * 按来源累加计数。
     *
     * @param source 来源标识
     * @param count 数量
     */
    public void add(String source, long count) {
        if (source == null) {
            return;
        }
        switch (source) {
            case "progress" -> progress += count;
            case "workload" -> workload += count;
            case "document" -> document += count;
            case "auth" -> auth += count;
            default -> { /* 未知来源忽略，向前兼容 */ }
        }
    }

    /** 计算合计。 */
    public void finish() {
        this.total = progress + workload + document + auth;
    }
}
