package com.yuan.utils;

import org.springframework.stereotype.Component;

@Component
public class UserContext {
    private static final ThreadLocal<String> USER_ROLE= new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID= new ThreadLocal<>();

    public static void set(String role,String userId) {
        USER_ROLE.set(role);
        USER_ID.set(userId);
    }

    public static void setRole(String role) {
        USER_ROLE.set(role);
    }

    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    public static String getRole() {
        return USER_ROLE.get();
    }

    public static void clear() {
        USER_ROLE.remove();
        USER_ID.remove();
    }
}
