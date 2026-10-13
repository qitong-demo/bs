package com.friendlystation.platform.statistics;

import java.util.List;

/** CSV 组装工具：统一加 UTF-8 BOM，便于 Excel 直接打开中文内容。 */
public final class CsvSupport {
    private CsvSupport() {
    }

    public static String toCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append(join(headers)).append("\r\n");
        for (List<String> row : rows) {
            builder.append(join(row)).append("\r\n");
        }
        return builder.toString();
    }

    private static String join(List<String> values) {
        return values.stream().map(CsvSupport::escape).reduce((left, right) -> left + "," + right).orElse("");
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
