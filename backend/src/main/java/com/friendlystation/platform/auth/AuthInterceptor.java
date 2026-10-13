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

    public AuthInterceptor(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
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
