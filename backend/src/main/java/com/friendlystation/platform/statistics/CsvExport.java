package com.friendlystation.platform.statistics;

/** 一次导出的结果：文件名与 UTF-8 BOM 开头的 CSV 正文。 */
public record CsvExport(String fileName, String content) {
}
