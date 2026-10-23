package com.friendlystation.platform.station;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.persistence.entity.StationRectification;
import com.friendlystation.platform.persistence.mapper.StationRectificationMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("mysql")
public class MysqlStationRectificationService implements StationRectificationService {
    private final StationRectificationMapper mapper;
    private final StationService stationService;
    private final StationMemberService memberService;
    private final com.friendlystation.platform.notification.NotificationService notificationService;

    public MysqlStationRectificationService(StationRectificationMapper mapper, StationService stationService,
                                            StationMemberService memberService,
                                            com.friendlystation.platform.notification.NotificationService notificationService) {
        this.mapper = mapper;
        this.stationService = stationService;
        this.memberService = memberService;
        this.notificationService = notificationService;
    }

    @Override
    public PageResult<Map<String, Object>> page(long current, long size, String status, List<Long> stationIds) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        if (stationIds != null && stationIds.isEmpty()) {
            return new PageResult<>(safeCurrent, safeSize, 0, List.of());
        }
        LambdaQueryWrapper<StationRectification> query = new LambdaQueryWrapper<>();
        if (stationIds != null) {
            query.in(StationRectification::getStationId, stationIds);
        }
        if (status != null && !status.isBlank()) {
            query.eq(StationRectification::getStatus, status.trim().toUpperCase());
        }
        query.orderByAsc(StationRectification::getStatus).orderByDesc(StationRectification::getCreateTime);
        var result = mapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(safeCurrent, safeSize), query);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::view).toList());
    }

    @Override
    public Map<String, Object> create(long stationId, Map<String, Object> request, long operatorId) {
        StationRectification item = new StationRectification();
        item.setStationId(stationId);
        item.setTitle(required(request.get("title"), "请填写整改标题"));
        item.setContent(required(request.get("content"), "请填写整改要求"));
        item.setLevel(FormatUtils.text(request.get("level"), "NORMAL"));
        item.setStatus("PENDING");
        String deadline = FormatUtils.text(request.get("deadline"), "");
        if (!deadline.isBlank()) {
            item.setDeadline(LocalDate.parse(deadline));
        }
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        mapper.insert(item);
        notifyStation(item, "收到新的服务质量整改通知", "请在整改期限内反馈");
        return view(item);
    }

    @Override
    public Map<String, Object> reply(long id, String reply, long operatorId) {
        StationRectification item = require(id);
        if ("CLOSED".equals(item.getStatus())) {
            throw new BusinessException("整改已关闭，不能再反馈");
        }
        item.setStationReply(reply == null ? "" : reply.trim());
        item.setStatus("REVIEW");
        item.setUpdateTime(LocalDateTime.now());
        mapper.updateById(item);
        return view(item);
    }

    @Override
    public Map<String, Object> review(long id, String action, String note, long operatorId) {
        StationRectification item = require(id);
        if (!"REVIEW".equals(item.getStatus()) && !"PENDING".equals(item.getStatus())) {
            throw new BusinessException("当前整改状态不能复核");
        }
        boolean suspend = "suspend".equalsIgnoreCase(action);
        item.setStatus("approve".equalsIgnoreCase(action) || suspend ? "CLOSED" : "PENDING");
        item.setReviewNote(note == null ? "" : note.trim());
        item.setReviewerId(operatorId);
        item.setUpdateTime(LocalDateTime.now());
        mapper.updateById(item);
        if (suspend) {
            stationService.suspend(item.getStationId(), LocalDateTime.now(), LocalDateTime.now().plusDays(7),
                    note == null || note.isBlank() ? "整改未通过，暂停服务" : note.trim());
        }
        notifyStation(item, "整改复核结果", "approve".equalsIgnoreCase(action) || suspend ? "整改已通过" : "请继续整改");
        return view(item);
    }

    @Override
    public Map<String, Object> detail(long id) {
        return view(require(id));
    }

    @Override
    public long pendingCount(List<Long> stationIds) {
        if (stationIds != null && stationIds.isEmpty()) return 0;
        LambdaQueryWrapper<StationRectification> query = new LambdaQueryWrapper<>();
        if (stationIds != null) query.in(StationRectification::getStationId, stationIds);
        query.ne(StationRectification::getStatus, "CLOSED");
        Long count = mapper.selectCount(query);
        return count == null ? 0 : count;
    }

    private void notifyStation(StationRectification item, String title, String content) {
        StationDto station = stationService.detail(item.getStationId());
        if (station.managerId() != null) {
            notificationService.push(station.managerId(), "SYSTEM", title,
                    station.stationName() + " · " + content, "/admin?tab=rectifications&rectificationId=" + item.getId(),
                    "RECTIFICATION", item.getId());
        }
    }

    private StationRectification require(long id) {
        StationRectification item = mapper.selectById(id);
        if (item == null) throw new BusinessException("整改记录不存在");
        return item;
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
        out.put("statusLabel", statusLabel(item.getStatus()));
        out.put("deadline", item.getDeadline() == null ? "" : item.getDeadline().toString());
        out.put("stationReply", item.getStationReply() == null ? "" : item.getStationReply());
        out.put("reviewNote", item.getReviewNote() == null ? "" : item.getReviewNote());
        out.put("createTime", FormatUtils.dateTime(item.getCreateTime()));
        return out;
    }

    private static String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "REVIEW" -> "待平台复核";
            case "CLOSED" -> "已关闭";
            default -> "待整改";
        };
    }

    private static String required(Object value, String message) {
        String text = FormatUtils.text(value, "");
        if (text.isBlank()) throw new BusinessException(message);
        return text;
    }
}
