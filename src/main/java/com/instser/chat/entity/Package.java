package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 套餐实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_package")
public class Package implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 套餐名称
     */
    @TableField("package_name")
    private String packageName;

    /**
     * 套餐描述
     */
    @TableField("description")
    private String description;

    /**
     * 套餐类型（basic/premium/enterprise等）
     */
    @TableField("package_type")
    private String packageType;

    /**
     * 套餐价格
     */
    @TableField("price")
    private BigDecimal price;

    /**
     * 优惠价格
     */
    @TableField("discount_price")
    private BigDecimal discountPrice;

    /**
     * 有效期（天数）
     */
    @TableField("validity_days")
    private Integer validityDays;

    // 注意：chat_count 和 daily_chat_limit 字段已从数据库中删除
    // 相关功能已迁移到 model_permissions JSON配置
    // 如需访问这些值，请使用 getChatCount() 和 getDailyChatLimit() 兼容方法

    /**
     * 限制类型（daily-每日限制，total-总次数限制）
     */
    @TableField("limit_type")
    private String limitType;

    /**
     * 包含的AI模型权限（JSON格式存储模型ID列表）
     */
    @TableField("model_permissions")
    private String modelPermissions;

    /**
     * 是否启用（0-禁用，1-启用）
     */
    @TableField("is_enabled")
    private Integer isEnabled;

    /**
     * 排序权重
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标志（0-未删除，1-已删除）
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /**
     * 获取模型权限配置映射
     *
     * @return 模型权限配置映射
     */
    public Map<String, ModelPermission> getModelPermissionsMap() {
        if (!StringUtils.hasText(this.modelPermissions)) {
            return getDefaultModelPermissions();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<Map<String, ModelPermission>> typeRef =
                new TypeReference<Map<String, ModelPermission>>() {};
            return mapper.readValue(this.modelPermissions, typeRef);
        } catch (Exception e) {
            log.warn("解析套餐{}模型权限配置失败，使用默认配置", this.getId(), e);
            return getDefaultModelPermissions();
        }
    }

    /**
     * 获取套餐总聊天次数
     * 兼容方法：根据限制类型返回相应的总次数
     *
     * @return 总聊天次数
     */
    public Integer getChatCount() {
        // 如果是每日限制模式，总次数不限制
        if ("daily".equals(this.limitType)) {
            return -1; // -1表示无限制
        }

        // 其他模式：从STANDARD模型配置计算总次数
        Map<String, ModelPermission> permissions = getModelPermissionsMap();
        ModelPermission standardPermission = permissions.get("STANDARD");
        if (standardPermission != null && standardPermission.getDailyLimit() > 0) {
            // 假设总次数 = 每日限制 × 有效期天数
            return standardPermission.getDailyLimit() * (this.validityDays != null ? this.validityDays : 30);
        }

        return 1000; // 默认值
    }

    /**
     * 获取每日聊天次数限制
     * 兼容方法：从STANDARD模型权限配置获取
     *
     * @return 每日聊天次数限制
     */
    public Integer getDailyChatLimit() {
        Map<String, ModelPermission> permissions = getModelPermissionsMap();
        ModelPermission standardPermission = permissions.get("STANDARD");
        return standardPermission != null ? standardPermission.getDailyLimit() : 0;
    }

    /**
     * 获取默认模型权限配置
     *
     * @return 默认模型权限配置
     */
    private Map<String, ModelPermission> getDefaultModelPermissions() {
        Map<String, ModelPermission> defaults = new HashMap<>();

        // 根据套餐类型返回默认配置
        if (this.packageType == null) {
            return defaults;
        }

        switch (this.packageType) {
            case "basic":
                defaults.put("STANDARD", new ModelPermission(300, "标准模型"));
                defaults.put("PERFORMANCE", new ModelPermission(20, "性能模型"));
                defaults.put("BETA_MODEL", new ModelPermission(5, "测试模型"));
                break;
            case "medium":
                defaults.put("STANDARD", new ModelPermission(500, "标准模型"));
                defaults.put("PERFORMANCE", new ModelPermission(300, "性能模型"));
                defaults.put("BETA_MODEL", new ModelPermission(20, "测试模型"));
                break;
            case "high":
                defaults.put("STANDARD", new ModelPermission(-1, "标准模型"));
                defaults.put("PERFORMANCE", new ModelPermission(-1, "性能模型"));
                defaults.put("BETA_MODEL", new ModelPermission(-1, "测试模型"));
                break;
            default:
                // 默认无权限
                break;
        }

        return defaults;
    }

    /**
     * 模型权限配置类
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ModelPermission {
        /**
         * 每日限制次数（-1表示无限制）
         */
        @JsonProperty("daily_limit")
        private Integer dailyLimit;

        /**
         * 描述信息
         */
        private String description;
    }
}
