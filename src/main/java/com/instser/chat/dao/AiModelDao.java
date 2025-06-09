package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.AiModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI模型数据访问层
 *
 * @author instser
 * @since 2024-01-01
 */
@Mapper
public interface AiModelDao extends BaseMapper<AiModel> {

    /**
     * 根据模型代码查询模型
     *
     * @param modelCode 模型代码
     * @return 模型信息
     */
    AiModel selectByModelCode(@Param("modelCode") String modelCode);

    /**
     * 查询可用的模型列表
     *
     * @return 可用模型列表
     */
    List<AiModel> selectAvailableModels();
}
