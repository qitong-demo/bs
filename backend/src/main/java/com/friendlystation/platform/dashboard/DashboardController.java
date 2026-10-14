package com.friendlystation.platform.dashboard;

import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.statistics.CsvExport;
import com.friendlystation.platform.statistics.CsvExportBuilder;
import com.friendlystation.platform.statistics.StatisticsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 统计分析与报表导出。指标全部来自业务表实时聚合，不再返回固定演示值。 */
@RestController
@RequestMapping("/api/stat")
public class DashboardController {
    private final StatisticsService statisticsService;
    private final CsvExportBuilder csvExportBuilder;

    public DashboardController(StatisticsService statisticsService, CsvExportBuilder csvExportBuilder) {
        this.statisticsService = statisticsService;
        this.csvExportBuilder = csvExportBuilder;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(statisticsService.overview());
    }

    @GetMapping("/appeal/month")
    public ApiResponse<Map<String, Object>> monthlyTrend() {
        return ApiResponse.ok(statisticsService.monthlyTrend());
    }

    @GetMapping("/service/ranking")
    public ApiResponse<List<Map<String, Object>>> serviceRanking() {
        return ApiResponse.ok(statisticsService.serviceRanking());
    }

    /** 报表导出：appeals、appointments、stations、signups、evaluations。 */
    @GetMapping("/export/{type}")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ResponseEntity<byte[]> export(@PathVariable String type) {
        CsvExport export = csvExportBuilder.build(type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + export.fileName() + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(export.content().getBytes(StandardCharsets.UTF_8));
    }
}
