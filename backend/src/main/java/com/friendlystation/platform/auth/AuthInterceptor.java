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

    public AuthInterceptor(JwtService jwtService, ObjectMapper objectMapper,
                           CredentialVersionService credentialVersionService) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.credentialVersionService = credentialVersionService;
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
            CurrentUserContext.set(currentUser);
            if (required != null && Arrays.stream(required.value()).noneMatch(currentUser.role()::equals)) {
                writeError(response, 403, "当前角色无权执行此操作");
                return false;
            }
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
