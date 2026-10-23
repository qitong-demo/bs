package com.friendlystation.platform.station;

import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.persistence.entity.StationRectification;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Profile("!mysql")
public class DemoStationRectificationService implements StationRectificationService {
    private final List<StationRectification> items = new CopyOnWriteArrayList<>();
    private final AtomicLong ids = new AtomicLong(8000);
    private final StationService stationService;
    private final com.friendlystation.platform.notification.NotificationService notificationService;

    public DemoStationRectificationService(StationService stationService,
                                           com.friendlystation.platform.notification.NotificationService notificationService) {
        this.stationService = stationService;
        this.notificationService = notificationService;
    }

    @Override
    public PageResult<Map<String, Object>> page(long current, long size, String status, List<Long> stationIds) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        String normalized = status == null ? "" : status.trim().toUpperCase();
        List<StationRectification> filtered = items.stream()
                .filter(item -> stationIds == null || stationIds.contains(item.getStationId()))
                .filter(item -> normalized.isBlank() || normalized.equals(item.getStatus()))
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .toList();
        int from = (int) Math.min((safeCurrent - 1) * safeSize, filtered.size());
        int to = (int) Math.min(from + safeSize, filtered.size());
        return new PageResult<>(safeCurrent, safeSize, filtered.size(),
                new ArrayList<>(filtered.subList(from, to)).stream().map(this::view).toList());
    }

    @Override
    public Map<String, Object> create(long stationId, Map<String, Object> request, long operatorId) {
        StationRectification item = new StationRectification();
        item.setId(ids.incrementAndGet());
        item.setStationId(stationId);
        item.setTitle(required(request.get("title"), "请填写整改标题"));
        item.setContent(required(request.get("content"), "请填写整改要求"));
        item.setLevel(FormatUtils.text(request.get("level"), "NORMAL"));
        item.setStatus("PENDING");
        String deadline = FormatUtils.text(request.get("deadline"), "");
        if (!deadline.isBlank()) item.setDeadline(LocalDate.parse(deadline));
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        items.add(item);
        notifyStation(item, "收到新的服务质量整改通知");
        return view(item);
    }

    @Override
    public Map<String, Object> reply(long id, String reply, long operatorId) {
        StationRectification item = require(id);
        if ("CLOSED".equals(item.getStatus())) throw new BusinessException("整改已关闭，不能再反馈");
        item.setStationReply(reply == null ? "" : reply.trim());
        item.setStatus("REVIEW");
        item.setUpdateTime(LocalDateTime.now());
        return view(item);
    }

    @Override
    public Map<String, Object> review(long id, String action, String note, long operatorId) {
        StationRectification item = require(id);
        if (!"REVIEW".equals(item.getStatus()) && !"PENDING".equals(item.getStatus())) {
            throw new BusinessException("当前整改状态不能复核");
        }
        item.setStatus("approve".equalsIgnoreCase(action) ? "CLOSED" : "PENDING");
        item.setReviewNote(note == null ? "" : note.trim());
        item.setReviewerId(operatorId);
        item.setUpdateTime(LocalDateTime.now());
        notifyStation(item, "整改复核结果");
        return view(item);
    }

    @Override
    public Map<String, Object> detail(long id) {
        return view(require(id));
    }

    @Override
    public long pendingCount(List<Long> stationIds) {
        return items.stream()
                .filter(item -> stationIds == null || stationIds.contains(item.getStationId()))
                .filter(item -> !"CLOSED".equals(item.getStatus())).count();
    }

    private void notifyStation(StationRectification item, String title) {
        StationDto station = stationService.detail(item.getStationId());
        if (station.managerId() != null) {
            notificationService.push(station.managerId(), "SYSTEM", title,
                    station.stationName() + " · " + item.getTitle(),
                    "/admin?tab=rectifications&rectificationId=" + item.getId(), "RECTIFICATION", item.getId());
        }
    }

    private StationRectification require(long id) {
        return items.stream().filter(item -> item.getId() == id).findFirst()
                .orElseThrow(() -> new BusinessException("整改记录不存在"));
    }

    private Map<String, Object> view(StationRectification item) {
        StationDto station = stationService.detail(item.getStationId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", item.getId());
        out.put("stationId", item.getStationId());
        out.put("stationName", station.stationName());
        out.put("title", item.getTitle());
        out.put("content", item.getContent());
        out.put("level", item.getLevel());
        out.put("status", item.getStatus());
        out.put("statusLabel", switch (item.getStatus()) {
            case "REVIEW" -> "待平台复核";
            case "CLOSED" -> "已关闭";
            default -> "待整改";
        });
        out.put("deadline", item.getDeadline() == null ? "" : item.getDeadline().toString());
        out.put("stationReply", item.getStationReply() == null ? "" : item.getStationReply());
        out.put("reviewNote", item.getReviewNote() == null ? "" : item.getReviewNote());
        out.put("createTime", FormatUtils.dateTime(item.getCreateTime()));
        return out;
    }

    private static String required(Object value, String message) {
        String text = FormatUtils.text(value, "");
        if (text.isBlank()) throw new BusinessException(message);
        return text;
    }
}
