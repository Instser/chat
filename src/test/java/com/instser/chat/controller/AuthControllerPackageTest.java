package com.instser.chat.controller;

import com.instser.chat.entity.Package;
import com.instser.chat.entity.User;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/**
 * 用户套餐功能测试
 * 用于验证日志输出和数据获取流程
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class AuthControllerPackageTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private PackageService packageService;

    @Autowired
    private AuthController authController;

    @Test
    void testPackageDataFlow() {
        log.info("=== 开始测试套餐数据流程 ===");

        try {
            // 1. 检查用户数据
            List<User> users = userService.list();
            log.info("数据库中用户总数: {}", users.size());
            
            if (users.isEmpty()) {
                log.error("数据库中没有用户数据，无法进行测试");
                return;
            }

            User testUser = users.get(0);
            log.info("测试用户: ID={}, username={}", testUser.getId(), testUser.getUsername());

            // 2. 检查套餐数据
            List<Package> packages = packageService.list();
            log.info("数据库中套餐总数: {}", packages.size());
            
            for (Package pkg : packages) {
                log.info("套餐: ID={}, name={}, enabled={}, deleted={}", 
                        pkg.getId(), pkg.getPackageName(), pkg.getIsEnabled(), pkg.getDeleted());
            }

            // 3. 检查用户账户
            UserAccount userAccount = userAccountService.getUserAccount(testUser.getId());
            if (userAccount != null) {
                log.info("用户账户: userId={}, packageId={}", 
                        userAccount.getUserId(), userAccount.getPackageId());
            } else {
                log.warn("用户{}没有账户信息", testUser.getId());
            }

            // 4. 使用反射调用私有方法测试套餐名称获取
            try {
                java.lang.reflect.Method method = AuthController.class.getDeclaredMethod("getUserPackageName", Long.class);
                method.setAccessible(true);
                String packageName = (String) method.invoke(authController, testUser.getId());
                log.info("通过反射调用获取的套餐名称: {}", packageName);
            } catch (Exception e) {
                log.error("反射调用失败", e);
            }

        } catch (Exception e) {
            log.error("测试过程中发生异常", e);
        }

        log.info("=== 测试完成 ===");
    }
}
