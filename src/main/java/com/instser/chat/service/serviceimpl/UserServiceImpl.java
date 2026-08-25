package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.common.exception.BusinessException;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.dao.UserDao;
import com.instser.chat.dto.request.UpdateProfileRequest;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.User;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserDao, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final PackageService packageService;
    private final UserAccountService userAccountService;

    // 使用@Lazy注解延迟注入PasswordEncoder，打破循环依赖
    public UserServiceImpl(@Lazy PasswordEncoder passwordEncoder,
                          PackageService packageService,
                          UserAccountService userAccountService) {
        this.passwordEncoder = passwordEncoder;
        this.packageService = packageService;
        this.userAccountService = userAccountService;
    }

    @Override
    public User getUserByEmail(String email) {
        return baseMapper.selectByEmail(email);
    }

    @Override
    public User getUserByUsername(String username) {
        return baseMapper.selectByUsername(username);
    }

    @Override
    @Transactional
    public User registerUser(String username, String email, String password, String avatar) {
        LogUtils.logUserOperation(log, null, "OAuth2用户注册开始", "邮箱：{}，用户名：{}", email, username);

        try {
            User user = createUserInternal(username, email, password, avatar, "OAuth2用户注册成功");
            return user;

        } catch (BusinessException e) {
            LogUtils.logUserWarning(log, null, "OAuth2用户注册失败", "邮箱：{}，原因：{}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            LogUtils.logUserError(log, null, "OAuth2用户注册异常", "邮箱：{}，异常：{}", e, email, e.getMessage());
            throw new BusinessException("注册失败", e);
        }
    }

    @Override
    @Transactional
    public User registerGuestUser(String username, String email, String password) {
        LogUtils.logUserOperation(log, null, "试用用户注册开始", "邮箱：{}，用户名：{}", email, username);

        try {
            User user = createUserInternal(username, email, password, null, "试用用户注册成功");
            return user;

        } catch (BusinessException e) {
            LogUtils.logUserWarning(log, null, "试用用户注册失败", "邮箱：{}，原因：{}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            LogUtils.logUserError(log, null, "试用用户注册异常", "邮箱：{}，异常：{}", e, email, e.getMessage());
            throw new BusinessException("注册失败", e);
        }
    }

    /**
     * 用户注册内部逻辑（OAuth2与试用用户共用）
     * 职责：唯一性校验、创建用户、初始化账户、绑定默认套餐
     */
    private User createUserInternal(String username, String email, String password, String avatar,
                                    String successLogTag) {
        // 检查邮箱是否已存在
        if (getUserByEmail(email) != null) {
            LogUtils.logUserWarning(log, null, "注册失败", "邮箱已存在：{}", email);
            throw new BusinessException("邮箱已被注册");
        }

        LogUtils.logUserDebug(log, null, "注册验证", "邮箱验证通过：{}", email);

        // 检查用户名是否已存在
        if (getUserByUsername(username) != null) {
            LogUtils.logUserWarning(log, null, "注册失败", "用户名已存在：{}", username);
            throw new BusinessException("用户名已被使用");
        }

        LogUtils.logUserDebug(log, null, "注册验证", "用户名验证通过：{}", username);

        // 创建用户
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password)); // 随机密码，不用于登录
        user.setAvatar(avatar);
        user.setRole("user");
        user.setStatus(1);
        user.setLastLoginTime(now); // 注册即登录，设置最后登录时间
        user.setCreatedTime(now);
        user.setUpdateTime(now);

        this.save(user);

        LogUtils.logUserOperation(log, user.getId(), successLogTag, "邮箱：{}，用户名：{}", email, username);

        // 为新用户创建账户信息
        userAccountService.initUserAccount(user.getId());

        // 为新用户自动绑定ID为1的套餐
        bindDefaultPackage(user.getId());

        return user;
    }

    /**
     * 为用户绑定默认套餐
     */
    private void bindDefaultPackage(Long userId) {
        try {
            LogUtils.logUserDebug(log, userId, "套餐绑定", "开始为用户绑定默认套餐");
            Package defaultPackage = packageService.getPackageById(1L);
            if (defaultPackage != null) {
                boolean activated = userAccountService.activatePackage(
                    userId,
                    1L,
                    defaultPackage.getValidityDays(),
                    defaultPackage.getChatCount()
                );

                if (activated) {
                    LogUtils.logUserOperation(log, userId, "套餐绑定", "自动绑定默认套餐成功，套餐ID：1");
                } else {
                    LogUtils.logUserWarning(log, userId, "套餐绑定", "自动绑定默认套餐失败，套餐ID：1");
                }
            } else {
                LogUtils.logAdminOperation(log, "套餐绑定", "默认套餐(ID=1)不存在，跳过自动绑定");
            }
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "套餐绑定", "自动绑定默认套餐异常：{}", e, e.getMessage());
            // 不抛出异常，避免影响用户注册流程
        }
    }

    @Override
    public User login(String email, String password) {
        LogUtils.logUserOperation(log, null, "用户登录开始", "邮箱：{}", email);

        try {
            User user = getUserByEmail(email);
            if (user == null) {
                LogUtils.logUserWarning(log, null, "登录失败", "用户不存在：{}", email);
                throw new BusinessException("用户不存在");
            }

            LogUtils.logUserDebug(log, user.getId(), "登录验证", "用户存在检查通过");

            if (user.getStatus() == 0) {
                LogUtils.logUserWarning(log, user.getId(), "登录失败", "账户已禁用");
                throw new BusinessException("账户已被禁用");
            }

            LogUtils.logUserDebug(log, user.getId(), "登录验证", "账户状态检查通过");

            if (!passwordEncoder.matches(password, user.getPassword())) {
                LogUtils.logUserWarning(log, user.getId(), "登录失败", "密码错误");
                throw new BusinessException("密码错误");
            }

            LogUtils.logUserDebug(log, user.getId(), "登录验证", "密码验证通过");

            // 更新最后登录时间
            updateLastLoginTime(user.getId());

            LogUtils.logUserOperation(log, user.getId(), "用户登录成功", "邮箱：{}", email);
            return user;

        } catch (BusinessException e) {
            LogUtils.logUserWarning(log, null, "用户登录失败", "邮箱：{}，原因：{}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            LogUtils.logUserError(log, null, "用户登录异常", "邮箱：{}，异常：{}", e, email, e.getMessage());
            throw new BusinessException("登录失败", e);
        }
    }

    @Override
    public void updateLastLoginTime(Long userId) {
        LogUtils.logUserDebug(log, userId, "登录时间更新", "更新最后登录时间");
        User user = new User();
        user.setId(userId);
        user.setLastLoginTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        this.updateById(user);
    }

    @Override
    public void initDefaultUser() {
        LogUtils.logAdminOperation(log, "系统初始化", "开始检查默认用户");
        // 检查是否已存在默认用户
        User existingUser = getUserByEmail("admin@example.com");
        if (existingUser != null) {
            LogUtils.logAdminOperation(log, "系统初始化", "默认用户已存在，跳过初始化");
            return;
        }

        try {
            // 创建默认用户
            User defaultUser = new User();
            defaultUser.setId(1L);
            defaultUser.setUsername("admin");
            defaultUser.setEmail("admin@example.com");
            defaultUser.setPassword(passwordEncoder.encode("admin123"));
            defaultUser.setRole("admin");
            defaultUser.setStatus(1);
            defaultUser.setCreatedTime(LocalDateTime.now());
            defaultUser.setUpdateTime(LocalDateTime.now());

            this.save(defaultUser);
            LogUtils.logAdminOperation(log, "系统初始化", "默认用户初始化完成，用户ID：{}", defaultUser.getId());
        } catch (Exception e) {
            LogUtils.logAdminOperation(log, "系统初始化", "默认用户初始化失败：{}", e, e.getMessage());
            throw new BusinessException("默认用户初始化失败", e);
        }
    }

    @Override
    public boolean checkPassword(String rawPassword, String encodedPassword) {
        LogUtils.logUserDebug(log, null, "密码验证", "执行密码验证");
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public void updatePassword(Long userId, String newPassword) {
        LogUtils.logUserOperation(log, userId, "密码更新", "开始更新用户密码");
        try {
            User user = new User();
            user.setId(userId);
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setUpdateTime(LocalDateTime.now());

            this.updateById(user);
            LogUtils.logUserOperation(log, userId, "密码更新", "用户密码更新成功");
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "密码更新", "用户密码更新失败，错误：{}", e, e.getMessage());
            throw new BusinessException("密码更新失败", e);
        }
    }

    @Override
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        LogUtils.logUserOperation(log, userId, "资料更新", "开始更新用户资料");
        try {
            // 验证用户名是否已被使用
            User existingUser = getUserByUsername(request.getUsername());
            if (existingUser != null && !existingUser.getId().equals(userId)) {
                LogUtils.logUserWarning(log, userId, "资料更新", "用户名已被使用，用户名：{}", request.getUsername());
                throw new BusinessException("用户名已被使用");
            }

            LogUtils.logUserDebug(log, userId, "资料更新", "用户名验证通过");

            // 验证邮箱是否已被使用
            existingUser = getUserByEmail(request.getEmail());
            if (existingUser != null && !existingUser.getId().equals(userId)) {
                LogUtils.logUserWarning(log, userId, "资料更新", "邮箱已被使用，邮箱：{}", request.getEmail());
                throw new BusinessException("邮箱已被使用");
            }

            LogUtils.logUserDebug(log, userId, "资料更新", "邮箱验证通过");

            User user = new User();
            user.setId(userId);
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());
            user.setAvatar(request.getAvatar());
            user.setUpdateTime(LocalDateTime.now());

            this.updateById(user);

            // 返回更新后的完整用户信息
            User updatedUser = this.getById(userId);
            LogUtils.logUserOperation(log, userId, "资料更新", "用户资料更新成功");
            return updatedUser;
        } catch (BusinessException e) {
            LogUtils.logUserWarning(log, userId, "资料更新", "用户资料更新失败，原因：{}", e.getMessage());
            throw e;
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "资料更新", "用户资料更新异常，错误：{}", e, e.getMessage());
            throw new BusinessException("资料更新失败", e);
        }
    }

    @Override
    public void updateUserAvatar(Long userId, String avatarUrl) {
        LogUtils.logUserDebug(log, userId, "头像更新", "更新用户头像：{}", avatarUrl);
        try {
            User user = new User();
            user.setId(userId);
            user.setAvatar(avatarUrl);
            user.setUpdateTime(LocalDateTime.now());

            this.updateById(user);
            LogUtils.logUserDebug(log, userId, "头像更新", "用户头像更新成功");
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "头像更新", "用户头像更新失败，错误：{}", e, e.getMessage());
            // 头像更新失败不抛出异常，避免影响主流程
        }
    }
}
