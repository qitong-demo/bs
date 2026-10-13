package com.friendlystation.platform.statistics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvSupportTest {
    @Test
    void startsWithBomSoExcelReadsChinese() {
        String csv = CsvSupport.toCsv(List.of("编号", "标题"), List.of(List.of("1", "测试")));
        assertTrue(csv.startsWith("\uFEFF"), "CSV 应以 UTF-8 BOM 开头");
        assertTrue(csv.contains("编号,标题"));
        assertTrue(csv.contains("1,测试"));
    }

    @Test
    void usesCarriageReturnLineFeed() {
        String csv = CsvSupport.toCsv(List.of("a"), List.of(List.of("1"), List.of("2")));
        assertEquals("\uFEFFa\r\n1\r\n2\r\n", csv);
    }

    @Test
    void escapesCommasQuotesAndNewlines() {
        String csv = CsvSupport.toCsv(List.of("head"), List.of(List.of("含,逗号"), List.of("含\"引号"), List.of("含\n换行")));
        assertTrue(csv.contains("\"含,逗号\""));
        assertTrue(csv.contains("\"含\"\"引号\""));
        assertTrue(csv.contains("\"含\n换行\""));
    }

    @Test
    void writesEmptyCellForNullValue() {
        String csv = CsvSupport.toCsv(List.of("a", "b"), List.of(java.util.Arrays.asList("1", null)));
        assertTrue(csv.endsWith("1,\r\n"));
    }
}
