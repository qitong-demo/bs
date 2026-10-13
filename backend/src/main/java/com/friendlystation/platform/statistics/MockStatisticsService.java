package com.friendlystation.platform.statistics;

import com.friendlystation.platform.content.ActivityService;
import com.friendlystation.platform.evaluation.StationEvaluationService;
import com.friendlystation.platform.station.StationService;
import com.friendlystation.platform.workflow.WorkflowService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 非 mysql profile 的统计聚合：基于内存业务数据实时计算，指标口径与 mysql 实现一致。 */
@Service
@Profile("!mysql")
public class MockStatisticsService implements StatisticsService {
    private static final String DATA_SCOPE = "内存演示数据（服务重启后清空）";

    private final WorkflowService workflowService;
    private final StationService stationService;
    private final ActivityService activityService;
    private final StationEvaluationService evaluationService;

    public MockStatisticsService(WorkflowService workflowService,
                                 StationService stationService,
                                 ActivityService activityService,
                                 StationEvaluationService evaluationService) {
        this.workflowService = workflowService;
        this.stationService = stationService;
        this.activityService = activityService;
        this.evaluationService = evaluationService;
    }

    @Override
    public Map<String, Object> overview() {
        LocalDateTime monthStart = LocalDate.now().minusDays(30).atStartOfDay();

        long openStations = stationService.adminPage(1, 1, null, null, null, "OPEN").total();
        long closedStations = stationService.adminPage(1, 1, null, null, null, "CLOSED").total();
        List<Map<String, Object>> appointments = allAppointments();
        List<Map<String, Object>> appeals = allAppeals();
        List<Map<String, Object>> signups = workflowService.allSignups();
        Map<String, Object> evaluationSummary = evaluationService.summary(null);

        long monthlyAppointments = appointments.stream()
                .filter(item -> isAfter(item.get("createTime"), monthStart)).count();
        long monthlyAppeals = appeals.stream()
                .filter(item -> isAfter(item.get("createTime"), monthStart)).count();
        long resolvedAppeals = appeals.stream()
                .filter(item -> "RESOLVED".equals(item.get("status"))).count();
        long pendingAppeals = appeals.stream()
                .filter(item -> !"RESOLVED".equals(item.get("status")) && !"CLOSED".equals(item.get("status"))).count();
        long pendingAppointments = appointments.stream()
                .filter(item -> "PENDING_STATION_CONFIRMATION".equals(item.get("status"))).count();
        long publishedActivities = activityService.adminPage(1, 1, "PUBLISHED", null).total();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stationCount", openStations + closedStations);
        out.put("openStationCount", openStations);
        out.put("closedStationCount", closedStations);
        out.put("appointmentCount", (long) appointments.size());
        out.put("monthlyAppointmentCount", monthlyAppointments);
        out.put("pendingAppointmentCount", pendingAppointments);
        out.put("appealCount", (long) appeals.size());
        out.put("monthlyAppealCount", monthlyAppeals);
        out.put("pendingAppealCount", pendingAppeals);
        out.put("resolvedAppealCount", resolvedAppeals);
        out.put("finishRate", StatisticsSupport.finishRate(resolvedAppeals, appeals.size()));
        out.put("appealRecordCount", appeals.stream()
                .mapToLong(item -> ((Number) item.getOrDefault("recordCount", 0)).longValue()).sum());
        out.put("activityCount", activityService.adminPage(1, 1, null, null).total());
        out.put("publishedActivityCount", publishedActivities);
        out.put("signupCount", (long) signups.size());
        out.put("evaluationCount", ((Number) evaluationSummary.getOrDefault("count", 0)).longValue());
        out.put("averageScore", evaluationSummary.getOrDefault("averageScore", 0.0));
        out.put("dataScope", DATA_SCOPE);
        out.put("dataStatus", "系统计算结果");
        out.put("generatedAt", StatisticsSupport.format(LocalDateTime.now()));
        return out;
    }

    @Override
    public Map<String, Object> monthlyTrend() {
        List<YearMonth> months = StatisticsSupport.recentMonths(6);
        List<Map<String, Object>> appointments = allAppointments();
        List<Map<String, Object>> appeals = allAppeals();
        List<Map<String, Object>> signups = workflowService.allSignups();

        List<String> labels = new ArrayList<>();
        List<Long> appointmentCounts = new ArrayList<>();
        List<Long> appealCounts = new ArrayList<>();
        List<Long> signupCounts = new ArrayList<>();
        List<Double> finishRates = new ArrayList<>();

        for (YearMonth month : months) {
            String key = StatisticsSupport.monthKey(month);
            labels.add(StatisticsSupport.monthLabel(month));

            List<Map<String, Object>> monthAppeals = appeals.stream()
                    .filter(item -> key.equals(StatisticsSupport.monthKeyOf(item.get("createTime")))).toList();
            long resolved = monthAppeals.stream().filter(item -> "RESOLVED".equals(item.get("status"))).count();

            appointmentCounts.add(appointments.stream()
                    .filter(item -> key.equals(StatisticsSupport.monthKeyOf(item.get("createTime")))).count());
            appealCounts.add((long) monthAppeals.size());
            signupCounts.add(signups.stream()
                    .filter(item -> key.equals(StatisticsSupport.monthKeyOf(item.get("createTime")))).count());
            finishRates.add(StatisticsSupport.finishRate(resolved, monthAppeals.size()));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("months", labels);
        out.put("appointments", appointmentCounts);
        out.put("appeals", appealCounts);
        out.put("signups", signupCounts);
        out.put("finishRate", finishRates);
        out.put("dataScope", DATA_SCOPE);
        out.put("dataNote", "按各业务记录的创建时间统计自然月数据。");
        out.put("dataStatus", "系统计算结果");
        out.put("generatedAt", StatisticsSupport.format(LocalDateTime.now()));
        return out;
    }

    /**
     * 排行：按**驿站**统计联系申请数量（与 mysql 实现口径一致）。
     * 预约事项已改为用户自由填写，按服务项目排行不再成立。
     */
    @Override
    public List<Map<String, Object>> serviceRanking() {
        List<Map<String, Object>> appointments = allAppointments();
        Map<String, Long> grouped = appointments.stream()
                .map(item -> String.valueOf(item.getOrDefault("stationName", "")))
                .filter(name -> !name.isBlank() && !"null".equals(name))
                .collect(Collectors.groupingBy(name -> name, Collectors.counting()));
        if (grouped.isEmpty()) {
            return List.of();
        }
        long max = grouped.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        return grouped.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", entry.getKey());
                    row.put("count", entry.getValue());
                    row.put("percent", max <= 0 ? 0 : Math.round(entry.getValue() * 100.0 / max));
                    return row;
                })
                .sorted(Comparator.comparingLong((Map<String, Object> row) -> (Long) row.get("count")).reversed())
                .limit(10)
                .toList();
    }

    private List<Map<String, Object>> allAppointments() {
        return workflowService.appointmentPage(1, StatisticsSupport.AGGREGATE_LIMIT, null, null, null).records();
    }

    private List<Map<String, Object>> allAppeals() {
        return workflowService.appealPage(1, StatisticsSupport.AGGREGATE_LIMIT, null, null, null).records();
    }

    private static boolean isAfter(Object createTime, LocalDateTime threshold) {
        LocalDateTime value = StatisticsSupport.parseDateTime(createTime);
        return value != null && value.isAfter(threshold);
    }
}
