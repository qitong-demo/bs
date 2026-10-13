package com.friendlystation.platform.evaluation;

import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.station.MockStationService;
import com.friendlystation.platform.station.StationDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存版驿站评价服务：不依赖数据库，供 dev / default Profile 使用。
 * 对外返回字段与 {@link MysqlStationEvaluationService} 完全一致。
 */
@Service
@Profile("!mysql")
public class MockStationEvaluationService implements StationEvaluationService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 内置用户 id 与姓名的对应关系，其他用户使用「用户{id}」 */
    private static final Map<Long, String> KNOWN_USERS = Map.of(
            1L, "骑手小王",
            2L, "陈管理员",
            3L, "平台管理员"
    );

    private final AtomicLong idGenerator = new AtomicLong(6);
    private final MockStationService stationService;
    private final List<Evaluation> evaluations = new CopyOnWriteArrayList<>();

    public MockStationEvaluationService(MockStationService stationService) {
        this.stationService = stationService;
        // 预置几条演示数据，便于前端直接看到列表与统计效果
        this.evaluations.add(new Evaluation(1L, 1L, 1L, 5, 5, 5, 5, "休息区很干净，充电位也多，跑单间隙来歇脚很方便。", LocalDateTime.now().minusDays(3)));
        this.evaluations.add(new Evaluation(2L, 2L, 1L, 4, 4, 4, 4, "热水和热饭设备好用，就是高峰期座位有点紧张。", LocalDateTime.now().minusDays(2)));
        this.evaluations.add(new Evaluation(3L, 3L, 2L, 4, 5, 4, 4, "环境安静，管理员服务态度好。", LocalDateTime.now().minusDays(1)));
        this.evaluations.add(new Evaluation(4L, 1L, 3L, 5, 5, 4, 5, "驿站设施齐全，整体体验不错。", LocalDateTime.now().minusHours(6)));
        this.evaluations.add(new Evaluation(5L, 4L, 2L, 3, 3, 4, 3, "位置稍远，但基本服务都有。", LocalDateTime.now().minusHours(3)));
        this.evaluations.add(new Evaluation(6L, 2L, 3L, 5, 5, 5, 5, "淋浴和休息区都很好，推荐。", LocalDateTime.now().minusHours(1)));
    }

    @Override
    public Map<String, Object> create(Map<String, Object> payload, long userId) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        long stationId = requireStationId(safePayload.get("stationId"));
        int score = readScore(safePayload.get("score"), 0, "综合评分必须在 1 到 5 之间");
        int serviceScore = readScore(safePayload.get("serviceScore"), score, "服务评分必须在 1 到 5 之间");
        int environmentScore = readScore(safePayload.get("environmentScore"), score, "环境评分必须在 1 到 5 之间");
        int facilityScore = readScore(safePayload.get("facilityScore"), score, "设施评分必须在 1 到 5 之间");
        String content = text(safePayload.get("content"));
        // 驿站必须存在
        stationService.detail(stationId);

        Evaluation evaluation = new Evaluation(
                idGenerator.incrementAndGet(),
                stationId,
                userId,
                score,
                serviceScore,
                environmentScore,
                facilityScore,
                content,
                LocalDateTime.now()
        );
        evaluations.add(evaluation);

        Map<String, Object> data = toMap(evaluation);
        data.put("status", "SAVED");
        data.put("dataStatus", "系统计算结果");
        return data;
    }

    @Override
    public PageResult<Map<String, Object>> page(long current, long size, Long stationId, Long userId) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        List<Evaluation> filtered = evaluations.stream()
                .filter(item -> stationId == null || stationId.equals(item.stationId()))
                .filter(item -> userId == null || userId.equals(item.userId()))
                .sorted(Comparator.comparing(Evaluation::createTime, Comparator.reverseOrder())
                        .thenComparing(Evaluation::id, Comparator.reverseOrder()))
                .toList();
        int from = (int) Math.min((safeCurrent - 1) * safeSize, filtered.size());
        int to = (int) Math.min(from + safeSize, filtered.size());
        List<Map<String, Object>> records = new ArrayList<>();
        for (Evaluation item : filtered.subList(from, to)) {
            records.add(toMap(item));
        }
        return new PageResult<>(safeCurrent, safeSize, filtered.size(), records);
    }

    @Override
    public Map<String, Object> summary(Long stationId) {
        List<Integer> totalScores = new ArrayList<>();
        List<Integer> serviceScores = new ArrayList<>();
        List<Integer> environmentScores = new ArrayList<>();
        List<Integer> facilityScores = new ArrayList<>();
        for (Evaluation item : evaluations) {
            if (stationId != null && !stationId.equals(item.stationId())) {
                continue;
            }
            totalScores.add(item.score());
            serviceScores.add(item.serviceScore());
            environmentScores.add(item.environmentScore());
            facilityScores.add(item.facilityScore());
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("stationId", stationId);
        summary.put("count", totalScores.size());
        summary.put("averageScore", average(totalScores));
        summary.put("serviceAverage", average(serviceScores));
        summary.put("environmentAverage", average(environmentScores));
        summary.put("facilityAverage", average(facilityScores));
        return summary;
    }

    @Override
    public List<Map<String, Object>> myEvaluations(long userId) {
        List<Map<String, Object>> records = new ArrayList<>();
        evaluations.stream()
                .filter(item -> item.userId() != null && item.userId() == userId)
                .sorted(Comparator.comparing(Evaluation::createTime, Comparator.reverseOrder())
                        .thenComparing(Evaluation::id, Comparator.reverseOrder()))
                .forEach(item -> records.add(toMap(item)));
        return records;
    }

    /** 组装固定字段的评价 Map */
    private Map<String, Object> toMap(Evaluation item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", item.id());
        data.put("stationId", item.stationId());
        data.put("stationName", stationName(item.stationId()));
        data.put("userId", item.userId());
        data.put("userName", userName(item.userId()));
        data.put("score", item.score());
        data.put("serviceScore", item.serviceScore());
        data.put("environmentScore", item.environmentScore());
        data.put("facilityScore", item.facilityScore());
        data.put("content", item.content());
        data.put("createTime", item.createTime().format(TIME_FORMATTER));
        return data;
    }

    /** 通过内存版驿站服务查询驿站名称，查询不到时返回空串 */
    private String stationName(Long stationId) {
        if (stationId == null) {
            return "";
        }
        try {
            StationDto station = stationService.detail(stationId);
            return station.stationName() == null ? "" : station.stationName();
        } catch (BusinessException ignored) {
            return "";
        }
    }

    /** 内置用户返回真实姓名，其他用户返回「用户{id}」 */
    private String userName(Long userId) {
        if (userId == null) {
            return "";
        }
        return KNOWN_USERS.getOrDefault(userId, "用户" + userId);
    }

    private long requireStationId(Object value) {
        long stationId = toLong(value);
        if (stationId <= 0) {
            throw new BusinessException("评价驿站不能为空");
        }
        return stationId;
    }

    private int readScore(Object value, int fallback, String message) {
        Object raw = value == null ? fallback : value;
        int score = (int) toLong(raw);
        if (score < 1 || score > 5) {
            throw new BusinessException(message);
        }
        return score;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return "null".equals(text) ? "" : text;
    }

    /** 平均值保留一位小数，无数据时为 0.0 */
    private double average(List<Integer> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        int total = 0;
        for (Integer value : values) {
            total += value == null ? 0 : value;
        }
        return Math.round((double) total / values.size() * 10) / 10.0;
    }

    /** 内存评价记录 */
    private record Evaluation(
            Long id,
            Long stationId,
            Long userId,
            Integer score,
            Integer serviceScore,
            Integer environmentScore,
            Integer facilityScore,
            String content,
            LocalDateTime createTime
    ) {
    }
}
