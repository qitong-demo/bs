package com.friendlystation.platform.admin;

import com.friendlystation.platform.auth.CurrentUser;
import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.DataScopeService;
import com.friendlystation.platform.auth.RequirePermission;
import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.workflow.WorkflowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final AdminPermissionService adminPermissionService;
    private final com.friendlystation.platform.auth.AdminCredentialService adminCredentialService;

    public AdminController(WorkflowService workflowService, AdminUserService adminUserService,
                           DataScopeService dataScopeService,
                           AdminPermissionService adminPermissionService,
                           com.friendlystation.platform.auth.AdminCredentialService adminCredentialService) {
        this.workflowService = workflowService;
        this.adminUserService = adminUserService;
        this.dataScopeService = dataScopeService;
        this.adminPermissionService = adminPermissionService;
        this.adminCredentialService = adminCredentialService;
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

    @RequirePermission({"ACCOUNT_MANAGE"})
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

    @RequirePermission({"ACCOUNT_MANAGE"})
    @GetMapping("/users/{id}")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> userDetail(@PathVariable Long id) {
        return ApiResponse.ok(adminUserService.userDetail(id));
    }

    @RequirePermission({"ACCOUNT_MANAGE"})
    @PutMapping("/users/{id}")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(adminUserService.updateUser(id, payload));
    }

    @RequirePermission({"ACCOUNT_MANAGE"})
    @PutMapping("/users/{id}/status")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> updateUserStatus(@PathVariable Long id,
                                                             @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(adminUserService.updateStatus(id, String.valueOf(payload.getOrDefault("status", "正常"))));
    }

    @RequirePermission({"ACCOUNT_MANAGE"})
    @PutMapping("/users/{id}/roles")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> updateUserRoles(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(adminUserService.setRoles(id, readRoleCodes(payload)));
    }

    // ---------------------------------------------------------------- 子账号与凭据

    /**
     * 创建管理员子账号并分发权限。
     *
     * <p>需要管理员输入**自己的**登录密码二次确认——管理员账号一旦被盗，
     * 最省事的破坏就是批量建号或改密。</p>
     */
    @RequirePermission({"ACCOUNT_MANAGE"})
    @PostMapping("/users")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> createSubAccount(@RequestBody Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : payload;
        java.util.List<String> permissions = new java.util.ArrayList<>();
        if (data.get("permissions") instanceof java.util.List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    permissions.add(String.valueOf(item));
                }
            }
        }
        return ApiResponse.ok(adminCredentialService.createSubAccount(
                CurrentUserContext.require().id(),
                text(data.get("adminPassword")),
                text(data.get("username")),
                text(data.get("phone")),
                text(data.get("password")),
                permissions));
    }

    /** 改他人账号 ID：管理员密码 + 被改账号邮箱的验证码。 */
    @RequirePermission({"ACCOUNT_MANAGE"})
    @PutMapping("/users/{id}/username")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> changeOtherUsername(@PathVariable Long id,
                                                               @RequestBody Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : payload;
        return ApiResponse.ok(adminCredentialService.changeOtherUsername(
                CurrentUserContext.require().id(), text(data.get("adminPassword")),
                id, text(data.get("code")), text(data.get("newUsername"))));
    }

    /** 重置他人密码：管理员密码即可，不需要被改账号配合（忘密码场景的兜底）。 */
    @RequirePermission({"ACCOUNT_MANAGE"})
    @PutMapping("/users/{id}/password")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> resetOtherPassword(@PathVariable Long id,
                                                              @RequestBody Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : payload;
        return ApiResponse.ok(adminCredentialService.resetOtherPassword(
                CurrentUserContext.require().id(), text(data.get("adminPassword")),
                id, text(data.get("newPassword")), text(data.get("confirmPassword"))));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    // ---------------------------------------------------------------- 功能权限

    /**
     * 权限目录。管理员创建子账号时据此渲染勾选项。
     * grantable=false 的项在界面上要禁用——例如账号管理，授给子账号等于让它自我提权。
     */
    @GetMapping("/permissions")
    public ApiResponse<List<Map<String, Object>>> permissions() {
        return ApiResponse.ok(adminPermissionService.catalog());
    }

    /**
     * 当前账号实际生效的权限。
     *
     * <p>前端据此决定管理端显示哪些标签页——不再前端硬编码 platformOnly，
     * 否则子账号会看到点进去就 403 的标签。</p>
     */
    @GetMapping("/my-permissions")
    public ApiResponse<Map<String, Object>> myPermissions() {
        CurrentUser user = CurrentUserContext.require();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("permissions", adminPermissionService.effectiveOf(user.id(), user.role()));
        out.put("role", user.role());
        return ApiResponse.ok(out);
    }

    /** 某账号被显式授予的权限。 */
    @RequirePermission({"ACCOUNT_MANAGE"})
    @GetMapping("/users/{id}/permissions")
    public ApiResponse<Map<String, Object>> userPermissions(@PathVariable Long id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", id);
        out.put("permissions", adminPermissionService.grantedOf(id));
        out.put("catalog", adminPermissionService.catalog());
        return ApiResponse.ok(out);
    }

    /** 覆盖式分发权限。 */
    @RequirePermission({"ACCOUNT_MANAGE"})
    @PutMapping("/users/{id}/permissions")
    public ApiResponse<Map<String, Object>> grantPermissions(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("permissions");
        List<String> codes = new java.util.ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    codes.add(String.valueOf(item));
                }
            }
        }
        return ApiResponse.ok(adminPermissionService.grant(id, codes));
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
