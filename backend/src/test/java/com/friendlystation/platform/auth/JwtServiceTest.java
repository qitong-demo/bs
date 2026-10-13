package com.friendlystation.platform.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private final JwtService jwtService = new JwtService(
            "friendly-station-demo-secret-key-at-least-32-bytes",
            120
    );

    @Test
    void issuesAndParsesRoleClaims() {
        AccountUser user = new AccountUser(7L, "station_admin_01", "hash", "13800002089",
                "STATION_ADMIN", "陈管理员", "驿站管理员", "拱墅区");

        String token = jwtService.issue(user);
        CurrentUser parsed = jwtService.parse(token);

        assertEquals(7L, parsed.id());
        assertEquals("station_admin_01", parsed.username());
        assertEquals("STATION_ADMIN", parsed.role());
    }

    @Test
    void rejectsInvalidToken() {
        assertThrows(JwtException.class, () -> jwtService.parse("invalid.token.value"));
    }

    @Test
    void refreshesAValidToken() {
        AccountUser user = new AccountUser(7L, "rider_xiaowang", "hash", "13800002088",
                "USER", "骑手小王", "外卖骑手", "西湖区");

        CurrentUser refreshed = jwtService.parse(jwtService.refresh(jwtService.issue(user)));

        assertEquals(user.id(), refreshed.id());
        assertEquals(user.username(), refreshed.username());
        assertEquals(user.role(), refreshed.role());
    }
}
