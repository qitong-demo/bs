package com.friendlystation.platform.content;

import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.station.StationDto;
import com.friendlystation.platform.station.StationService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 非 mysql Profile 下的内存活动实现，用于本地演示与前端联调。
 * 驿站名称取自内存驿站种子数据（与 MockStationService 的 1~5 号驿站保持一致）。
 */
@Service
@Profile("!mysql")
public class MockActivityService implements ActivityService {
    private final AtomicLong idGenerator = new AtomicLong(3);
    private final List<Map<String, Object>> store = new CopyOnWriteArrayList<>();
    /** 活动报名人数，内存实现维护简单计数，默认 0。 */
    private final Map<Long, Integer> signedCounts = new ConcurrentHashMap<>();
    private final StationService stationService;

    public MockActivityService(StationService stationService) {
        this.stationService = stationService;
        LocalDateTime now = LocalDateTime.now();
        store.add(record(1L, "夏季送清凉公益活动", 1L, "西湖区文新街道友好驿站",
                LocalDateTime.of(2026, 6, 15, 9, 0, 0),
                "西湖区文新街道友好驿站", 100,
                "为新就业群体提供防暑物资和健康咨询。", "PUBLISHED", now.minusDays(2)));
        store.add(record(2L, "新就业群体法律咨询专场", 2L, "蒋村街道综合服务驿站",
                LocalDateTime.of(2026, 6, 20, 14, 0, 0),
                "蒋村街道综合服务驿站", 50,
                "提供劳动权益和职业保障方面的公益咨询。", "PUBLISHED", now.minusDays(5)));
        store.add(record(3L, "友好驿站健康义诊服务日", 3L, "古荡街道党群服务中心驿站",
                LocalDateTime.of(2026, 6, 28, 9, 30, 0),
                "古荡街道服务中心一楼", 30,
                "邀请社区卫生服务中心为骑手、快递员提供基础健康检查。", "PUBLISHED", now.minusDays(8)));
    }

    @Override
    public PageResult<Map<String, Object>> publicPage(long current, long size, Long stationId, String keyword,
                                                     String cityCode) {
        return page(current, size, stationId, keyword, "PUBLISHED", cityCode);
    }

    @Override
    public PageResult<Map<String, Object>> adminPage(long current, long size, String status, String keyword) {
        // 管理端不做城市过滤
        return page(current, size, null, keyword, status, null);
    }

    @Override
    public Map<String, Object> detail(Long id) {
        return view(require(id));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> create(Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : (Map<String, Object>) payload;
        String title = requireTitle(data.get("title"));
        Long stationId = ContentSupport.toNullableLong(data.get("stationId"));
        String stationName = requireStation(stationId);
        int maxCount = requireMaxCount(data.get("maxCount"));
        LocalDateTime activityTime = ContentSupport.parseDateTime(ContentSupport.trimText(data.get("activityTime")));
        if (activityTime == null) {
            activityTime = LocalDateTime.now();
        }
        long id = idGenerator.incrementAndGet();
        Map<String, Object> record = record(id, title, stationId, stationName, activityTime,
                ContentSupport.trimText(data.get("address")), maxCount,
                ContentSupport.trimText(data.get("content")),
                ContentSupport.textOrDefault(data.get("status"), "PUBLISHED"),
                LocalDateTime.now());
        applySourceFields(record, data);
        store.add(record);
        signedCounts.put(id, 0);
        return view(record);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> update(Long id, Map<String, Object> payload) {
        Map<String, Object> record = require(id);
        Map<String, Object> data = payload == null ? Map.of() : (Map<String, Object>) payload;
        if (data.containsKey("title")) {
            record.put("title", requireTitle(data.get("title")));
        }
        if (data.containsKey("stationId")) {
            Long stationId = ContentSupport.toNullableLong(data.get("stationId"));
            record.put("stationId", stationId);
            record.put("stationName", requireStation(stationId));
        }
        if (data.containsKey("activityTime")) {
            LocalDateTime activityTime = ContentSupport.parseDateTime(ContentSupport.trimText(data.get("activityTime")));
            if (activityTime != null) {
                record.put("activityTime", activityTime);
            }
        }
        if (data.containsKey("address")) {
            record.put("address", ContentSupport.trimText(data.get("address")));
        }
        if (data.containsKey("maxCount")) {
            record.put("maxCount", requireMaxCount(data.get("maxCount")));
        }
        if (data.containsKey("content")) {
            record.put("content", ContentSupport.trimText(data.get("content")));
        }
        if (data.containsKey("status")) {
            record.put("status", ContentSupport.textOrDefault(data.get("status"), "PUBLISHED"));
        }
        return view(record);
    }

    @Override
    public void delete(Long id) {
        Map<String, Object> record = require(id);
        store.remove(record);
        signedCounts.remove(id);
    }

    @Override
    public int remainingSlots(Long activityId) {
        Map<String, Object> record = require(activityId);
        return remaining(record);
    }

    private PageResult<Map<String, Object>> page(long current, long size, Long stationId, String keyword,
                                                String status, String cityCode) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = ContentSupport.trimText(keyword);
        String normalizedStatus = ContentSupport.trimText(status);
        String normalizedCity = ContentSupport.trimText(cityCode);
        List<Map<String, Object>> filtered = store.stream()
                .filter(item -> normalizedStatus.isEmpty() || normalizedStatus.equals(item.get("status")))
                .filter(item -> stationId == null || stationId.equals(item.get("stationId")))
                // 城市口径与 mysql 一致：只看「本市 + 全国性」
                .filter(item -> normalizedCity.isEmpty()
                        || normalizedCity.equals(item.get("cityCode"))
                        || ContentSupport.trimText(item.get("cityCode")).isEmpty())
                .filter(item -> normalizedKeyword.isEmpty()
                        || String.valueOf(item.get("title")).contains(normalizedKeyword)
                        || String.valueOf(item.get("address")).contains(normalizedKeyword)
                        || String.valueOf(item.get("content")).contains(normalizedKeyword))
                .sorted((left, right) -> {
                    // 本市优先，其次按活动时间倒序——与 mysql 的 ORDER BY 同口径
                    if (!normalizedCity.isEmpty()) {
                        boolean leftLocal = normalizedCity.equals(left.get("cityCode"));
                        boolean rightLocal = normalizedCity.equals(right.get("cityCode"));
                        if (leftLocal != rightLocal) {
                            return leftLocal ? -1 : 1;
                        }
                    }
                    LocalDateTime leftTime = (LocalDateTime) left.get("activityTime");
                    LocalDateTime rightTime = (LocalDateTime) right.get("activityTime");
                    return rightTime.compareTo(leftTime);
                })
                .toList();
        int from = (int) Math.min((safeCurrent - 1) * safeSize, filtered.size());
        int to = (int) Math.min(from + safeSize, filtered.size());
        List<Map<String, Object>> records = filtered.subList(from, to).stream().map(this::view).toList();
        return new PageResult<>(safeCurrent, safeSize, filtered.size(), records);
    }

    /**
     * 写入来源相关字段，与 mysql 实现同口径。
     *
     * <p>内存实现里字段存在 Map 上，缺键即为"未填写"。</p>
     */
    private static void applySourceFields(Map<String, Object> record, Map<String, Object> data) {
        putIfPresent(record, data, "externalUrl", 500, "原文链接");
        putIfPresent(record, data, "publisher", 120, "主办方");
        putIfPresent(record, data, "category", 30, "活动分类");
        putIfPresent(record, data, "summary", 500, "摘要");
        if (data.containsKey("cityCode")) {
            String code = ContentSupport.trimText(data.get("cityCode"));
            if (!code.isEmpty() && !code.matches("\\d{6}")) {
                throw new BusinessException("城市编码应为 6 位数字");
            }
            record.put("cityCode", code);
        }
        if (data.containsKey("sourceLevel")) {
            String level = ContentSupport.trimText(data.get("sourceLevel")).toUpperCase();
            if (!level.isEmpty() && !java.util.Set.of("GOV", "MEDIA", "PLATFORM").contains(level)) {
                throw new BusinessException("来源等级只能是 GOV、MEDIA 或留空（驿站自办）");
            }
            record.put("sourceLevel", level);
        }
    }

    private static void putIfPresent(Map<String, Object> record, Map<String, Object> data,
                                     String key, int max, String label) {
        if (!data.containsKey(key)) {
            return;
        }
        String text = ContentSupport.trimText(data.get(key));
        if (text.length() > max) {
            throw new BusinessException(label + "不能超过 " + max + " 个字符");
        }
        record.put(key, text);
    }

    private Map<String, Object> require(Long id) {
        if (id == null) {
            throw new BusinessException("活动不存在");
        }
        return store.stream()
                .filter(item -> id.equals(item.get("id")))
                .findFirst()
                .orElseThrow(() -> new BusinessException("活动不存在"));
    }

    private static String requireTitle(Object value) {
        String title = ContentSupport.trimText(value);
        if (title.isEmpty()) {
            throw new BusinessException("活动标题不能为空");
        }
        return title;
    }

    private static int requireMaxCount(Object value) {
        long maxCount = ContentSupport.toLong(value, 50L);
        if (maxCount <= 0) {
            throw new BusinessException("活动名额必须大于 0");
        }
        return (int) Math.min(maxCount, Integer.MAX_VALUE);
    }

    /** 校验驿站是否存在，返回驿站名称；stationId 为空时按未指定驿站处理。 */
    private String requireStation(Long stationId) {
        if (stationId == null) {
            return "";
        }
        StationDto station;
        try {
            station = stationService.detail(stationId);
        } catch (BusinessException exception) {
            throw new BusinessException("驿站不存在");
        }
        return station == null || station.stationName() == null ? "" : station.stationName();
    }

    private int remaining(Map<String, Object> record) {
        int maxCount = ((Number) record.get("maxCount")).intValue();
        int signedCount = signedCounts.getOrDefault(((Number) record.get("id")).longValue(), 0);
        return Math.max(maxCount - signedCount, 0);
    }

    private static Map<String, Object> record(Long id, String title, Long stationId, String stationName,
                                             LocalDateTime activityTime, String address, int maxCount,
                                             String content, String status, LocalDateTime createTime) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("title", title);
        record.put("stationId", stationId);
        record.put("stationName", stationName);
        record.put("activityTime", activityTime);
        record.put("address", address);
        record.put("maxCount", maxCount);
        record.put("content", content);
        record.put("status", status);
        record.put("createTime", createTime);
        return record;
    }

    /** 转换为对外固定字段结构。 */
    private Map<String, Object> view(Map<String, Object> record) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", record.get("id"));
        out.put("title", record.get("title"));
        out.put("stationId", record.get("stationId") == null ? 0L : record.get("stationId"));
        out.put("stationName", record.get("stationName") == null ? "" : record.get("stationName"));
        out.put("activityTime", ContentSupport.formatDateTime((LocalDateTime) record.get("activityTime")));
        out.put("address", record.get("address") == null ? "" : record.get("address"));
        out.put("maxCount", record.get("maxCount"));
        int signedCount = signedCounts.getOrDefault(((Number) record.get("id")).longValue(), 0);
        out.put("signedCount", signedCount);
        out.put("remaining", remaining(record));
        out.put("content", record.get("content") == null ? "" : record.get("content"));
        out.put("status", record.get("status"));
        out.put("statusLabel", ContentSupport.statusLabel((String) record.get("status")));
        out.put("createTime", ContentSupport.formatDateTime((LocalDateTime) record.get("createTime")));

        // 以下字段 mysql 实现一直会返回，而这里原先缺失——两套 profile 的 JSON 不一致，
        // 会让"内存模式与 MySQL 模式行为相同"这个约定失效。本次一并补齐。
        String organizerType = record.get("organizerType") == null
                ? "STATION" : String.valueOf(record.get("organizerType"));
        out.put("organizerType", organizerType);
        out.put("organizerLabel", organizerTypeLabel(organizerType));
        out.put("externalUrl", record.get("externalUrl") == null ? "" : String.valueOf(record.get("externalUrl")));

        // 采集来的真实活动：来源必须可核对，字段缺失时返回空串而不是省略
        out.put("cityCode", record.get("cityCode") == null ? "" : String.valueOf(record.get("cityCode")));
        out.put("publisher", record.get("publisher") == null ? "" : String.valueOf(record.get("publisher")));
        out.put("category", record.get("category") == null ? "" : String.valueOf(record.get("category")));
        out.put("summary", record.get("summary") == null ? "" : String.valueOf(record.get("summary")));
        String sourceLevel = record.get("sourceLevel") == null ? "" : String.valueOf(record.get("sourceLevel"));
        out.put("sourceLevel", sourceLevel);
        out.put("sourceLevelLabel", sourceLevelLabel(sourceLevel));
        return out;
    }

    /** 承办方中文文案。第三方活动只提供跳转、不接受站内报名，文案上要讲清楚。 */
    private static String organizerTypeLabel(String organizerType) {
        return switch (organizerType == null ? "" : organizerType.trim().toUpperCase()) {
            case "EXTERNAL" -> "第三方机构";
            case "PLATFORM" -> "平台";
            default -> "驿站";
        };
    }

    /** 来源等级中文文案，与 mysql 实现同口径。 */
    private static String sourceLevelLabel(String level) {
        if (level == null || level.isBlank() || "null".equals(level)) {
            return "驿站自办";
        }
        return switch (level.trim().toUpperCase()) {
            case "GOV" -> "政府群团";
            case "MEDIA" -> "官方媒体";
            case "PLATFORM" -> "平台自办";
            default -> "驿站自办";
        };
    }
}
