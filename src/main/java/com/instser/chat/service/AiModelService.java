package com.instser.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.instser.chat.entity.AiModel;

import java.util.List;

/**
 * AI模型服务接口
 *
 * @author instser
 * @since 2024-01-01
 */
public interface AiModelService extends IService<AiModel> {

    /**
     * 根据模型代码查询模型
     *
     * @param modelCode 模型代码
     * @return 模型信息
     */
    AiModel getModelByCode(String modelCode);

    /**
     * 获取可用的模型列表
     *
     * @return 可用模型列表
     */
    List<AiModel> getAvailableModels();

    /**
     * 初始化默认模型数据
     */
    void initDefaultModels();
}
