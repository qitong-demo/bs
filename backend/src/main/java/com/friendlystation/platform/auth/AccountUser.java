package com.friendlystation.platform.auth;

public record AccountUser(
        Long id,
        String username,
        String passwordHash,
        String phone,
        String role,
        String realName,
        String jobType,
        String area
) {
}
