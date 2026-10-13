package com.friendlystation.platform.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class FormatUtilsTest {
    @Test
    void formatsDateTimeAndDateWithFixedPattern() {
        assertEquals("2026-10-07 09:30:00", FormatUtils.dateTime(LocalDateTime.of(2026, 10, 7, 9, 30, 0)));
        assertEquals("2026-10-07", FormatUtils.date(LocalDate.of(2026, 10, 7)));
    }

    @Test
    void returnsEmptyStringForNullTime() {
        assertEquals("", FormatUtils.dateTime(null));
        assertEquals("", FormatUtils.date(null));
    }

    @Test
    void parsesAllSupportedDateInputs() {
        assertEquals(LocalDateTime.of(2026, 10, 7, 0, 0), FormatUtils.dateTime("2026-10-07"));
        assertEquals(LocalDateTime.of(2026, 10, 7, 9, 30), FormatUtils.dateTime("2026-10-07 09:30"));
        assertEquals(LocalDateTime.of(2026, 10, 7, 9, 30, 15), FormatUtils.dateTime("2026-10-07T09:30:15"));
    }

    @Test
    void fallsBackToTodayWhenInputIsUnparsable() {
        LocalDateTime fallback = FormatUtils.dateTime("not-a-date");
        assertNotNull(fallback);
        assertTrue(Math.abs(fallback.until(LocalDateTime.now(), java.time.temporal.ChronoUnit.MINUTES)) < 5);
    }

    @Test
    void distinguishesMissingNumberFromZero() {
        assertNull(FormatUtils.nullableNumber(null));
        assertNull(FormatUtils.nullableNumber(""));
        assertNull(FormatUtils.nullableNumber("abc"));
        assertEquals(0L, FormatUtils.number("abc"));
        assertEquals(12L, FormatUtils.number("12"));
    }

    @Test
    void trimsTextAndKeepsFallbackForBlank() {
        assertEquals("值", FormatUtils.text("  值  ", "兜底"));
        assertEquals("兜底", FormatUtils.text("   ", "兜底"));
        assertEquals("兜底", FormatUtils.text(null, "兜底"));
    }

    @Test
    void parsesBooleanFlagsFromMultipleRepresentations() {
        assertTrue(FormatUtils.flag(Boolean.TRUE));
        assertTrue(FormatUtils.flag(1));
        assertTrue(FormatUtils.flag("true"));
        assertFalse(FormatUtils.flag(null));
        assertFalse(FormatUtils.flag(0));
    }
}
