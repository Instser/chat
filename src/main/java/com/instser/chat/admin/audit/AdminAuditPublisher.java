package com.instser.chat.admin.audit;

import com.instser.chat.admin.common.AdminAuthSupport;
import com.instser.chat.event.OperationAuditEvent;
import com.instser.chat.security.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 管理员操作审计发布器。
 * 写入 t_operation_audit（异步），不阻塞主流程。
 */
@Component
@RequiredArgsConstructor
public class AdminAuditPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void success(String operationType, String target, String targetDetail) {
        publish(operationType, target, targetDetail, "SUCCESS");
    }

    public void failed(String operationType, String target, String targetDetail) {
        publish(operationType, target, targetDetail, "FAILED");
    }

    public void publish(String operationType, String target, String targetDetail, String result) {
        UserDetailsServiceImpl.CustomUserDetails principal;
        try {
            principal = AdminAuthSupport.requireAdminPrincipal();
        } catch (Exception ex) {
            principal = null;
        }

        String operator = principal != null ? principal.getUsername() : "system";
        Long operatorId = principal != null ? principal.getUserId() : null;
        String clientIp = resolveClientIp();

        applicationEventPublisher.publishEvent(new OperationAuditEvent(
                operator,
                operatorId,
                operationType,
                target,
                targetDetail,
                result,
                clientIp,
                MDC.get("traceId"),
                LocalDateTime.now()
        ));
    }

    private String resolveClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
