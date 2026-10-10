package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量导入结果 VO。
 * <p>
 * 用于向调用方反馈导入的总体情况：总行数、成功/失败数及失败详情列表。
 *
 * @author hbsz
 */
@Data
public class ImportResultVO implements Serializable {

    /** 总行数（不含表头） */
    private int total;

    /** 成功导入数 */
    private int successCount;

    /** 失败数 */
    private int failCount;

    /** 失败详情（每条含行号和原因） */
    private List<String> errors;

    /**
     * 构造导入结果对象。
     *
     * @param total   总行数
     * @param success 成功数
     * @param errors  失败详情列表
     * @return 已填充的 ImportResultVO
     */
    public static ImportResultVO of(int total, int success, List<String> errors) {
        ImportResultVO vo = new ImportResultVO();
        vo.setTotal(total);
        vo.setSuccessCount(success);
        vo.setFailCount(errors.size());
        vo.setErrors(errors);
        return vo;
    }
}
