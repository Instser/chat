package com.instser.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.User;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.security.UserDetailsServiceImpl;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.service.UserService;
import com.instser.chat.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 用户套餐功能测试
 */
@WebMvcTest(AuthController.class)
public class AuthControllerUserPackageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private UserService userService;

    @MockBean
    private UserAccountService userAccountService;

    @MockBean
    private PackageService packageService;

    @MockBean
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private UserAccount testUserAccount;
    private Package testPackage;

    @BeforeEach
    void setUp() {
        // 创建测试用户
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setRole("user");
        testUser.setStatus(1);
        testUser.setCreatedTime(LocalDateTime.now());
        testUser.setUpdateTime(LocalDateTime.now());

        // 创建测试用户账户
        testUserAccount = new UserAccount();
        testUserAccount.setId(1L);
        testUserAccount.setUserId(1L);
        testUserAccount.setPackageId(1L);
        testUserAccount.setPackageExpireTime(LocalDateTime.now().plusDays(30));
        testUserAccount.setChatCountRemaining(1000);

        // 创建测试套餐
        testPackage = new Package();
        testPackage.setId(1L);
        testPackage.setPackageName("基础套餐");
        testPackage.setDescription("适合个人用户的基础AI对话套餐");
        testPackage.setPackageType("basic");
        testPackage.setPriceUsd(new BigDecimal("9.99"));
        testPackage.setPriceCny(new BigDecimal("69.99"));
        testPackage.setValidityDays(30);
        testPackage.setIsEnabled(1);
    }

    @Test
    void testGetCurrentUserWithoutPackage() throws Exception {
        // 模拟认证用户
        mockAuthentication();

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("获取用户信息成功"))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.packageName").doesNotExist());
    }

    @Test
    void testGetCurrentUserWithPackageTrue() throws Exception {
        // 模拟认证用户
        mockAuthentication();

        // 模拟服务调用
        when(userAccountService.getUserAccount(1L)).thenReturn(testUserAccount);
        when(packageService.getPackageById(1L)).thenReturn(testPackage);

        mockMvc.perform(get("/auth/me")
                        .param("userPackage", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("获取用户信息成功"))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.packageName").value("基础套餐"));
    }

    @Test
    void testGetCurrentUserWithPackageFalse() throws Exception {
        // 模拟认证用户
        mockAuthentication();

        mockMvc.perform(get("/auth/me")
                        .param("userPackage", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("获取用户信息成功"))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.packageName").doesNotExist());
    }

    @Test
    void testGetCurrentUserWithPackageTrueButNoUserAccount() throws Exception {
        // 模拟认证用户
        mockAuthentication();

        // 模拟用户没有账户信息
        when(userAccountService.getUserAccount(1L)).thenReturn(null);

        mockMvc.perform(get("/auth/me")
                        .param("userPackage", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("获取用户信息成功"))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.packageName").isEmpty());
    }

    @Test
    void testGetCurrentUserWithPackageTrueButNoPackage() throws Exception {
        // 模拟认证用户
        mockAuthentication();

        // 模拟用户有账户但套餐不存在
        when(userAccountService.getUserAccount(1L)).thenReturn(testUserAccount);
        when(packageService.getPackageById(1L)).thenReturn(null);

        mockMvc.perform(get("/auth/me")
                        .param("userPackage", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("获取用户信息成功"))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.packageName").isEmpty());
    }

    @Test
    void testGetCurrentUserUnauthenticated() throws Exception {
        // 不设置认证信息
        mockMvc.perform(get("/auth/me")
                        .param("userPackage", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户未认证"));
    }

    private void mockAuthentication() {
        // 创建模拟的认证对象
        Authentication authentication = mock(Authentication.class);
        UserDetailsServiceImpl.CustomUserDetails userDetails = 
            new UserDetailsServiceImpl.CustomUserDetails(testUser);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        // 设置安全上下文
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }
}
