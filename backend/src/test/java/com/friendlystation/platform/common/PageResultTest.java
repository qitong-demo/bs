package com.friendlystation.platform.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageResultTest {
    @Test
    void slicesMiddlePage() {
        PageResult<Integer> page = PageResult.of(2, 3, List.of(1, 2, 3, 4, 5, 6, 7));
        assertEquals(2, page.current());
        assertEquals(3, page.size());
        assertEquals(7, page.total());
        assertEquals(List.of(4, 5, 6), page.records());
    }

    @Test
    void returnsEmptyRecordsPastTheEnd() {
        PageResult<Integer> page = PageResult.of(5, 10, List.of(1, 2, 3));
        assertEquals(3, page.total());
        assertTrue(page.records().isEmpty());
    }

    @Test
    void normalizesInvalidPagingArguments() {
        PageResult<Integer> page = PageResult.of(0, 0, List.of(1, 2));
        assertEquals(1, page.current());
        assertEquals(1, page.size());
        assertEquals(List.of(1), page.records());
    }

    @Test
    void capsPageSizeAtTwoHundred() {
        PageResult<Integer> page = PageResult.of(1, 1000, List.of(1, 2));
        assertEquals(200, page.size());
        assertEquals(2, page.records().size());
    }
}
