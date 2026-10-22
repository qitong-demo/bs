package com.friendlystation.platform.notification;

import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
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

/** 内存版站内通知，供非 mysql profile 演示。 */
@Service
@Profile("!mysql")
public class DemoNotificationService implements NotificationService {
    private final AtomicLong idGenerator = new AtomicLong(0);
    private final List<Map<String, Object>> items = new CopyOnWriteArrayList<>();

    @Override
    public void push(long userId, String type, String title, String content, String link,
                     String bizType, Long bizId) {
        if (userId <= 0 || title == null || title.isBlank()) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", idGenerator.incrementAndGet());
        item.put("userId", userId);
        item.put("type", type == null ? "SYSTEM" : type);
        item.put("title", title.trim());
        item.put("content", content == null ? "" : content.trim());
        item.put("link", link == null ? "" : link.trim());
        item.put("read", false);
        item.put("bizType", bizType == null ? "" : bizType);
        item.put("bizId", bizId == null ? 0L : bizId);
        item.put("createTime", FormatUtils.dateTime(LocalDateTime.now()));
        items.add(item);
    }

    @Override
    public PageResult<Map<String, Object>> page(long userId, long current, long size) {
        return PageResult.of(current, size, list(userId));
    }

    @Override
    public long unreadCount(long userId) {
        return list(userId).stream().filter(item -> !Boolean.TRUE.equals(item.get("read"))).count();
    }

    @Override
    public void markRead(long userId, long id) {
        Map<String, Object> item = find(userId, id);
        if (item == null) {
            throw new BusinessException("通知不存在");
        }
        item.put("read", true);
    }

    @Override
    public void markAllRead(long userId) {
        // 直接改内部对象：list() 返回的是副本，改副本不会影响未读计数。
        items.stream()
                .filter(item -> number(item.get("userId")) == userId)
                .forEach(item -> item.put("read", true));
    }

    @Override
    public List<Map<String, Object>> list(long userId) {
        return items.stream()
                .filter(item -> number(item.get("userId")) == userId)
                .sorted(Comparator.comparing((Map<String, Object> item) -> String.valueOf(item.get("createTime")))
                        .thenComparing(item -> number(item.get("id")))
                        .reversed())
                .map(item -> (Map<String, Object>) new LinkedHashMap<>(item))
                .toList();
    }

    private Map<String, Object> find(long userId, long id) {
        return items.stream()
                .filter(item -> number(item.get("userId")) == userId && number(item.get("id")) == id)
                .findFirst()
                .orElse(null);
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
