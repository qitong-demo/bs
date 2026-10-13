package com.friendlystation.platform.content;

import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.PageResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** 非 mysql Profile 下的内存资讯实现，用于本地演示与前端联调。 */
@Service
@Profile("!mysql")
public class MockNewsService implements NewsService {
    private final AtomicLong idGenerator = new AtomicLong(2);
    private final List<Map<String, Object>> store = new CopyOnWriteArrayList<>();

    public MockNewsService() {
        store.add(record(1L, "杭州市支持新就业群体发展若干措施（2024年版）", "政策",
                "围绕新就业群体在就业服务、职业保障、驿站服务等方面提出若干支持措施。", 0, "PUBLISHED",
                LocalDateTime.now().minusDays(3)));
        store.add(record(2L, "新就业群体职业伤害保障政策解读", "政策",
                "对职业伤害保障的适用范围、参保方式和待遇申领流程进行解读。", 0, "PUBLISHED",
                LocalDateTime.now().minusDays(6)));
    }

    @Override
    public PageResult<Map<String, Object>> publicPage(long current, long size, String category, String keyword) {
        return page(current, size, category, keyword, "PUBLISHED");
    }

    /**
     * 分城市 / 分来源的前台分页，与 mysql 实现保持同一口径：
     * 不限城市的内容在所有城市可见；城市相关的内容只在该城市可见且排到最前面。
     */
    @Override
    public PageResult<Map<String, Object>> publicPage(long current, long size, String category, String keyword,
                                                      String cityCode, String sourceType, String platform) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedCategory = ContentSupport.trimText(category);
        String normalizedKeyword = ContentSupport.trimText(keyword);
        String city = ContentSupport.trimText(cityCode);
        String source = ContentSupport.trimText(sourceType).toUpperCase();
        String platformFilter = ContentSupport.trimText(platform);

        List<Map<String, Object>> filtered = store.stream()
                .filter(item -> "PUBLISHED".equals(item.get("status")))
                .filter(item -> normalizedCategory.isEmpty() || normalizedCategory.equals(item.get("category")))
                .filter(item -> source.isEmpty() || source.equals(String.valueOf(item.get("sourceType"))))
                .filter(item -> platformFilter.isEmpty() || platformFilter.equals(String.valueOf(item.getOrDefault("platform", ""))))
                // 城市过滤：本城市 + 不限城市
                .filter(item -> city.isEmpty()
                        || ContentSupport.trimText(item.get("cityCode")).isEmpty()
                        || city.equals(String.valueOf(item.get("cityCode"))))
                .filter(item -> normalizedKeyword.isEmpty()
                        || String.valueOf(item.get("title")).contains(normalizedKeyword)
                        || String.valueOf(item.get("content")).contains(normalizedKeyword)
                        || String.valueOf(item.getOrDefault("publisher", "")).contains(normalizedKeyword))
                .sorted(cityComparator(city))
                .toList();
        int from = (int) Math.min((safeCurrent - 1) * safeSize, filtered.size());
        int to = (int) Math.min(from + safeSize, filtered.size());
        List<Map<String, Object>> records = filtered.subList(from, to).stream().map(this::view).toList();
        return new PageResult<>(safeCurrent, safeSize, filtered.size(), records);
    }

    /** 本城市优先 → 有发布日期的优先 → 发布日期倒序。 */
    private static Comparator<Map<String, Object>> cityComparator(String city) {
        return Comparator
                .comparingInt((Map<String, Object> item) -> {
                    String itemCity = ContentSupport.trimText(item.get("cityCode"));
                    return !city.isEmpty() && city.equals(itemCity) ? 0 : 1;
                })
                .thenComparingInt(item -> ContentSupport.trimText(item.get("publishedAt")).isEmpty() ? 1 : 0)
                .thenComparing(Comparator.comparing(
                        (Map<String, Object> item) -> ContentSupport.trimText(item.get("publishedAt"))).reversed());
    }

    @Override
    public PageResult<Map<String, Object>> adminPage(long current, long size, String category, String keyword, String status) {
        return page(current, size, category, keyword, status);
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
        long id = idGenerator.incrementAndGet();
        Map<String, Object> record = record(id, title,
                ContentSupport.textOrDefault(data.get("category"), "政策"),
                ContentSupport.trimText(data.get("content")),
                0,
                ContentSupport.textOrDefault(data.get("status"), "PUBLISHED"),
                LocalDateTime.now());
        store.add(record);
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
        if (data.containsKey("category")) {
            record.put("category", ContentSupport.textOrDefault(data.get("category"), "政策"));
        }
        if (data.containsKey("content")) {
            record.put("content", ContentSupport.trimText(data.get("content")));
        }
        if (data.containsKey("status")) {
            record.put("status", ContentSupport.textOrDefault(data.get("status"), "PUBLISHED"));
        }
        record.put("updateTime", LocalDateTime.now());
        return view(record);
    }

    @Override
    public void delete(Long id) {
        Map<String, Object> record = require(id);
        store.remove(record);
    }

    private PageResult<Map<String, Object>> page(long current, long size, String category, String keyword, String status) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedCategory = ContentSupport.trimText(category);
        String normalizedKeyword = ContentSupport.trimText(keyword);
        String normalizedStatus = ContentSupport.trimText(status);
        List<Map<String, Object>> filtered = store.stream()
                .filter(item -> normalizedStatus.isEmpty() || normalizedStatus.equals(item.get("status")))
                .filter(item -> normalizedCategory.isEmpty() || normalizedCategory.equals(item.get("category")))
                .filter(item -> normalizedKeyword.isEmpty()
                        || String.valueOf(item.get("title")).contains(normalizedKeyword)
                        || String.valueOf(item.get("content")).contains(normalizedKeyword))
                .sorted(Comparator.comparing((Map<String, Object> item) -> (LocalDateTime) item.get("createTime")).reversed())
                .toList();
        int from = (int) Math.min((safeCurrent - 1) * safeSize, filtered.size());
        int to = (int) Math.min(from + safeSize, filtered.size());
        List<Map<String, Object>> records = filtered.subList(from, to).stream().map(this::view).toList();
        return new PageResult<>(safeCurrent, safeSize, filtered.size(), records);
    }

    private Map<String, Object> require(Long id) {
        if (id == null) {
            throw new BusinessException("资讯不存在");
        }
        return store.stream()
                .filter(item -> id.equals(item.get("id")))
                .findFirst()
                .orElseThrow(() -> new BusinessException("资讯不存在"));
    }

    private static String requireTitle(Object value) {
        String title = ContentSupport.trimText(value);
        if (title.isEmpty()) {
            throw new BusinessException("资讯标题不能为空");
        }
        return title;
    }

    private static Map<String, Object> record(Long id, String title, String category, String content,
                                             int viewCount, String status, LocalDateTime createTime) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("title", title);
        record.put("category", category);
        record.put("content", content);
        record.put("viewCount", viewCount);
        record.put("status", status);
        record.put("createTime", createTime);
        record.put("updateTime", createTime);
        return record;
    }

    /** 转换为对外固定字段结构。 */
    private Map<String, Object> view(Map<String, Object> record) {
        LocalDateTime createTime = (LocalDateTime) record.get("createTime");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", record.get("id"));
        out.put("title", record.get("title"));
        out.put("category", record.get("category"));
        out.put("content", record.get("content"));
        out.put("viewCount", record.get("viewCount"));
        out.put("status", record.get("status"));
        out.put("statusLabel", ContentSupport.statusLabel((String) record.get("status")));
        out.put("date", ContentSupport.formatDate(createTime));
        out.put("createTime", ContentSupport.formatDateTime(createTime));
        // 来源与城市信息，与 mysql 实现返回同样的字段集合（内存模式的种子数据没有这些值时给默认值）
        String sourceType = ContentSupport.textOrDefault(record.get("sourceType"), "OFFICIAL");
        String cityCode = ContentSupport.trimText(record.get("cityCode"));
        out.put("sourceType", sourceType);
        out.put("sourceTypeLabel", "PLATFORM".equalsIgnoreCase(sourceType) ? "平台" : "官方");
        out.put("publisher", ContentSupport.trimText(record.get("publisher")));
        out.put("platform", ContentSupport.trimText(record.get("platform")));
        out.put("cityCode", cityCode);
        out.put("national", cityCode.isEmpty());
        out.put("cityLabel", cityCode.isEmpty() ? "全国" : "");
        out.put("publishedAt", ContentSupport.trimText(record.get("publishedAt")));
        out.put("sourceUrl", ContentSupport.trimText(record.get("sourceUrl")));
        out.put("source", "news");
        return out;
    }
}
