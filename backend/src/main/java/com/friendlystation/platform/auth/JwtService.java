package com.friendlystation.platform.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final long expireMinutes;

    public JwtService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.token-expire-minutes:120}") long expireMinutes
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
    }

    /**
     * 签发令牌。
     *
     * @param tokenVersion 账号当前的凭据版本。改账号 ID 或密码后该值会 +1，
     *                     旧令牌因版本不符在拦截器处被拒——这是"改完强制重新登录"的实现方式。
     */
    public String issue(AccountUser user, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim("username", user.username())
                .claim("role", user.role())
                .claim("ver", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireMinutes * 60)))
                .signWith(signingKey)
                .compact();
    }

    public CurrentUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Integer version = claims.get("ver", Integer.class);
        return new CurrentUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class),
                version == null ? 0 : version
        );
    }

    /** 续签：沿用令牌里的版本，续签不会让其他设备失效。 */
    public String refresh(String token) {
        CurrentUser currentUser = parse(token);
        return issue(new AccountUser(
                currentUser.id(),
                currentUser.username(),
                null,
                null,
                currentUser.role(),
                null,
                null,
                null
        ), currentUser.tokenVersion());
    }
}
