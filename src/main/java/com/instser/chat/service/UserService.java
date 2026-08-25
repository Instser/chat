package com.instser.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.instser.chat.dto.request.UpdateProfileRequest;
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
     * 注册新用户（OAuth2专用）
     *
     * @param username 用户名
     * @param email    邮箱
     * @param password 随机密码
     * @param avatar   头像URL
     * @return 创建的用户对象
     */
    User registerUser(String username, String email, String password, String avatar);

    /**
     * 注册试用用户（一键试用，随机密码不可用于登录）
     *
     * @param username 用户名
     * @param email    邮箱
     * @param password 随机密码
     * @return 创建的用户对象
     */
    User registerGuestUser(String username, String email, String password);

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

    /**
     * 检查密码是否正确
     *
     * @param rawPassword     原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    boolean checkPassword(String rawPassword, String encodedPassword);

    /**
     * 更新用户密码
     *
     * @param userId      用户ID
     * @param newPassword 新密码
     */
    void updatePassword(Long userId, String newPassword);

    /**
     * 更新用户资料
     *
     * @param userId  用户ID
     * @param request 更新请求
     * @return 更新后的用户信息
     */
    User updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * 更新用户头像
     *
     * @param userId    用户ID
     * @param avatarUrl 头像URL
     */
    void updateUserAvatar(Long userId, String avatarUrl);
}
