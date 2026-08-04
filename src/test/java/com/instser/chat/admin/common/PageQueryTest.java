package com.instser.chat.admin.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageQueryTest {

    @Test
    void safeSize_shouldCapAt100() {
        PageQuery query = new PageQuery();
        query.setSize(500);
        assertEquals(100, query.safeSize());
    }

    @Test
    void safePage_shouldDefaultTo1() {
        PageQuery query = new PageQuery();
        query.setPage(0);
        assertEquals(1, query.safePage());
    }

    @Test
    void isAsc_shouldParseOrder() {
        PageQuery query = new PageQuery();
        query.setSortOrder("asc");
        assertTrue(query.isAsc());
        query.setSortOrder("desc");
        assertFalse(query.isAsc());
    }
}
