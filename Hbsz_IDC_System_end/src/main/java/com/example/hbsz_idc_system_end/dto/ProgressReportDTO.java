package com.example.hbsz_idc_system_end.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 进度填报 DTO（请求参数封装）。
 * <p>
 * 用于工程师上报任务进度。提交后进入项目经理审核流程，审核通过后才会实际更新任务进度。
 * 含字段：
 * <ul>
 *   <li>progress：进度百分比；</li>
 *   <li>remark：填报备注；</li>
 *   <li>images：施工图片URL列表，前端上传文件后由文件存储服务返回相对路径；</li>
 * </ul>
 *
 * @author hbsz
 */
@Data
public class ProgressReportDTO implements Serializable {

    /** 进度百分比：@NotNull 非空，@Min/@Max 取值范围 0-100 */
    @NotNull(message = "进度不能为空")
    @Min(value = 0, message = "进度不能小于0")
    @Max(value = 100, message = "进度不能大于100")
    private Integer progress;

    /** 填报备注 */
    private String remark;

    /** 施工图片URL列表（前端先调用 /files/upload 获取URL，再回传） */
    private List<String> images;

    /** 实际开工时间（必填） */
    @NotNull(message = "实际开工时间不能为空")
    private LocalDate actualStartDate;

    /** 实际结束时间（必填） */
    @NotNull(message = "实际结束时间不能为空")
    private LocalDate actualEndDate;

}