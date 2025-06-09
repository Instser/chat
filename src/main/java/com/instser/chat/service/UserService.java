package com.instser.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.instser.chat.entity.User;

/**
 * 用户服务接口
 *
 * @author instser
 * @since 2024-01-01
 */
public interface UserService extends IService<User> {

    /**
     * 根据邮箱查询用户
     *
     * @param email 邮箱
     * @return 用户信息
     */
    User getUserByEmail(String email);

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    User getUserByUsername(String username);

    /**
     * 注册新用户
     *
     * @param username 用户名
     * @param email    邮箱
     * @param password 密码
     * @return 用户ID
     */
    Long registerUser(String username, String email, String password);

    /**
     * 用户登录
     *
     * @param email    邮箱
     * @param password 密码
     * @return 用户信息
     */
    User login(String email, String password);

    /**
     * 更新最后登录时间
     *
     * @param userId 用户ID
     */
    void updateLastLoginTime(Long userId);

    /**
     * 初始化默认用户
     */
    void initDefaultUser();
}
