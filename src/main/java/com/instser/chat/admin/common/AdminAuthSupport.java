package com.instser.chat.admin.common;

import com.instser.chat.entity.User;
import com.instser.chat.security.UserDetailsServiceImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 管理员接口获取当前登录主体的辅助方法。
 */
public final class AdminAuthSupport {

    private AdminAuthSupport() {
    }

    public static UserDetailsServiceImpl.CustomUserDetails requireAdminPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsServiceImpl.CustomUserDetails details)) {
            throw new AccessDeniedException("未登录或管理员身份无效");
        }
        boolean isAdmin = details.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()));
        if (!isAdmin) {
            throw new AccessDeniedException("需要管理员权限");
        }
        return details;
    }

    public static Long requireAdminUserId() {
        return requireAdminPrincipal().getUserId();
    }

    public static String requireAdminUsername() {
        return requireAdminPrincipal().getUsername();
    }

    public static User requireAdminUser() {
        return requireAdminPrincipal().getUser();
    }
}
