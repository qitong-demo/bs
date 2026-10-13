package com.friendlystation.platform.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {
    private final PasswordService passwordService = new PasswordService();

    @Test
    void encodesAndVerifiesPasswordWithoutStoringPlaintext() {
        String encoded = passwordService.encode("password");

        assertNotEquals("password", encoded);
        assertTrue(passwordService.matches("password", encoded));
        assertFalse(passwordService.matches("wrong-password", encoded));
    }
}
