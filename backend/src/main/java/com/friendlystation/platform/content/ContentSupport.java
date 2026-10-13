package com.friendlystation.platform.content;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 资讯、公告、活动三个内容服务的公共工具方法。
 * 内存实现与 MySQL 实现共用，保证对外输出格式完全一致。
 */
final class ContentSupport {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ContentSupport() {
    }

    /** 判断字符串是否为空或空白。 */
    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 读取文本字段并去除首尾空白，空白时返回空串。 */
    static String trimText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /** 读取文本字段，空白时返回默认值。 */
    static String textOrDefault(Object value, String fallback) {
        String text = trimText(value);
        return text.isEmpty() ? fallback : text;
    }

    /** 状态中文文案：PUBLISHED→已发布，DRAFT→草稿，其他原样返回。 */
    static String statusLabel(String status) {
        if ("PUBLISHED".equals(status)) {
            return "已发布";
        }
        if ("DRAFT".equals(status)) {
            return "草稿";
        }
        return status == null ? "" : status;
    }

    /**
     * 资讯来源等级的中文文案。
     *
     * <p>同样的政策内容，出自国务院文件还是商业媒体转述，可信度差别很大；
     * 界面上必须能区分，不能让用户以为每条都是官方一手文件。</p>
     */
    static String sourceLevelLabel(String level) {
        if ("AUTHORITATIVE".equalsIgnoreCase(level)) {
            return "官方文件";
        }
        if ("OFFICIAL_MEDIA".equalsIgnoreCase(level)) {
            return "官方媒体";
        }
        return "第三方来源";
    }

    /** 日期格式化为 yyyy-MM-dd，空值返回空串。 */
    static String formatDate(LocalDateTime time) {
        return time == null ? "" : time.format(DATE_FORMATTER);
    }

    /** 时间格式化为 yyyy-MM-dd HH:mm:ss，空值返回空串。 */
    static String formatDateTime(LocalDateTime time) {
        return time == null ? "" : time.format(DATE_TIME_FORMATTER);
    }

    /** 解析前端传入的整数字段，无法解析时返回默认值。 */
    static long toLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(trimText(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /** 解析前端传入的 Long 字段，空值或非法值返回 null。 */
    static Long toNullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = trimText(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 解析 yyyy-MM-dd HH:mm:ss 时间，兼容 ISO 的 T 分隔写法，无法解析返回 null。 */
    static LocalDateTime parseDateTime(String value) {
        String text = trimText(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, DATE_TIME_FORMATTER);
        } catch (Exception ignored) {
            // 继续尝试其它常见写法
        }
        try {
            return LocalDateTime.parse(text);
        } catch (Exception ignored) {
            // 继续尝试仅日期写法
        }
        try {
            return LocalDate.parse(text.substring(0, Math.min(10, text.length()))).atStartOfDay();
        } catch (Exception ignored) {
            return null;
        }
    }
}
