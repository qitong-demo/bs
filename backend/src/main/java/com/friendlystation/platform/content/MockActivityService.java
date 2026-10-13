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
    public PageResult<Map<String, Object>> publicPage(long current, long size, Long stationId, String keyword) {
        return page(current, size, stationId, keyword, "PUBLISHED");
    }

    @Override
    public PageResult<Map<String, Object>> adminPage(long current, long size, String status, String keyword) {
        return page(current, size, null, keyword, status);
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

    private PageResult<Map<String, Object>> page(long current, long size, Long stationId, String keyword, String status) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = ContentSupport.trimText(keyword);
        String normalizedStatus = ContentSupport.trimText(status);
        List<Map<String, Object>> filtered = store.stream()
                .filter(item -> normalizedStatus.isEmpty() || normalizedStatus.equals(item.get("status")))
                .filter(item -> stationId == null || stationId.equals(item.get("stationId")))
                .filter(item -> normalizedKeyword.isEmpty()
                        || String.valueOf(item.get("title")).contains(normalizedKeyword)
                        || String.valueOf(item.get("address")).contains(normalizedKeyword)
                        || String.valueOf(item.get("content")).contains(normalizedKeyword))
                .sorted(Comparator.comparing((Map<String, Object> item) -> (LocalDateTime) item.get("activityTime")).reversed())
                .toList();
        int from = (int) Math.min((safeCurrent - 1) * safeSize, filtered.size());
        int to = (int) Math.min(from + safeSize, filtered.size());
        List<Map<String, Object>> records = filtered.subList(from, to).stream().map(this::view).toList();
        return new PageResult<>(safeCurrent, safeSize, filtered.size(), records);
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
        return out;
    }
}
