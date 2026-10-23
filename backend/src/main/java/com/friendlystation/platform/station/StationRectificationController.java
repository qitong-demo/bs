package com.friendlystation.platform.station;

import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.DataScopeService;
import com.friendlystation.platform.auth.RequirePermission;
import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.audit.AuditLogService;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/station/rectification")
public class StationRectificationController {
    private final StationRectificationService service;
    private final DataScopeService dataScopeService;
    private final AuditLogService auditLogService;

    public StationRectificationController(StationRectificationService service,
                                           DataScopeService dataScopeService,
                                           AuditLogService auditLogService) {
        this.service = service;
        this.dataScopeService = dataScopeService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/page")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.page(current, size, status,
                dataScopeService.stationIds(CurrentUserContext.require())));
    }

    @GetMapping("/pending-count")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> pendingCount() {
        return ApiResponse.ok(Map.of("count", service.pendingCount(
                dataScopeService.stationIds(CurrentUserContext.require()))));
    }

    @PostMapping
    @RequireRole({"PLATFORM_ADMIN"})
    @RequirePermission({"STATION_MANAGE"})
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> payload) {
        var user = CurrentUserContext.require();
        long stationId = FormatUtils.number(payload.get("stationId"));
        Map<String, Object> result = service.create(stationId, payload, user.id());
        auditLogService.record(user, "RECTIFICATION_CREATE", "STATION", stationId,
                String.valueOf(payload.getOrDefault("title", "")), "station=" + stationId);
        return ApiResponse.ok(result);
    }

    @PutMapping("/{id}/reply")
    @RequireRole({"STATION_ADMIN"})
    public ApiResponse<Map<String, Object>> reply(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> payload) {
        var user = CurrentUserContext.require();
        requireScope(id, user);
        String reply = payload == null ? "" : String.valueOf(payload.getOrDefault("reply", ""));
        return ApiResponse.ok(service.reply(id, reply, user.id()));
    }

    @PutMapping("/{id}/review")
    @RequireRole({"PLATFORM_ADMIN"})
    @RequirePermission({"STATION_MANAGE"})
    public ApiResponse<Map<String, Object>> review(@PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, Object> payload) {
        var user = CurrentUserContext.require();
        Map<String, Object> data = payload == null ? Map.of() : payload;
        String action = String.valueOf(data.getOrDefault("action", "approve"));
        String note = String.valueOf(data.getOrDefault("note", ""));
        Map<String, Object> result = service.review(id, action, note, user.id());
        auditLogService.record(user, "RECTIFICATION_REVIEW", "STATION", id, note, "action=" + action);
        return ApiResponse.ok(result);
    }

    private void requireScope(long id, com.friendlystation.platform.auth.CurrentUser user) {
        List<Long> scoped = dataScopeService.stationIds(user);
        long stationId = FormatUtils.number(service.detail(id).get("stationId"));
        if (scoped != null && !scoped.contains(stationId)) {
            throw new com.friendlystation.platform.common.ForbiddenException("无权处理该站点的整改");
        }
    }
}
