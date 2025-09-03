package com.instser.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 套餐缓存DTO - 精简版本，只包含权限验证所需的核心字段
 *
 * @author instser
 * @since 2025-09-04
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PackageCacheDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 套餐ID
     */
    private Long packageId;

    /**
     * 套餐名称
     */
    private String packageName;

    /**
     * 限制类型（daily-每日限制，total-总次数限制）
     */
    private String limitType;

    /**
     * 模型限制配置 - 精简格式
     * Key: 模型ID (String)
     * Value: 每日限制次数 (Integer)，-1表示无限制
     * 
     * 例如：{"1": 30, "2": 20, "3": 5}
     */
    private Map<String, Integer> modelLimits;

    /**
     * 是否启用（0-禁用，1-启用）
     */
    private Integer isEnabled;

    /**
     * 从完整Package对象创建缓存DTO
     */
    public static PackageCacheDTO fromPackage(com.instser.chat.entity.Package packageEntity) {
        if (packageEntity == null) {
            return null;
        }

        PackageCacheDTO dto = new PackageCacheDTO();
        dto.setPackageId(packageEntity.getId());
        dto.setPackageName(packageEntity.getPackageName());
        dto.setLimitType(packageEntity.getLimitType());
        dto.setIsEnabled(packageEntity.getIsEnabled());

        // 转换模型权限配置为精简格式
        Map<String, com.instser.chat.entity.Package.ModelPermission> permissions = packageEntity.getModelPermissionsMap();
        if (permissions != null) {
            Map<String, Integer> simplifiedLimits = new java.util.HashMap<>();
            permissions.forEach((modelId, permission) -> {
                if (permission != null) {
                    simplifiedLimits.put(modelId, permission.getDailyLimit());
                }
            });
            dto.setModelLimits(simplifiedLimits);
        }

        return dto;
    }

    /**
     * 获取指定模型的每日限制次数
     */
    public Integer getModelDailyLimit(String modelId) {
        if (modelLimits == null) {
            return 0;
        }
        return modelLimits.getOrDefault(modelId, 0);
    }

    /**
     * 检查是否有指定模型的权限
     */
    public boolean hasModelPermission(String modelId) {
        if (modelLimits == null) {
            return false;
        }
        Integer limit = modelLimits.get(modelId);
        return limit != null && limit != 0;
    }
}