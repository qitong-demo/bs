package com.friendlystation.platform.statistics;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 统计口径共用的计算与时间工具，保证内存实现与 mysql 实现输出一致。 */
public final class StatisticsSupport {
    /** 一次性聚合的上限，避免单次请求拉取过多记录。 */
    public static final int AGGREGATE_LIMIT = 20000;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private StatisticsSupport() {
    }

    public static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /** 办结率百分比，分母为 0 时返回 0。 */
    public static double finishRate(long resolved, long total) {
        return total <= 0 ? 0.0 : round1(resolved * 100.0 / total);
    }

    /** 最近 6 个自然月（含当月），按时间升序。 */
    public static List<YearMonth> recentMonths(int count) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth start = YearMonth.now().minusMonths(count - 1L);
        for (int index = 0; index < count; index++) {
            months.add(start.plusMonths(index));
        }
        return months;
    }

    public static String monthLabel(YearMonth month) {
        return month.getMonthValue() + "月";
    }

    public static String monthKey(YearMonth month) {
        return month.toString();
    }

    /** 从 `yyyy-MM-dd HH:mm:ss` 文本取 `yyyy-MM`，无法解析时返回空串。 */
    public static String monthKeyOf(Object createTime) {
        String text = createTime == null ? "" : String.valueOf(createTime);
        return text.length() >= 7 ? text.substring(0, 7) : "";
    }

    public static boolean withinMonth(LocalDateTime value, YearMonth month) {
        return value != null && YearMonth.from(value).equals(month);
    }

    public static String format(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }

    /** 解析业务服务返回的 `yyyy-MM-dd HH:mm:ss` 文本，无法解析时返回 null。 */
    public static LocalDateTime parseDateTime(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim().replace('T', ' ');
        if (text.length() < 19) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.substring(0, 19), DATE_TIME);
        } catch (Exception ignored) {
            return null;
        }
    }
}
