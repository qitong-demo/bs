package com.friendlystation.platform.statistics;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatisticsSupportTest {
    @Test
    void roundsToSingleDecimal() {
        assertEquals(4.6, StatisticsSupport.round1(4.55));
        assertEquals(0.0, StatisticsSupport.round1(0.0));
        assertEquals(95.2, StatisticsSupport.round1(95.2499));
    }

    @Test
    void finishRateGuardsAgainstZeroDenominator() {
        assertEquals(50.0, StatisticsSupport.finishRate(1, 2));
        assertEquals(0.0, StatisticsSupport.finishRate(0, 0));
        assertEquals(100.0, StatisticsSupport.finishRate(3, 3));
    }

    @Test
    void buildsSixAscendingMonthsEndingWithCurrent() {
        List<YearMonth> months = StatisticsSupport.recentMonths(6);
        assertEquals(6, months.size());
        assertEquals(YearMonth.now(), months.get(5));
        assertEquals(YearMonth.now().minusMonths(5), months.get(0));
    }

    @Test
    void extractsMonthKeyFromBusinessTimeText() {
        assertEquals("2026-10", StatisticsSupport.monthKeyOf("2026-10-07 09:30:00"));
        assertEquals("", StatisticsSupport.monthKeyOf(null));
        assertEquals("", StatisticsSupport.monthKeyOf("bad"));
        assertEquals("10月", StatisticsSupport.monthLabel(YearMonth.of(2026, 10)));
    }

    @Test
    void parsesDateTimeOnlyForCompleteValues() {
        assertEquals(LocalDateTime.of(2026, 10, 7, 9, 30, 0), StatisticsSupport.parseDateTime("2026-10-07 09:30:00"));
        assertNull(StatisticsSupport.parseDateTime("2026-10-07"));
        assertNull(StatisticsSupport.parseDateTime(null));
    }

    @Test
    void checksMembershipInTargetMonth() {
        assertTrue(StatisticsSupport.withinMonth(LocalDateTime.of(2026, 10, 7, 9, 0), YearMonth.of(2026, 10)));
        assertFalse(StatisticsSupport.withinMonth(LocalDateTime.of(2026, 11, 1, 0, 0), YearMonth.of(2026, 10)));
        assertFalse(StatisticsSupport.withinMonth(null, YearMonth.of(2026, 10)));
    }
}
