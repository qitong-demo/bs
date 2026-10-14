package com.friendlystation.platform.auth;

import com.friendlystation.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 账号安全：改账号 ID、改密码、绑定邮箱。
 *
 * <p>三种角色（普通用户 / 驿站管理员 / 管理员）共用这一套接口——
 * 他们改的都是"自己的"凭据，规则一致，没有分成三份的必要。</p>
 */
@RestController
@RequestMapping("/api/user/credentials")
public class UserCredentialController {

    private final UserCredentialService userCredentialService;

    public UserCredentialController(UserCredentialService userCredentialService) {
        this.userCredentialService = userCredentialService;
    }

    /** 凭据概况：账号 ID 与是否已绑定邮箱（邮箱打码）。 */
    @GetMapping
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> info() {
        return ApiResponse.ok(userCredentialService.credentialInfo(CurrentUserContext.require().id()));
    }

    /** 改账号 ID。需邮箱验证码；成功后所有旧令牌失效。 */
    @PutMapping("/username")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> changeUsername(@RequestBody Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : payload;
        return ApiResponse.ok(userCredentialService.changeUsername(
                CurrentUserContext.require().id(),
                text(data.get("code")),
                text(data.get("newUsername"))));
    }

    /** 改密码。需当前密码 + 邮箱验证码；成功后所有旧令牌失效。 */
    @PutMapping("/password")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> changePassword(@RequestBody Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : payload;
        return ApiResponse.ok(userCredentialService.changePassword(
                CurrentUserContext.require().id(),
                text(data.get("code")),
                text(data.get("oldPassword")),
                text(data.get("newPassword")),
                text(data.get("confirmPassword"))));
    }

    /** 绑定或更换邮箱。验证码发往新邮箱。 */
    @PutMapping("/email")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> bindEmail(@RequestBody Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : payload;
        return ApiResponse.ok(userCredentialService.bindEmail(
                CurrentUserContext.require().id(),
                text(data.get("email")),
                text(data.get("code"))));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
