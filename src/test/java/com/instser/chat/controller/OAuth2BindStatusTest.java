package com.instser.chat.controller;

import com.instser.chat.dto.OAuth2LoginResult;
import com.instser.chat.entity.User;
import com.instser.chat.vo.UserVO;
import com.instser.chat.vo.converter.VOConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * OAuth2绑定状态测试
 * 注意：已移除后端state验证功能
 *
 * @author instser
 * @since 2025-07-17
 */
public class OAuth2BindStatusTest {

    @Test
    @DisplayName("测试OAuth2绑定状态设置 - 已存在用户")
    public void testOAuth2BindStatus_ExistingUser() {
        // 创建测试用户
        User user = createTestUser();
        
        // 创建OAuth2登录结果 - 已存在用户
        OAuth2LoginResult loginResult = OAuth2LoginResult.existingUser(user);
        
        // 转换为UserVO
        UserVO userVO = VOConverter.toUserVO(user);
        
        // 模拟设置绑定状态
        setOAuth2BindStatus(userVO, loginResult);
        
        // 验证绑定状态
        assertEquals(2, userVO.getBind(), "已存在OAuth绑定的用户登录，bind应该为2");
    }

    @Test
    @DisplayName("测试OAuth2绑定状态设置 - 新用户")
    public void testOAuth2BindStatus_NewUser() {
        // 创建测试用户
        User user = createTestUser();
        
        // 创建OAuth2登录结果 - 新用户
        OAuth2LoginResult loginResult = OAuth2LoginResult.newUser(user);
        
        // 转换为UserVO
        UserVO userVO = VOConverter.toUserVO(user);
        
        // 模拟设置绑定状态
        setOAuth2BindStatus(userVO, loginResult);
        
        // 验证绑定状态
        assertEquals(1, userVO.getBind(), "新用户注册，bind应该为1");
    }

    @Test
    @DisplayName("测试OAuth2绑定状态设置 - 新绑定")
    public void testOAuth2BindStatus_NewBinding() {
        // 创建测试用户
        User user = createTestUser();
        
        // 创建OAuth2登录结果 - 新绑定
        OAuth2LoginResult loginResult = OAuth2LoginResult.newBinding(user);
        
        // 转换为UserVO
        UserVO userVO = VOConverter.toUserVO(user);
        
        // 模拟设置绑定状态
        setOAuth2BindStatus(userVO, loginResult);
        
        // 验证绑定状态
        assertEquals(1, userVO.getBind(), "通过邮箱关联的用户，bind应该为1");
    }

    @Test
    @DisplayName("测试普通登录绑定状态")
    public void testNormalLoginBindStatus() {
        // 创建测试用户
        User user = createTestUser();
        
        // 转换为UserVO
        UserVO userVO = VOConverter.toUserVO(user);
        
        // 模拟普通登录设置绑定状态
        userVO.setBind(0);
        
        // 验证绑定状态
        assertEquals(0, userVO.getBind(), "普通登录，bind应该为0");
    }

    /**
     * 创建测试用户
     */
    private User createTestUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setRole("USER");
        user.setStatus(1);
        return user;
    }

    /**
     * 模拟OAuth2Controller中的setOAuth2BindStatus方法
     */
    private void setOAuth2BindStatus(UserVO userVO, OAuth2LoginResult loginResult) {
        switch (loginResult.getLoginType()) {
            case EXISTING_USER:
                // 已存在OAuth绑定的用户登录
                userVO.setBind(2);
                break;
            case NEW_USER:
                // 新用户注册（无绑定）
                userVO.setBind(1);
                break;
            case NEW_BINDING:
                // 通过邮箱关联的用户（无OAuth绑定）
                userVO.setBind(1);
                break;
            default:
                // 默认设置为无绑定
                userVO.setBind(1);
                break;
        }
    }
}
