package com.friendlystation.platform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.friendlystation.platform.common.ApiResponse;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final CredentialVersionService credentialVersionService;
    private final com.friendlystation.platform.admin.AdminPermissionService adminPermissionService;
    private final AccountService accountService;

    public AuthInterceptor(JwtService jwtService, ObjectMapper objectMapper,
                           CredentialVersionService credentialVersionService,
                           com.friendlystation.platform.admin.AdminPermissionService adminPermissionService,
                           AccountService accountService) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.credentialVersionService = credentialVersionService;
        this.adminPermissionService = adminPermissionService;
        this.accountService = accountService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole required = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }

        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            if (required != null) {
                writeError(response, 401, "请先登录");
                return false;
            }
            return true;
        }

        try {
            CurrentUser currentUser = jwtService.parse(token);
            // 凭据版本比对：账号 ID 或密码改过之后，旧令牌立即失效。
            // 只让前端清本地令牌是不够的——JWT 无状态，旧令牌在过期前仍然可用。
            int currentVersion = credentialVersionService.versionOf(currentUser.id());
            if (currentVersion < 0 || currentVersion != currentUser.tokenVersion()) {
                writeError(response, 401, "登录凭证已失效，请重新登录");
                return false;
            }
            // 停用账号的旧令牌同样必须失效：只拦住登录不够，JWT 在过期前本来仍然可用。
            if (!accountService.enabled(currentUser.id())) {
                writeError(response, 401, "账号已停用，请联系管理员");
                return false;
            }
            if (required != null && Arrays.stream(required.value()).noneMatch(currentUser.role()::equals)) {
                writeError(response, 403, "当前角色无权执行此操作");
                return false;
            }
            // 功能权限校验：角色对了不代表这件事能做。
            // 之前只校验角色，子账号绕过前端直接调接口即可越权——权限体系等于装饰。
            RequirePermission permission = handlerMethod.getMethodAnnotation(RequirePermission.class);
            if (permission == null) {
                permission = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
            }
            if (permission != null) {
                java.util.List<String> effective =
                        adminPermissionService.effectiveOf(currentUser.id(), currentUser.role());
                boolean allowed = Arrays.stream(permission.value()).anyMatch(effective::contains);
                if (!allowed) {
                    writeError(response, 403, "当前账号未被授予该功能权限");
                    return false;
                }
            }
            // 只有确认这次请求可以继续时才写入上下文：提前 set 后再 return false，
            // Spring 不会对返回 false 的拦截器调用 afterCompletion，线程池线程会残留上一个用户。
            CurrentUserContext.set(currentUser);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            writeError(response, 401, "登录凭证已失效，请重新登录");
            return false;
        }
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        CurrentUserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        return authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim()
                : authorization.trim();
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> payload = code == 403
                ? ApiResponse.forbidden(message)
                : ApiResponse.unauthorized(message);
        response.getWriter().write(objectMapper.writeValueAsString(payload));
    }
}
