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

    public String issue(AccountUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim("username", user.username())
                .claim("role", user.role())
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
        return new CurrentUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class)
        );
    }

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
        ));
    }
}
