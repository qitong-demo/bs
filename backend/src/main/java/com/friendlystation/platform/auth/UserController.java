package com.friendlystation.platform.auth;

import com.friendlystation.platform.admin.AdminUserService;
import com.friendlystation.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 当前登录用户的个人中心接口，账号数据统一由 {@link AdminUserService} 提供。 */
@RestController
@RequestMapping("/api/user")
public class UserController {
    private final AdminUserService adminUserService;

    public UserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/info")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> info() {
        return ApiResponse.ok(adminUserService.userDetail(CurrentUserContext.require().id()));
    }

    @GetMapping("/page")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<List<Map<String, Object>>> page(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(adminUserService.userPage(1, 200, role, keyword).records());
    }

    @PutMapping("/info")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> updateInfo(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(adminUserService.updateUser(CurrentUserContext.require().id(), payload));
    }

    @PutMapping("/{id}/status")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(adminUserService.updateStatus(id, String.valueOf(payload.getOrDefault("status", "正常"))));
    }
}
