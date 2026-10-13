package com.friendlystation.platform.auth;

public final class CurrentUserContext {
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static CurrentUser require() {
        CurrentUser user = get();
        if (user == null) {
            throw new IllegalStateException("当前请求未认证");
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
