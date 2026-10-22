package com.friendlystation.platform.notification;

import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 站内通知：当前登录用户自己的消息，不做跨用户读取。 */
@RestController
@RequestMapping("/api/notification")
@RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(notificationService.page(
                CurrentUserContext.require().id(), current, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount() {
        long count = notificationService.unreadCount(CurrentUserContext.require().id());
        return ApiResponse.ok(Map.of("count", count));
    }

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(notificationService.list(CurrentUserContext.require().id()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Map<String, Object>> markRead(@PathVariable Long id) {
        notificationService.markRead(CurrentUserContext.require().id(), id);
        return ApiResponse.ok(Map.of("id", id, "read", true));
    }

    @PutMapping("/read-all")
    public ApiResponse<Map<String, Object>> markAllRead() {
        notificationService.markAllRead(CurrentUserContext.require().id());
        return ApiResponse.ok(Map.of("read", true));
    }
}
