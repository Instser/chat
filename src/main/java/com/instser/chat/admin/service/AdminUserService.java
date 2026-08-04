package com.instser.chat.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.instser.chat.admin.audit.AdminAuditPublisher;
import com.instser.chat.admin.common.AdminAuthSupport;
import com.instser.chat.admin.common.AdminErrorCodes;
import com.instser.chat.admin.common.PageResult;
import com.instser.chat.admin.common.SensitiveMasker;
import com.instser.chat.admin.dto.AdminPackageActionRequest;
import com.instser.chat.admin.dto.AdminUserRoleRequest;
import com.instser.chat.admin.dto.AdminUserStatusRequest;
import com.instser.chat.admin.query.AdminUserQuery;
import com.instser.chat.admin.vo.AdminUserDetailVO;
import com.instser.chat.admin.vo.AdminUserSummaryVO;
import com.instser.chat.common.result.Result;
import com.instser.chat.component.ChatLimitCacheManager;
import com.instser.chat.dao.UserOrderDao;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.User;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.dao.ChatSessionDao;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.service.UserService;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserService userService;
    private final UserAccountService userAccountService;
    private final PackageService packageService;
    private final UserOrderDao userOrderDao;
    private final ChatSessionDao chatSessionDao;
    private final ChatLimitCacheManager chatLimitCacheManager;
    private final AdminAuditPublisher adminAuditPublisher;

    public PageResult<AdminUserSummaryVO> listUsers(AdminUserQuery query) {
        Page<User> page = new Page<>(query.safePage(), query.safeSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getEmail, keyword)
                    .or().eq(User::getId, tryParseLong(keyword)));
        }
        if (StringUtils.hasText(query.getRole())) {
            wrapper.eq(User::getRole, query.getRole().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(User::getStatus, query.getStatus());
        }
        if (query.getCreatedFrom() != null) {
            wrapper.ge(User::getCreatedTime, query.getCreatedFrom());
        }
        if (query.getCreatedTo() != null) {
            wrapper.le(User::getCreatedTime, query.getCreatedTo());
        }

        String sortBy = query.getSortBy();
        boolean asc = query.isAsc();
        if ("lastLoginTime".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, asc, User::getLastLoginTime);
        } else if ("username".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, asc, User::getUsername);
        } else {
            wrapper.orderBy(true, asc, User::getCreatedTime);
        }

        Page<User> result = userService.page(page, wrapper);
        Map<Long, UserAccount> accountMap = loadAccounts(result.getRecords());
        return PageResult.of(result, user -> toSummary(user, accountMap.get(user.getId())));
    }

    public AdminUserDetailVO getUserDetail(Long userId) {
        User user = requireUser(userId);
        UserAccount account = userAccountService.getUserAccount(userId);
        return toDetail(user, account);
    }

    @Transactional
    public Result<AdminUserDetailVO> updateStatus(Long userId, AdminUserStatusRequest request) {
        User user = requireUser(userId);
        Long operatorId = AdminAuthSupport.requireAdminUserId();
        if (Objects.equals(userId, operatorId) && request.getStatus() != null && request.getStatus() == 0) {
            adminAuditPublisher.failed("USER_STATUS_CHANGE", String.valueOf(userId), "self-disable denied");
            return new Result<>(AdminErrorCodes.PERMISSION_DENIED, "不能禁用当前登录管理员自己");
        }
        Integer oldStatus = user.getStatus();
        user.setStatus(request.getStatus());
        user.setUpdateTime(LocalDateTime.now());
        userService.updateById(user);
        adminAuditPublisher.success("USER_STATUS_CHANGE", String.valueOf(userId),
                "old=" + oldStatus + ",new=" + request.getStatus() + ",reason=" + request.getReason());
        return Result.success(getUserDetail(userId), "用户状态已更新");
    }

    @Transactional
    public Result<AdminUserDetailVO> updateRole(Long userId, AdminUserRoleRequest request) {
        User user = requireUser(userId);
        String oldRole = user.getRole();
        String newRole = request.getRole().trim().toLowerCase();

        if ("admin".equalsIgnoreCase(oldRole) && "user".equalsIgnoreCase(newRole)) {
            long adminCount = userService.count(new LambdaQueryWrapper<User>()
                    .eq(User::getRole, "admin")
                    .eq(User::getStatus, 1));
            if (adminCount <= 1) {
                adminAuditPublisher.failed("USER_ROLE_CHANGE", String.valueOf(userId), "last admin protected");
                return new Result<>(AdminErrorCodes.LAST_ADMIN_PROTECTED, "禁止取消最后一个可用管理员");
            }
        }

        user.setRole(newRole);
        user.setUpdateTime(LocalDateTime.now());
        userService.updateById(user);
        adminAuditPublisher.success("USER_ROLE_CHANGE", String.valueOf(userId),
                "old=" + oldRole + ",new=" + newRole + ",reason=" + request.getReason());
        return Result.success(getUserDetail(userId), "用户角色已更新");
    }

    @Transactional
    public Result<AdminUserDetailVO> activatePackage(Long userId, AdminPackageActionRequest request) {
        requireUser(userId);
        Package pkg = requirePackage(request.getPackageId());
        Integer days = request.getValidityDays() != null ? request.getValidityDays() : pkg.getValidityDays();
        Integer chatCount = request.getChatCount() != null ? request.getChatCount() : pkg.getChatCount();
        boolean ok = userAccountService.activatePackage(userId, pkg.getId(), days, chatCount);
        if (!ok) {
            adminAuditPublisher.failed("USER_PACKAGE_ACTIVATE", String.valueOf(userId), request.getReason());
            return new Result<>(AdminErrorCodes.PACKAGE_CONFLICT, "激活套餐失败");
        }
        chatLimitCacheManager.refreshUserPackageCaches(userId, pkg.getId());
        adminAuditPublisher.success("USER_PACKAGE_ACTIVATE", String.valueOf(userId),
                "packageId=" + pkg.getId() + ",reason=" + request.getReason());
        return Result.success(getUserDetail(userId), "套餐已激活");
    }

    @Transactional
    public Result<AdminUserDetailVO> renewPackage(Long userId, AdminPackageActionRequest request) {
        requireUser(userId);
        Package pkg = requirePackage(request.getPackageId());
        Integer days = request.getValidityDays() != null ? request.getValidityDays() : pkg.getValidityDays();
        Integer chatCount = request.getChatCount() != null ? request.getChatCount() : 0;
        boolean ok = userAccountService.renewPackage(userId, pkg.getId(), days, chatCount);
        if (!ok) {
            adminAuditPublisher.failed("USER_PACKAGE_RENEW", String.valueOf(userId), request.getReason());
            return new Result<>(AdminErrorCodes.PACKAGE_CONFLICT, "续期套餐失败");
        }
        chatLimitCacheManager.refreshUserPackageCaches(userId, pkg.getId());
        adminAuditPublisher.success("USER_PACKAGE_RENEW", String.valueOf(userId),
                "packageId=" + pkg.getId() + ",reason=" + request.getReason());
        return Result.success(getUserDetail(userId), "套餐已续期");
    }

    @Transactional
    public Result<AdminUserDetailVO> changePackage(Long userId, AdminPackageActionRequest request) {
        requireUser(userId);
        Package pkg = requirePackage(request.getPackageId());
        Integer days = request.getValidityDays() != null ? request.getValidityDays() : pkg.getValidityDays();
        Integer chatCount = request.getChatCount() != null ? request.getChatCount() : pkg.getChatCount();
        boolean ok = userAccountService.upgradePackage(userId, pkg.getId(), days, chatCount);
        if (!ok) {
            adminAuditPublisher.failed("USER_PACKAGE_CHANGE", String.valueOf(userId), request.getReason());
            return new Result<>(AdminErrorCodes.PACKAGE_CONFLICT, "更换套餐失败");
        }
        chatLimitCacheManager.refreshUserPackageCaches(userId, pkg.getId());
        adminAuditPublisher.success("USER_PACKAGE_CHANGE", String.valueOf(userId),
                "packageId=" + pkg.getId() + ",reason=" + request.getReason());
        return Result.success(getUserDetail(userId), "套餐已更换");
    }

    public PageResult<AdminOrderBriefVO> listUserOrders(Long userId, long page, long size) {
        requireUser(userId);
        Page<UserOrder> orderPage = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        LambdaQueryWrapper<UserOrder> wrapper = new LambdaQueryWrapper<UserOrder>()
                .eq(UserOrder::getUserId, userId)
                .orderByDesc(UserOrder::getCreateTime);
        Page<UserOrder> result = userOrderDao.selectPage(orderPage, wrapper);
        return PageResult.of(result, this::toOrderBrief);
    }

    public PageResult<AdminSessionBriefVO> listUserSessions(Long userId, long page, long size) {
        requireUser(userId);
        Page<ChatSession> sessionPage = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getUpdateTime);
        Page<ChatSession> result = chatSessionDao.selectPage(sessionPage, wrapper);
        return PageResult.of(result, this::toSessionBrief);
    }

    private Map<Long, UserAccount> loadAccounts(List<User> users) {
        Map<Long, UserAccount> map = new HashMap<>();
        if (users == null || users.isEmpty()) {
            return map;
        }
        for (User user : users) {
            UserAccount account = userAccountService.getUserAccount(user.getId());
            if (account != null) {
                map.put(user.getId(), account);
            }
        }
        return map;
    }

    private AdminUserSummaryVO toSummary(User user, UserAccount account) {
        return AdminUserSummaryVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(SensitiveMasker.maskEmail(user.getEmail()))
                .role(user.getRole())
                .status(user.getStatus())
                .lastLoginTime(user.getLastLoginTime())
                .createdTime(user.getCreatedTime())
                .packageId(account != null ? account.getPackageId() : null)
                .chatCountRemaining(account != null ? account.getChatCountRemaining() : null)
                .packageExpireTime(account != null ? account.getPackageExpireTime() : null)
                .build();
    }

    private AdminUserDetailVO toDetail(User user, UserAccount account) {
        return AdminUserDetailVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(SensitiveMasker.maskEmail(user.getEmail()))
                .avatar(user.getAvatar())
                .role(user.getRole())
                .status(user.getStatus())
                .lastLoginTime(user.getLastLoginTime())
                .createdTime(user.getCreatedTime())
                .updateTime(user.getUpdateTime())
                .packageId(account != null ? account.getPackageId() : null)
                .chatCountRemaining(account != null ? account.getChatCountRemaining() : null)
                .totalChatCount(account != null ? account.getTotalChatCount() : null)
                .packageExpireTime(account != null ? account.getPackageExpireTime() : null)
                .tokenBalance(account != null ? account.getTokenBalance() : null)
                .tokenUsed(account != null ? account.getTokenUsed() : null)
                .membershipLevel(account != null ? account.getMembershipLevel() : null)
                .membershipExpireTime(account != null ? account.getMembershipExpireTime() : null)
                .totalAmountSpent(account != null ? account.getTotalAmountSpent() : null)
                .packageRemainingDays(account != null ? account.getRemainingDays() : null)
                .build();
    }

    private AdminOrderBriefVO toOrderBrief(UserOrder order) {
        return AdminOrderBriefVO.builder()
                .orderNo(order.getOrderNo())
                .packageId(order.getPackageId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .createTime(order.getCreateTime())
                .paymentTime(order.getPaymentTime())
                .build();
    }

    private AdminSessionBriefVO toSessionBrief(ChatSession session) {
        return AdminSessionBriefVO.builder()
                .sessionId(session.getSessionId())
                .title(session.getTitle())
                .createTime(session.getCreatedTime())
                .updateTime(session.getUpdateTime())
                .build();
    }

    private User requireUser(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        return user;
    }

    private Package requirePackage(Long packageId) {
        Package pkg = packageService.getPackageById(packageId);
        if (pkg == null) {
            throw new IllegalArgumentException("套餐不存在: " + packageId);
        }
        return pkg;
    }

    private Long tryParseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    @Data
    @Builder
    public static class AdminOrderBriefVO {
        private String orderNo;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long packageId;
        private java.math.BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private Integer paymentStatus;
        private LocalDateTime createTime;
        private LocalDateTime paymentTime;
    }

    @Data
    @Builder
    public static class AdminSessionBriefVO {
        private String sessionId;
        private String title;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }
}
