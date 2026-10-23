package com.friendlystation.platform.audit;

import com.friendlystation.platform.auth.CurrentUser;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Profile("!mysql")
public class DemoAuditLogService implements AuditLogService {
    private final List<Map<String, Object>> logs = new CopyOnWriteArrayList<>();
    private final AtomicLong ids = new AtomicLong(7000);

    @Override
    public void record(CurrentUser actor, String action, String targetType, long targetId,
                       String reason, String detail) {
        if (actor == null) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", ids.incrementAndGet());
        row.put("actorId", actor.id());
        row.put("actorName", actor.username());
        row.put("actorRole", actor.role());
        row.put("action", action);
        row.put("targetType", targetType);
        row.put("targetId", targetId);
        row.put("reason", reason == null ? "" : reason);
        row.put("detail", detail == null ? "" : detail);
        row.put("createTime", LocalDateTime.now());
        logs.add(row);
    }

    @Override
    public PageResult<Map<String, Object>> page(long current, long size, String action) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        String normalized = action == null ? "" : action.trim();
        List<Map<String, Object>> filtered = logs.stream()
                .filter(row -> normalized.isBlank() || normalized.equals(row.get("action")))
                .sorted((a, b) -> ((LocalDateTime) b.get("createTime")).compareTo((LocalDateTime) a.get("createTime")))
                .toList();
        int from = (int) Math.min((safeCurrent - 1) * safeSize, filtered.size());
        int to = (int) Math.min(from + safeSize, filtered.size());
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : filtered.subList(from, to)) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.put("createTime", FormatUtils.dateTime((LocalDateTime) row.get("createTime")));
            records.add(copy);
        }
        return new PageResult<>(safeCurrent, safeSize, filtered.size(), records);
    }
}
