package com.friendlystation.platform.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 统一的取值、类型转换与时间格式化工具，供内存实现与 MySQL 实现共用。 */
public final class FormatUtils {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private FormatUtils() {
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }

    public static String date(LocalDate value) {
        return value == null ? "" : value.format(DATE);
    }

    /** 取字符串，空值或空白时返回兜底值。 */
    public static String text(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    public static long number(Object value) {
        Long parsed = nullableNumber(value);
        return parsed == null ? 0L : parsed;
    }

    /** 取数字，无法解析时返回 null，便于区分「未传」和「传了 0」。 */
    public static Long nullableNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean flag(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && "true".equalsIgnoreCase(value.toString().trim());
    }

    /** 解析日期时间，兼容 `yyyy-MM-dd`、`yyyy-MM-dd HH:mm` 与 `yyyy-MM-dd HH:mm:ss` 三种写法。 */
    public static LocalDateTime dateTime(Object value) {
        if (value == null) {
            return LocalDateTime.now();
        }
        String raw = value.toString().trim().replace('T', ' ');
        try {
            if (raw.length() == 10) {
                return LocalDate.parse(raw, DATE).atStartOfDay();
            }
            if (raw.length() == 16) {
                return LocalDateTime.parse(raw, MINUTE);
            }
            return LocalDateTime.parse(raw.substring(0, 19), DATE_TIME);
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    public static LocalDate date(Object value) {
        if (value == null) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value.toString().trim().substring(0, 10), DATE);
        } catch (Exception ignored) {
            return LocalDate.now();
        }
    }
}
