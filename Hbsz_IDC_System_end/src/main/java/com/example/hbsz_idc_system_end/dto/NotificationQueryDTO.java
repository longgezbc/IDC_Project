package com.example.hbsz_idc_system_end.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 通知查询 DTO
 * <p>
 * 用于站内通知列表查询的筛选参数，支持按通知类型与已读状态筛选。
 * 继承 {@link PageQuery} 获得分页参数（pageNum/pageSize）。
 * </p>
 *
 * @author hbsz
 */
@Data
public class NotificationQueryDTO extends PageQuery implements Serializable {

    /** 通知类型：progress/task_due/doc_update/archive/system（可空，空则查全部） */
    private String notifyType;

    /** 已读状态：true已读 false未读（可空，空则查全部） */
    private Boolean isRead;

}
