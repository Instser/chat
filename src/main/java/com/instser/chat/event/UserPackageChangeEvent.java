package com.instser.chat.event;

import lombok.Data;
import org.springframework.context.ApplicationEvent;

/**
 * 用户套餐变更事件
 *
 * @author instser
 * @since 2025-09-03
 */
@Data
public class UserPackageChangeEvent extends ApplicationEvent {
    
    private final Long userId;
    private final Long oldPackageId;
    private final Long newPackageId;
    private final String changeType; // ACTIVATE, RENEW, UPGRADE, EXPIRE
    
    public UserPackageChangeEvent(Object source, Long userId, Long oldPackageId, Long newPackageId, String changeType) {
        super(source);
        this.userId = userId;
        this.oldPackageId = oldPackageId;
        this.newPackageId = newPackageId;
        this.changeType = changeType;
    }
    
    public static UserPackageChangeEvent createActivateEvent(Object source, Long userId, Long packageId) {
        return new UserPackageChangeEvent(source, userId, null, packageId, "ACTIVATE");
    }
    
    public static UserPackageChangeEvent createRenewEvent(Object source, Long userId, Long packageId) {
        return new UserPackageChangeEvent(source, userId, packageId, packageId, "RENEW");
    }
    
    public static UserPackageChangeEvent createUpgradeEvent(Object source, Long userId, Long oldPackageId, Long newPackageId) {
        return new UserPackageChangeEvent(source, userId, oldPackageId, newPackageId, "UPGRADE");
    }
    
    public static UserPackageChangeEvent createExpireEvent(Object source, Long userId, Long packageId) {
        return new UserPackageChangeEvent(source, userId, packageId, null, "EXPIRE");
    }
}