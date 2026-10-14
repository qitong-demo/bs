package com.friendlystation.platform.admin;

import com.friendlystation.platform.auth.CurrentUser;
import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.DataScopeService;
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
@RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
public class AdminController {
    private final WorkflowService workflowService;
    private final AdminUserService adminUserService;
    private final DataScopeService dataScopeService;

    public AdminController(WorkflowService workflowService, AdminUserService adminUserService,
                           DataScopeService dataScopeService) {
        this.workflowService = workflowService;
        this.adminUserService = adminUserService;
        this.dataScopeService = dataScopeService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        CurrentUser user = CurrentUserContext.require();
        // 与预约/诉求列表用同一套数据范围：此前这里统计的是全平台，
        // 驿站管理员在首页看到的待办数字会与管理工作台里按范围过滤的列表对不上。
        // stationIds/areas 为 null 时表示平台级角色、不加限制。
        Map<String, Object> out = new LinkedHashMap<>(
                workflowService.overview(dataScopeService.stationIds(user), dataScopeService.areas(user)));
        out.put("operatorId", user.id());
        out.put("operatorRole", user.role());
        out.put("generatedAt", FormatUtils.dateTime(LocalDateTime.now()));
        return ApiResponse.ok(out);
    }

    @GetMapping("/users")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<PageResult<Map<String, Object>>> users(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(adminUserService.userPage(current, size, role, keyword));
    }

    @GetMapping("/users/{id}")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> userDetail(@PathVariable Long id) {
        return ApiResponse.ok(adminUserService.userDetail(id));
    }

    @PutMapping("/users/{id}")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(adminUserService.updateUser(id, payload));
    }

    @PutMapping("/users/{id}/status")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> updateUserStatus(@PathVariable Long id,
                                                             @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(adminUserService.updateStatus(id, String.valueOf(payload.getOrDefault("status", "正常"))));
    }

    @PutMapping("/users/{id}/roles")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> updateUserRoles(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(adminUserService.setRoles(id, readRoleCodes(payload)));
    }

    @GetMapping("/roles")
    @RequireRole({"PLATFORM_ADMIN"})
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
