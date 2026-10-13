package com.friendlystation.platform.common;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 驿站营业时间解析与校验。
 *
 * <p>营业时间来源有两种：<b>以高德地图 POI 为准</b>（例如「24小时营业」「09:00-12:00 14:00-18:00」），
 * 高德未提供具体营业时间的驿站才回退到公开来源文本（例如「每日 07:00-18:00」）。
 * 这里把它们统一解析成时间区间，用于判断用户选择的预约时段是否落在营业时间内。</p>
 *
 * <p><b>兜底原则</b>：营业时间缺失或无法解析时，{@link #contains} 返回 {@code true}，
 * 即"判断不了就不拦截"，避免因为数据缺失把正常预约挡掉。</p>
 */
public final class BusinessHours {
    /** 匹配 HH:mm 或 H:mm，允许 - — ~ 至 作为分隔符。 */
    private static final Pattern RANGE_PATTERN = Pattern.compile(
            "(\\d{1,2}[:：]\\d{2})\\s*[-—~至]\\s*(\\d{1,2}[:：]\\d{2})");
    private static final Pattern ALL_DAY_PATTERN = Pattern.compile("24\\s*小时|全天|24H", Pattern.CASE_INSENSITIVE);

    private BusinessHours() {
    }

    /** 一个营业时间区间。 */
    public record TimeRange(LocalTime start, LocalTime end) {
        /** 时段是否完全落在本区间内。 */
        public boolean contains(LocalTime from, LocalTime to) {
            return !from.isBefore(start) && !to.isAfter(end);
        }
    }

    /** 营业时间是否表示全天开放。 */
    public static boolean isAllDay(String openTime) {
        return openTime != null && ALL_DAY_PATTERN.matcher(openTime).find();
    }

    /**
     * 解析营业时间文本。全天开放或无法解析时返回空列表，调用方需结合 {@link #isAllDay} 判断。
     */
    public static List<TimeRange> parse(String openTime) {
        List<TimeRange> ranges = new ArrayList<>();
        if (openTime == null || openTime.isBlank()) {
            return ranges;
        }
        Matcher matcher = RANGE_PATTERN.matcher(openTime);
        while (matcher.find()) {
            LocalTime start = parseTime(matcher.group(1));
            LocalTime end = parseTime(matcher.group(2));
            if (start != null && end != null && start.isBefore(end)) {
                ranges.add(new TimeRange(start, end));
            }
        }
        return ranges;
    }

    /**
     * 判断预约时段是否在营业时间内。
     *
     * @param openTime 营业时间文本，可为空
     * @param slot     预约时段，例如「09:00-11:00」
     * @return 无法判断（营业时间缺失/无法解析）或时段非法时返回 {@code true}，即不拦截
     */
    public static boolean contains(String openTime, String slot) {
        if (openTime == null || openTime.isBlank() || slot == null || slot.isBlank()) {
            return true;
        }
        if (isAllDay(openTime)) {
            return true;
        }
        List<TimeRange> ranges = parse(openTime);
        if (ranges.isEmpty()) {
            // 解析不出区间（例如「开放时间待核验」），无法判断就不拦截。
            return true;
        }
        Matcher matcher = RANGE_PATTERN.matcher(slot);
        if (!matcher.find()) {
            return true;
        }
        LocalTime from = parseTime(matcher.group(1));
        LocalTime to = parseTime(matcher.group(2));
        if (from == null || to == null || !from.isBefore(to)) {
            return true;
        }
        return ranges.stream().anyMatch(range -> range.contains(from, to));
    }

    /** 仅用于提示文案：把营业时间原样回显，无法解析时也给原文。 */
    public static String describe(String openTime) {
        return openTime == null ? "" : openTime.trim();
    }

    private static LocalTime parseTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalTime.parse(value.replace('：', ':').trim().length() == 4
                    ? "0" + value.replace('：', ':').trim()
                    : value.replace('：', ':').trim());
        } catch (Exception exception) {
            return null;
        }
    }
}
