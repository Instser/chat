package com.instser.chat.admin.service;

import com.instser.chat.admin.dto.AdminModelKeyAddRequest;
import com.instser.chat.admin.dto.AdminModelKeyUpdateRequest;
import com.instser.chat.admin.vo.AdminModelKeyVO;
import com.instser.chat.common.result.Result;

import java.util.List;

/**
 * 管理端-模型API密钥池服务接口。
 *
 * <p>轮询维度为单个模型行（{@code t_ai_model.id}）：每行模型可配多个 key，
 * 在行内按有效权重加权随机。所有写操作都会失效该模型行的本地密钥池缓存并记录审计，
 * 对外一律只返回脱敏密钥。</p>
 *
 * @author instser
 * @since 2026-09-16
 */
public interface AdminModelKeyService {

    /**
     * 某模型行的密钥池列表（按创建时间倒序）。
     */
    List<AdminModelKeyVO> listByModelId(Long modelId);

    /**
     * 为某模型行新增一个密钥；同模型下未删除记录内 api_key 不允许重复。
     */
    Result<AdminModelKeyVO> add(Long modelId, AdminModelKeyAddRequest request);

    /**
     * 更新密钥的权重/状态/备注（均可选）。
     */
    Result<AdminModelKeyVO> update(Long keyId, AdminModelKeyUpdateRequest request);

    /**
     * 逻辑删除密钥。
     */
    Result<Void> delete(Long keyId);

    /**
     * 清零密钥的统计（累计成功/失败、连续失败、最近错误与相关时间）。
     */
    Result<AdminModelKeyVO> resetStats(Long keyId);
}
