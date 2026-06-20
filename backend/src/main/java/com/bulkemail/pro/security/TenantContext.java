package com.bulkemail.pro.security;

public class TenantContext {

    private static final ThreadLocal<Long> ORGANIZATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_EMAIL = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ROLE = new ThreadLocal<>();

    public static void set(Long organizationId, String email, String role) {
        ORGANIZATION_ID.set(organizationId);
        USER_EMAIL.set(email);
        USER_ROLE.set(role);
    }

    public static Long getOrganizationId() {
        return ORGANIZATION_ID.get();
    }

    public static String getUserEmail() {
        return USER_EMAIL.get();
    }

    public static String getUserRole() {
        return USER_ROLE.get();
    }

    public static void clear() {
        ORGANIZATION_ID.remove();
        USER_EMAIL.remove();
        USER_ROLE.remove();
    }
}
