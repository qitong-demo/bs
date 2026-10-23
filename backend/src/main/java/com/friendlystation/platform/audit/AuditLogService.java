package com.friendlystation.platform.audit;

import com.friendlystation.platform.auth.CurrentUser;
import com.friendlystation.platform.common.PageResult;

import java.util.Map;

public interface AuditLogService {
    void record(CurrentUser actor, String action, String targetType, long targetId, String reason, String detail);

    PageResult<Map<String, Object>> page(long current, long size, String action);
}
