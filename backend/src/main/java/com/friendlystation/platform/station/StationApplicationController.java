package com.friendlystation.platform.station;

import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.RequirePermission;
import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
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

/** 驿站入驻注册：用户提交，平台管理员审核。 */
@RestController
@RequestMapping("/api/station/apply")
public class StationApplicationController {
    private final StationApplicationService applicationService;

    public StationApplicationController(StationApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 普通用户提交入驻申请；已有待审核申请时拒绝重复提交。 */
    @PostMapping
    @RequireRole({"USER"})
    public ApiResponse<Map<String, Object>> submit(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(applicationService.submit(payload, CurrentUserContext.require().id()));
    }

    /** 我的入驻申请：审核通过后角色会变成驿站管理员，仍可查看历史。 */
    @GetMapping("/mine")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<List<Map<String, Object>>> mine() {
        return ApiResponse.ok(applicationService.mine(CurrentUserContext.require().id()));
    }

    @GetMapping("/admin/page")
    @RequireRole({"PLATFORM_ADMIN"})
    @RequirePermission({"STATION_MANAGE"})
    public ApiResponse<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(applicationService.page(current, size, status, keyword));
    }

    @GetMapping("/admin/pending-count")
    @RequireRole({"PLATFORM_ADMIN"})
    @RequirePermission({"STATION_MANAGE"})
    public ApiResponse<Map<String, Object>> pendingCount() {
        return ApiResponse.ok(Map.of("count", applicationService.pendingCount()));
    }

    @PutMapping("/{id}/approve")
    @RequireRole({"PLATFORM_ADMIN"})
    @RequirePermission({"STATION_MANAGE"})
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, Object> payload) {
        String note = payload == null ? "" : String.valueOf(payload.getOrDefault("reviewNote", ""));
        return ApiResponse.ok(applicationService.approve(id, CurrentUserContext.require().id(), note));
    }

    @PutMapping("/{id}/reject")
    @RequireRole({"PLATFORM_ADMIN"})
    @RequirePermission({"STATION_MANAGE"})
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> payload) {
        String note = payload == null ? "" : String.valueOf(payload.getOrDefault("reviewNote", ""));
        return ApiResponse.ok(applicationService.reject(id, CurrentUserContext.require().id(), note));
    }

    @PutMapping("/{id}/cancel")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        return ApiResponse.ok(applicationService.cancel(id, CurrentUserContext.require().id()));
    }
}
