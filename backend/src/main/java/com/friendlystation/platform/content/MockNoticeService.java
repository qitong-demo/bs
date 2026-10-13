package com.friendlystation.platform.content;

import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.PageResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** 非 mysql Profile 下的内存公告实现，用于本地演示与前端联调。 */
@Service
@Profile("!mysql")
public class MockNoticeService implements NoticeService {
    private final AtomicLong idGenerator = new AtomicLong(2);
    private final List<Map<String, Object>> store = new CopyOnWriteArrayList<>();

    public MockNoticeService() {
        store.add(record(1L, "关于开展夏季送清凉活动的通知", "通知", "ALL",
                "近期将面向新就业群体开展夏季送清凉活动，请就近前往友好驿站领取防暑物资。", "PUBLISHED",
                LocalDateTime.now().minusDays(4)));
        store.add(record(2L, "友好驿站服务时间调整公告", "公告", "ALL",
                "自本月起部分友好驿站服务时间调整为 08:00-22:00，请合理安排前往时间。", "PUBLISHED",
                LocalDateTime.now().minusDays(9)));
    }

    @Override
    public PageResult<Map<String, Object>> publicPage(long current, long size, String noticeType, String keyword) {
        return page(current, size, noticeType, keyword, "PUBLISHED");
    }

    @Override
    public PageResult<Map<String, Object>> adminPage(long current, long size, String noticeType, String keyword, String status) {
        return page(current, size, noticeType, keyword, status);
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
                ContentSupport.textOrDefault(data.get("noticeType"), "通知"),
                ContentSupport.textOrDefault(data.get("targetType"), "ALL"),
                ContentSupport.trimText(data.get("content")),
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
        if (data.containsKey("noticeType")) {
            record.put("noticeType", ContentSupport.textOrDefault(data.get("noticeType"), "通知"));
        }
        if (data.containsKey("targetType")) {
            record.put("targetType", ContentSupport.textOrDefault(data.get("targetType"), "ALL"));
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
    }

    private PageResult<Map<String, Object>> page(long current, long size, String noticeType, String keyword, String status) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedType = ContentSupport.trimText(noticeType);
        String normalizedKeyword = ContentSupport.trimText(keyword);
        String normalizedStatus = ContentSupport.trimText(status);
        List<Map<String, Object>> filtered = store.stream()
                .filter(item -> normalizedStatus.isEmpty() || normalizedStatus.equals(item.get("status")))
                .filter(item -> normalizedType.isEmpty() || normalizedType.equals(item.get("noticeType")))
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
            throw new BusinessException("公告不存在");
        }
        return store.stream()
                .filter(item -> id.equals(item.get("id")))
                .findFirst()
                .orElseThrow(() -> new BusinessException("公告不存在"));
    }

    private static String requireTitle(Object value) {
        String title = ContentSupport.trimText(value);
        if (title.isEmpty()) {
            throw new BusinessException("公告标题不能为空");
        }
        return title;
    }

    private static Map<String, Object> record(Long id, String title, String noticeType, String targetType,
                                             String content, String status, LocalDateTime createTime) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("title", title);
        record.put("noticeType", noticeType);
        record.put("targetType", targetType);
        record.put("content", content);
        record.put("status", status);
        record.put("createTime", createTime);
        return record;
    }

    /** 转换为对外固定字段结构。 */
    private Map<String, Object> view(Map<String, Object> record) {
        LocalDateTime createTime = (LocalDateTime) record.get("createTime");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", record.get("id"));
        out.put("title", record.get("title"));
        out.put("noticeType", record.get("noticeType"));
        out.put("targetType", record.get("targetType"));
        out.put("content", record.get("content"));
        out.put("status", record.get("status"));
        out.put("statusLabel", ContentSupport.statusLabel((String) record.get("status")));
        out.put("date", ContentSupport.formatDate(createTime));
        out.put("createTime", ContentSupport.formatDateTime(createTime));
        out.put("source", "notice");
        return out;
    }
}
