package com.friendlystation.platform.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.friendlystation.platform.auth.CurrentUser;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.persistence.entity.AuditLog;
import com.friendlystation.platform.persistence.mapper.AuditLogMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Profile("mysql")
public class MysqlAuditLogService implements AuditLogService {
    private final AuditLogMapper mapper;

    public MysqlAuditLogService(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(CurrentUser actor, String action, String targetType, long targetId,
                       String reason, String detail) {
        if (actor == null) {
            return;
        }
        AuditLog log = new AuditLog();
        log.setActorId(actor.id());
        log.setActorName(actor.username());
        log.setActorRole(actor.role());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setReason(reason == null ? "" : reason);
        log.setDetail(detail == null ? "" : detail);
        log.setCreateTime(LocalDateTime.now());
        mapper.insert(log);
    }

    @Override
    public PageResult<Map<String, Object>> page(long current, long size, String action) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<AuditLog> query = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) {
            query.eq(AuditLog::getAction, action.trim());
        }
        query.orderByDesc(AuditLog::getCreateTime).orderByDesc(AuditLog::getId);
        Page<AuditLog> result = mapper.selectPage(new Page<>(safeCurrent, safeSize), query);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::view).toList());
    }

    private Map<String, Object> view(AuditLog log) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", log.getId());
        out.put("actorId", log.getActorId());
        out.put("actorName", log.getActorName() == null ? "" : log.getActorName());
        out.put("actorRole", log.getActorRole() == null ? "" : log.getActorRole());
        out.put("action", log.getAction());
        out.put("targetType", log.getTargetType());
        out.put("targetId", log.getTargetId() == null ? 0L : log.getTargetId());
        out.put("reason", log.getReason() == null ? "" : log.getReason());
        out.put("detail", log.getDetail() == null ? "" : log.getDetail());
        out.put("createTime", FormatUtils.dateTime(log.getCreateTime()));
        return out;
    }
}
