package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户数据访问层
 *
 * @author instser
 * @since 2024-01-01
 */
@Mapper
public interface UserDao extends BaseMapper<User> {

    /**
     * 根据邮箱查询用户
     *
     * @param email 邮箱
     * @return 用户信息
     */
    User selectByEmail(@Param("email") String email);

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    User selectByUsername(@Param("username") String username);
}
