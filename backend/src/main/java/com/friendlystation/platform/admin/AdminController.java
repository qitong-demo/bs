package com.friendlystation.platform.admin;

import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.workflow.WorkflowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理端工作台：运营概览、账号管理与角色权限管理。 */
@RestController
@RequestMapping("/api/admin")
@RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
public class AdminController {
    private final WorkflowService workflowService;
    private final AdminUserService adminUserService;

    public AdminController(WorkflowService workflowService, AdminUserService adminUserService) {
        this.workflowService = workflowService;
        this.adminUserService = adminUserService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> out = new LinkedHashMap<>(workflowService.overview());
        out.put("operatorId", CurrentUserContext.require().id());
        out.put("operatorRole", CurrentUserContext.require().role());
        out.put("generatedAt", FormatUtils.dateTime(LocalDateTime.now()));
        return ApiResponse.ok(out);
    }

    @GetMapping("/users")
    @RequireRole({"PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<PageResult<Map<String, Object>>> users(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(adminUserService.userPage(current, size, role, keyword));
    }

    @GetMapping("/users/{id}")
    @RequireRole({"PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<Map<String, Object>> userDetail(@PathVariable Long id) {
        return ApiResponse.ok(adminUserService.userDetail(id));
    }

    @PutMapping("/users/{id}")
    @RequireRole({"PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<Map<String, Object>> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(adminUserService.updateUser(id, payload));
    }

    @PutMapping("/users/{id}/status")
    @RequireRole({"PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<Map<String, Object>> updateUserStatus(@PathVariable Long id,
                                                             @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(adminUserService.updateStatus(id, String.valueOf(payload.getOrDefault("status", "正常"))));
    }

    @PutMapping("/users/{id}/roles")
    @RequireRole({"PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<Map<String, Object>> updateUserRoles(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(adminUserService.setRoles(id, readRoleCodes(payload)));
    }

    @GetMapping("/roles")
    @RequireRole({"PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<List<Map<String, Object>>> roles() {
        return ApiResponse.ok(adminUserService.roles());
    }

    private static List<String> readRoleCodes(Map<String, Object> payload) {
        Object value = payload.get("roleCodes") == null ? payload.get("roles") : payload.get("roleCodes");
        if (value instanceof List<?> list) {
            return list.stream().filter(item -> item != null).map(String::valueOf).toList();
        }
        if (value instanceof String text) {
            return List.of(text.split(","));
        }
        return List.of();
    }
}
