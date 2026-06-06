package com.yuan.utils;

import org.springframework.stereotype.Component;

@Component
public class UserContext {
    private static final ThreadLocal<String> USER_ROLE  = new ThreadLocal<>();

    public static void setRole(String userId) {
        USER_ROLE .set(userId);
    }

    public static String getRole() {
        return USER_ROLE .get();
    }

    public static void clear() {
        USER_ROLE .remove();
    }
}
