package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型API密钥池实体类。
 *
 * <p>同 {@code model_code} 的多行 {@code t_ai_model} 属不同模型级别（BEST/PERFORMANCE/STANDARD），
 * 不允许跨行轮换；因此轮询分组下放到「单个模型行」：本表一行 = 某模型（{@code model_id}）
 * 下的一个可用 API key，在该模型行内按有效权重加权随机选取。</p>
 *
 * <p>有效权重 = {@code max(1, weight − failPenalty × consecutiveFails)}；
 * {@code t_ai_model.api_key} 保留为「未配置密钥池时」的回退单 key。</p>
 *
 * @author instser
 * @since 2026-09-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_ai_model_key")
public class AiModelKey implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属模型行ID（t_ai_model.id，轮询维度：行内多key）
     */
    @TableField("model_id")
    private Long modelId;

    /**
     * API密钥（明文，仅服务端使用；对外接口一律脱敏）
     */
    @TableField("api_key")
    private String apiKey;

    /**
     * 备注（便于区分渠道/供应商）
     */
    @TableField("remark")
    private String remark;

    /**
     * 基础权重（1-100，越大被选中概率越高）
     */
    @TableField("weight")
    private Integer weight;

    /**
     * 状态（0-禁用，1-启用）
     */
    @TableField("status")
    private Integer status;

    /**
     * 累计成功次数
     */
    @TableField("success_count")
    private Long successCount;

    /**
     * 累计失败次数
     */
    @TableField("fail_count")
    private Long failCount;

    /**
     * 连续失败次数（成功即清零，用于动态降权）
     */
    @TableField("consecutive_fails")
    private Integer consecutiveFails;

    /**
     * 最近一次失败错误摘要（脱敏后截断255）
     */
    @TableField("last_error")
    private String lastError;

    /**
     * 最近一次使用时间
     */
    @TableField("last_used_time")
    private LocalDateTime lastUsedTime;

    /**
     * 最近一次成功时间
     */
    @TableField("last_success_time")
    private LocalDateTime lastSuccessTime;

    /**
     * 最近一次失败时间
     */
    @TableField("last_fail_time")
    private LocalDateTime lastFailTime;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

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

    public String getApiKey() {
        return apiKey == null ? null : apiKey.trim();
    }

    public String getRemark() {
        return remark == null ? null : remark.trim();
    }
}
