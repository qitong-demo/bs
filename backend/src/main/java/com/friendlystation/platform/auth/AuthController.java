package com.friendlystation.platform.auth;

import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accountService;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final CredentialVersionService credentialVersionService;

    public AuthController(AccountService accountService, PasswordService passwordService, JwtService jwtService,
                          CredentialVersionService credentialVersionService) {
        this.accountService = accountService;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.credentialVersionService = credentialVersionService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        AccountUser user = accountService.findByUsername(request.username());
        if (user == null || !passwordService.matches(request.password(), user.passwordHash())) {
            throw new BusinessException("用户名或密码错误");
        }
        // 签发时带上账号当前的凭据版本；账号 ID 或密码一旦被改，此令牌立即失效
        int version = Math.max(0, credentialVersionService.versionOf(user.id()));
        return ApiResponse.ok(Map.of(
                "token", jwtService.issue(user, version),
                "username", user.username(),
                "realName", user.realName() == null ? user.username() : user.realName(),
                "role", user.role(),
                "expiresIn", 120 * 60
        ));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        // 注册与改密必须用同一套规则：此前注册只校验非空，1 位密码也能注册，
        // 之后想改成同样长度却被拒绝，规则自相矛盾。
        String username = CredentialRules.requireUsername(request.username());
        String phone = CredentialRules.requirePhone(request.phone());
        String password = CredentialRules.requirePassword(request.password(), username);
        AccountUser user = accountService.register(username, phone, password);
        return ApiResponse.ok(Map.of(
                "id", user.id(),
                "username", user.username(),
                "phone", user.phone(),
                "role", user.role(),
                "status", "ENABLED"
        ));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(Map.of(
                "token", jwtService.refresh(request.token()),
                "expiresIn", 120 * 60
        ));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RegisterRequest(@NotBlank String username, @NotBlank String phone, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String token) {
    }
}
