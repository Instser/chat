package com.instser.chat.admin.common;

/**
 * 管理端脱敏工具：密钥/邮箱等只返回掩码，不回显明文。
 */
public final class SensitiveMasker {

    private SensitiveMasker() {
    }

    public static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        String value = secret.trim();
        if (value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }

    public static boolean hasSecret(String secret) {
        return secret != null && !secret.isBlank();
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 2) {
            return "*@" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + domain;
    }
}
