package com.friendlystation.platform.auth;

public interface AccountService {
    AccountUser findByUsername(String username);

    AccountUser register(String username, String phone, String password);
}
