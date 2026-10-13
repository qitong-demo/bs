package com.friendlystation.platform.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 营业时间解析与预约时段校验测试。
 *
 * <p>核心原则：营业时间缺失或无法解析时**不拦截**，避免因为数据缺失把正常预约挡掉。</p>
 */
class BusinessHoursTest {

    @Test
    void parsesSingleRange() {
        List<BusinessHours.TimeRange> ranges = BusinessHours.parse("每日 07:00-18:00");

        assertEquals(1, ranges.size());
        assertEquals("07:00", ranges.get(0).start().toString());
        assertEquals("18:00", ranges.get(0).end().toString());
    }

    @Test
    void parsesMultipleRangesAndVariousSeparators() {
        List<BusinessHours.TimeRange> ranges = BusinessHours.parse("09:00-12:00 14:00—18:00");

        assertEquals(2, ranges.size());
        assertEquals("09:00", ranges.get(0).start().toString());
        assertEquals("14:00", ranges.get(1).start().toString());
    }

    @Test
    void parsesFullWidthColon() {
        List<BusinessHours.TimeRange> ranges = BusinessHours.parse("8：30-17：30");

        assertEquals(1, ranges.size());
        assertEquals("08:30", ranges.get(0).start().toString());
        assertEquals("17:30", ranges.get(0).end().toString());
    }

    @Test
    void detectsAllDay() {
        assertTrue(BusinessHours.isAllDay("24小时营业"));
        assertTrue(BusinessHours.isAllDay("24 小时开放"));
        assertTrue(BusinessHours.isAllDay("全天"));
        assertFalse(BusinessHours.isAllDay("09:00-17:00"));
    }

    @Test
    void slotInsideRangeIsAccepted() {
        assertTrue(BusinessHours.contains("08:30-17:30", "09:00-11:00"));
        assertTrue(BusinessHours.contains("每日 07:00-18:00", "07:00-18:00"));
    }

    @Test
    void slotOutsideRangeIsRejected() {
        assertFalse(BusinessHours.contains("08:30-17:30", "19:00-21:00"));
        assertFalse(BusinessHours.contains("08:30-17:30", "08:00-10:00"));
        // 跨越中午休息时段：不在任何单个区间内
        assertFalse(BusinessHours.contains("09:00-12:00 14:00-18:00", "11:00-15:00"));
    }

    @Test
    void slotInSecondRangeIsAccepted() {
        assertTrue(BusinessHours.contains("09:00-12:00 14:00-18:00", "15:00-17:00"));
    }

    @Test
    void allDayAcceptsAnySlot() {
        assertTrue(BusinessHours.contains("24小时营业", "23:00-23:59"));
    }

    @Test
    void missingOrUnparsableOpenTimeDoesNotBlock() {
        assertTrue(BusinessHours.contains(null, "09:00-11:00"), "营业时间缺失时不应拦截");
        assertTrue(BusinessHours.contains("", "09:00-11:00"));
        assertTrue(BusinessHours.contains("开放时间待核验", "09:00-11:00"), "无法解析时不应拦截");
        assertTrue(BusinessHours.contains("08:30-17:30", null), "时段缺失时交给其他校验处理");
    }

    @Test
    void invalidSlotDoesNotBlock() {
        assertTrue(BusinessHours.contains("08:30-17:30", "待确认"));
        assertTrue(BusinessHours.contains("08:30-17:30", "10:00-09:00"), "起止颠倒的时段不在此处拦截");
    }

    @Test
    void describeEchoesOriginalText() {
        assertEquals("每日 07:00-18:00", BusinessHours.describe("每日 07:00-18:00"));
        assertEquals("", BusinessHours.describe(null));
    }
}
