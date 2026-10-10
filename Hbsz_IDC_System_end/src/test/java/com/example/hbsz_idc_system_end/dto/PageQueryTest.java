package com.example.hbsz_idc_system_end.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PageQuery 单元测试
 *
 * @author hbsz
 */
@DisplayName("分页参数测试")
class PageQueryTest {

    @Test
    @DisplayName("默认值测试")
    void defaultValue() {
        PageQuery query = new PageQuery();
        assertEquals(1, query.getPageNum());
        assertEquals(10, query.getPageSize());
    }

    @Test
    @DisplayName("页码为null时使用默认值")
    void setPageNum_null() {
        PageQuery query = new PageQuery();
        query.setPageNum(null);
        assertEquals(1, query.getPageNum());
    }

    @Test
    @DisplayName("页码为0或负数时使用默认值")
    void setPageNum_invalid() {
        PageQuery query = new PageQuery();
        query.setPageNum(0);
        assertEquals(1, query.getPageNum());
        query.setPageNum(-5);
        assertEquals(1, query.getPageNum());
    }

    @Test
    @DisplayName("页码为正数时正常设置")
    void setPageNum_valid() {
        PageQuery query = new PageQuery();
        query.setPageNum(5);
        assertEquals(5, query.getPageNum());
    }

    @Test
    @DisplayName("分页大小为null时使用默认值")
    void setPageSize_null() {
        PageQuery query = new PageQuery();
        query.setPageSize(null);
        assertEquals(10, query.getPageSize());
    }

    @Test
    @DisplayName("分页大小为0或负数时使用默认值")
    void setPageSize_invalid() {
        PageQuery query = new PageQuery();
        query.setPageSize(0);
        assertEquals(10, query.getPageSize());
        query.setPageSize(-1);
        assertEquals(10, query.getPageSize());
    }

    @Test
    @DisplayName("分页大小超过上限200时被截断")
    void setPageSize_exceedMax() {
        PageQuery query = new PageQuery();
        query.setPageSize(500);
        assertEquals(200, query.getPageSize());
        query.setPageSize(1000);
        assertEquals(200, query.getPageSize());
    }

    @Test
    @DisplayName("分页大小在合法范围内正常设置")
    void setPageSize_valid() {
        PageQuery query = new PageQuery();
        query.setPageSize(50);
        assertEquals(50, query.getPageSize());
        query.setPageSize(200);
        assertEquals(200, query.getPageSize());
    }

    @Test
    @DisplayName("偏移量计算正确")
    void getOffset() {
        PageQuery query = new PageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        assertEquals(0, query.getOffset());

        query.setPageNum(2);
        query.setPageSize(20);
        assertEquals(20, query.getOffset());

        query.setPageNum(5);
        query.setPageSize(50);
        assertEquals(200, query.getOffset());
    }
}
