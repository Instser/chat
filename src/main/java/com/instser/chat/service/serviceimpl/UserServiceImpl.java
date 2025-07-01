package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.common.exception.BusinessException;
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
    public Long registerUser(String username, String email, String password) {
        // 检查邮箱是否已存在
        if (getUserByEmail(email) != null) {
            throw new BusinessException("邮箱已被注册");
        }

        // 检查用户名是否已存在
        if (getUserByUsername(username) != null) {
            throw new BusinessException("用户名已被使用");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("user");
        user.setStatus(1);
        user.setCreatedTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        this.save(user);
        log.info("用户注册成功，用户ID：{}", user.getId());

        // 为新用户自动绑定ID为1的套餐
        try {
            Package defaultPackage = packageService.getPackageById(1L);
            if (defaultPackage != null) {
                boolean activated = userAccountService.activatePackage(
                    user.getId(),
                    1L,
                    defaultPackage.getValidityDays(),
                    defaultPackage.getChatCount()
                );

                if (activated) {
                    log.info("用户{}自动绑定默认套餐成功，套餐ID：1", user.getId());
                } else {
                    log.warn("用户{}自动绑定默认套餐失败，套餐ID：1", user.getId());
                }
            } else {
                log.warn("默认套餐(ID=1)不存在，跳过自动绑定");
            }
        } catch (Exception e) {
            log.error("用户{}自动绑定默认套餐异常", user.getId(), e);
            // 不抛出异常，避免影响用户注册流程
        }

        return user.getId();
    }

    @Override
    public User login(String email, String password) {
        User user = getUserByEmail(email);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (user.getStatus() == 0) {
            throw new BusinessException("账户已被禁用");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        // 更新最后登录时间
        updateLastLoginTime(user.getId());

        log.info("用户登录成功，用户ID：{}", user.getId());
        return user;
    }

    @Override
    public void updateLastLoginTime(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setLastLoginTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        this.updateById(user);
    }

    @Override
    public void initDefaultUser() {
        // 检查是否已存在默认用户
        User existingUser = getUserByEmail("admin@example.com");
        if (existingUser != null) {
            log.info("默认用户已存在，跳过初始化");
            return;
        }

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
        log.info("默认用户初始化完成，用户ID：{}", defaultUser.getId());
    }

    @Override
    public boolean checkPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public void updatePassword(Long userId, String newPassword) {
        User user = new User();
        user.setId(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());

        this.updateById(user);
        log.info("用户密码更新成功，用户ID：{}", userId);
    }

    @Override
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = new User();
        user.setId(userId);
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setAvatar(request.getAvatar());
        user.setUpdateTime(LocalDateTime.now());

        this.updateById(user);

        // 返回更新后的完整用户信息
        User updatedUser = this.getById(userId);
        log.info("用户资料更新成功，用户ID：{}", userId);
        return updatedUser;
    }
}
