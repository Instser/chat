package com.instser.chat.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.instser.chat.admin.audit.AdminAuditPublisher;
import com.instser.chat.admin.common.AdminErrorCodes;
import com.instser.chat.admin.dto.AdminPackageUpsertRequest;
import com.instser.chat.admin.dto.AdminStatusRequest;
import com.instser.chat.admin.vo.AdminPackageVO;
import com.instser.chat.common.result.Result;
import com.instser.chat.component.ChatLimitCacheManager;
import com.instser.chat.entity.Package;
import com.instser.chat.service.PackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPackageService {

    private final PackageService packageService;
    private final ChatLimitCacheManager chatLimitCacheManager;
    private final AdminAuditPublisher adminAuditPublisher;

    public List<AdminPackageVO> listAll() {
        return packageService.list(new LambdaQueryWrapper<Package>()
                        .orderByAsc(Package::getSortOrder)
                        .orderByDesc(Package::getCreatedTime))
                .stream()
                .map(this::toVo)
                .collect(Collectors.toList());
    }

    public AdminPackageVO getById(Long packageId) {
        return toVo(requirePackage(packageId));
    }

    @Transactional
    public Result<AdminPackageVO> create(AdminPackageUpsertRequest request) {
        Package pkg = new Package();
        apply(pkg, request);
        pkg.setCreatedTime(LocalDateTime.now());
        pkg.setUpdateTime(LocalDateTime.now());
        packageService.save(pkg);
        adminAuditPublisher.success("PACKAGE_CREATE", String.valueOf(pkg.getId()),
                "name=" + pkg.getPackageName() + ",reason=" + request.getReason());
        return Result.success(toVo(pkg), "套餐已创建");
    }

    @Transactional
    public Result<AdminPackageVO> update(Long packageId, AdminPackageUpsertRequest request) {
        Package pkg = requirePackage(packageId);
        apply(pkg, request);
        pkg.setUpdateTime(LocalDateTime.now());
        packageService.updateById(pkg);
        chatLimitCacheManager.refreshUserPackageCaches(null, packageId);
        adminAuditPublisher.success("PACKAGE_UPDATE", String.valueOf(packageId),
                "name=" + pkg.getPackageName() + ",reason=" + request.getReason());
        return Result.success(toVo(pkg), "套餐已更新");
    }

    @Transactional
    public Result<AdminPackageVO> updateStatus(Long packageId, AdminStatusRequest request) {
        Package pkg = requirePackage(packageId);
        Integer old = pkg.getIsEnabled();
        pkg.setIsEnabled(request.getStatus());
        pkg.setUpdateTime(LocalDateTime.now());
        packageService.updateById(pkg);
        chatLimitCacheManager.refreshUserPackageCaches(null, packageId);
        adminAuditPublisher.success("PACKAGE_STATUS", String.valueOf(packageId),
                "old=" + old + ",new=" + request.getStatus() + ",reason=" + request.getReason());
        return Result.success(toVo(pkg), "套餐状态已更新");
    }

    @Transactional
    public Result<List<AdminPackageVO>> sort(List<Long> orderedIds, String reason) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return new Result<>(AdminErrorCodes.PACKAGE_CONFLICT, "排序列表不能为空");
        }
        int order = 1;
        for (Long id : orderedIds) {
            Package pkg = packageService.getById(id);
            if (pkg == null) {
                continue;
            }
            pkg.setSortOrder(order++);
            pkg.setUpdateTime(LocalDateTime.now());
            packageService.updateById(pkg);
            chatLimitCacheManager.refreshUserPackageCaches(null, id);
        }
        adminAuditPublisher.success("PACKAGE_SORT", "batch",
                "count=" + orderedIds.size() + ",reason=" + reason);
        return Result.success(listAll().stream()
                .sorted(Comparator.comparing(AdminPackageVO::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList()), "套餐排序已更新");
    }

    private void apply(Package pkg, AdminPackageUpsertRequest request) {
        pkg.setPackageName(request.getPackageName());
        pkg.setDescription(request.getDescription());
        pkg.setPackageType(request.getPackageType());
        pkg.setPrice(request.getPrice());
        pkg.setDiscountPrice(request.getDiscountPrice());
        pkg.setValidityDays(request.getValidityDays());
        pkg.setLimitType(request.getLimitType());
        pkg.setModelPermissions(request.getModelPermissions());
        pkg.setFeatures(request.getFeatures());
        if (request.getIsEnabled() != null) {
            pkg.setIsEnabled(request.getIsEnabled());
        } else if (pkg.getIsEnabled() == null) {
            pkg.setIsEnabled(1);
        }
        if (request.getSortOrder() != null) {
            pkg.setSortOrder(request.getSortOrder());
        }
    }

    private Package requirePackage(Long packageId) {
        Package pkg = packageService.getById(packageId);
        if (pkg == null) {
            throw new IllegalArgumentException("套餐不存在: " + packageId);
        }
        return pkg;
    }

    private AdminPackageVO toVo(Package pkg) {
        return AdminPackageVO.builder()
                .id(pkg.getId())
                .packageName(pkg.getPackageName())
                .description(pkg.getDescription())
                .packageType(pkg.getPackageType())
                .price(pkg.getPrice())
                .discountPrice(pkg.getDiscountPrice())
                .validityDays(pkg.getValidityDays())
                .limitType(pkg.getLimitType())
                .modelPermissions(pkg.getModelPermissions())
                .features(pkg.getFeatures())
                .isEnabled(pkg.getIsEnabled())
                .sortOrder(pkg.getSortOrder())
                .chatCount(pkg.getChatCount())
                .dailyChatLimit(pkg.getDailyChatLimit())
                .createdTime(pkg.getCreatedTime())
                .updateTime(pkg.getUpdateTime())
                .build();
    }
}
