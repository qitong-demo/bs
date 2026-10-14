package com.friendlystation.platform.auth;

/**
 * 当前登录用户。由 JWT 解析得到，因此**不含密码等敏感信息**。
 *
 * @param tokenVersion 令牌里带的凭据版本。拦截器会与账号当前版本比对，
 *                     不一致说明账号 ID 或密码已被修改，该令牌必须失效。
 */
public record CurrentUser(Long id, String username, String role, int tokenVersion) {
}
