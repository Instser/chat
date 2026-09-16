package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.AiModelKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型API密钥池数据访问层
 *
 * <p>基于 MyBatis-Plus {@code BaseMapper}：逻辑删除（{@code deleted}）由实体上的
 * {@code @TableLogic} 自动过滤，查询无需显式追加条件。</p>
 *
 * @author instser
 * @since 2026-09-16
 */
@Mapper
public interface AiModelKeyMapper extends BaseMapper<AiModelKey> {
}
