package com.example.hbsz_idc_system_end.dto;

import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询基础参数（查询对象基类）。
 * 作为各业务列表查询 DTO 的父类，封装页码与每页条数，并对非法值自动校正。
 *
 * @author hbsz
 */
@Data
public class PageQuery implements Serializable {

    /** 分页大小上限，防止恶意请求导致 OOM */
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MIN_PAGE_NUM = 1;

    /**
     * 当前页码，默认1
     */
    private Integer pageNum = CommonConstant.DEFAULT_PAGE_NUM;

    /**
     * 每页条数，默认10
     */
    private Integer pageSize = 10;

    /**
     * 设置页码，自动校正非法值
     */
    public void setPageNum(Integer pageNum) {
        this.pageNum = (pageNum == null || pageNum < MIN_PAGE_NUM) ? CommonConstant.DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 设置每页条数，自动校正非法值（上限 200）
     */
    public void setPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            this.pageSize = 10;
        } else if (pageSize > MAX_PAGE_SIZE) {
            this.pageSize = MAX_PAGE_SIZE;
        } else {
            this.pageSize = pageSize;
        }
    }

    /**
     * 获取MyBatis-Plus分页偏移量
     */
    public long getOffset() {
        return (long) (pageNum - 1) * pageSize;
    }

}
