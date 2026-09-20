package com.sharp.common;

/** 当前请求的登录用户名（由 AuthInterceptor 设置，控制器读取）。 */
public final class CurrentUser {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static void set(String username) {
        HOLDER.set(username);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
