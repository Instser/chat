package com.instser.chat.security;

import com.instser.chat.entity.User;
import com.instser.chat.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Spring Security用户详情服务实现
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    
    private final UserService userService;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("加载用户信息: {}", username);
        
        User user;
        try {
            // 判断是邮箱还是用户名
            if (username.contains("@")) {
                user = userService.getUserByEmail(username);
            } else {
                user = userService.getUserByUsername(username);
            }
        } catch (Exception e) {
            log.error("查询用户信息失败: {}", username, e);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        
        if (user == null) {
            log.warn("用户不存在: {}", username);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("用户已被禁用: {}", username);
            throw new UsernameNotFoundException("用户已被禁用: " + username);
        }
        
        return new CustomUserDetails(user);
    }
    
    /**
     * 自定义用户详情类
     */
    public static class CustomUserDetails implements UserDetails {
        
        private final User user;
        
        public CustomUserDetails(User user) {
            this.user = user;
        }
        
        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            List<GrantedAuthority> authorities = new ArrayList<>();
            
            // 添加角色权限
            if (user.getRole() != null) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase()));
            } else {
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            }
            
            return authorities;
        }
        
        @Override
        public String getPassword() {
            return user.getPassword();
        }
        
        @Override
        public String getUsername() {
            return user.getUsername();
        }
        
        @Override
        public boolean isAccountNonExpired() {
            return true;
        }
        
        @Override
        public boolean isAccountNonLocked() {
            return user.getStatus() != null && user.getStatus() == 1;
        }
        
        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }
        
        @Override
        public boolean isEnabled() {
            return user.getStatus() != null && user.getStatus() == 1;
        }
        
        /**
         * 获取用户实体
         */
        public User getUser() {
            return user;
        }
        
        /**
         * 获取用户ID
         */
        public Long getUserId() {
            return user.getId();
        }
        
        /**
         * 获取用户邮箱
         */
        public String getEmail() {
            return user.getEmail();
        }
        
        /**
         * 获取用户角色
         */
        public String getRole() {
            return user.getRole();
        }
    }
}
