package com.instser.chat.service;

import java.io.Serializable;
import java.util.List;

/**
 * 基础服务接口
 *
 * @author instser
 * @since 2024-01-01
 */
public interface BaseService<T> {

    /**
     * 保存实体
     *
     * @param entity 实体
     * @return 是否成功
     */
    boolean save(T entity);

    /**
     * 根据ID删除
     *
     * @param id 主键ID
     * @return 是否成功
     */
    boolean removeById(Serializable id);

    /**
     * 根据ID更新
     *
     * @param entity 实体
     * @return 是否成功
     */
    boolean updateById(T entity);

    /**
     * 根据ID查询
     *
     * @param id 主键ID
     * @return 实体
     */
    T getById(Serializable id);

    /**
     * 查询所有
     *
     * @return 实体列表
     */
    List<T> list();

    /**
     * 统计数量
     *
     * @return 数量
     */
    long count();
}
