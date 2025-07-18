package com.instser.chat.service.impl;

import com.instser.chat.dto.OAuth2LoginResult;
import com.instser.chat.dto.OAuth2UserInfo;
import com.instser.chat.entity.User;
import com.instser.chat.entity.UserOAuth;
import com.instser.chat.exception.OAuth2Exception;
import com.instser.chat.dao.UserOAuthDao;
import com.instser.chat.service.OAuth2AuditService;
import com.instser.chat.service.OAuth2Service;
import com.instser.chat.service.UserService;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.util.OAuth2SecurityUtil;
import com.instser.chat.vo.OAuth2BindingInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * OAuth2服务实现类
 *
 * @author instser
 * @since 2025-07-16
 */
@Service
@Slf4j
@Transactional
public class OAuth2ServiceImpl implements OAuth2Service {

    @Autowired
    private UserService userService;

    @Autowired
    private UserOAuthDao userOAuthDao;



    @Autowired
    private OAuth2AuditService auditService;

    @Autowired
    private OAuth2SecurityUtil securityUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;



    @Value("${oauth2.providers.google.client-id:}")
    private String googleClientId;

    @Value("${oauth2.providers.google.client-secret:}")
    private String googleClientSecret;

    @Value("${oauth2.providers.github.client-id:}")
    private String githubClientId;

    @Value("${oauth2.providers.github.client-secret:}")
    private String githubClientSecret;

    private static final List<String> SUPPORTED_PROVIDERS = Arrays.asList("google", "github");



    @Override
    public String getAuthorizationUrl(String provider, String state) {
        if (!isProviderSupported(provider)) {
            throw new OAuth2Exception("INVALID_PROVIDER", "不支持的OAuth提供商: " + provider);
        }

        switch (provider.toLowerCase()) {
            case "google":
                return buildGoogleAuthUrl(state);
            case "github":
                return buildGithubAuthUrl(state);
            default:
                throw new OAuth2Exception("INVALID_PROVIDER", "不支持的OAuth提供商: " + provider);
        }
    }

    @Override
    public OAuth2UserInfo getUserInfo(String provider, String code) {
        if (!isProviderSupported(provider)) {
            throw new OAuth2Exception("INVALID_PROVIDER", "不支持的OAuth提供商: " + provider);
        }

        try {
            switch (provider.toLowerCase()) {
                case "google":
                    return getGoogleUserInfo(code);
                case "github":
                    return getGithubUserInfo(code);
                default:
                    throw new OAuth2Exception("INVALID_PROVIDER", "不支持的OAuth提供商: " + provider);
            }
        } catch (Exception e) {
            log.error("获取OAuth用户信息失败: provider={}, error={}", provider, e.getMessage());
            throw new OAuth2Exception("USER_INFO_ERROR", "获取用户信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public OAuth2LoginResult processLogin(OAuth2UserInfo userInfo, String clientIp, String userAgent) {
        log.info("处理OAuth2登录: provider={}, email={}, ip={}",
            userInfo.getProvider(), userInfo.getEmail(), clientIp);

        // 1. 检查是否已绑定OAuth账号
        UserOAuth existingOAuth = userOAuthDao.findByProviderAndProviderId(
            userInfo.getProvider(), userInfo.getId());

        if (existingOAuth != null && existingOAuth.getStatus() == 1) {
            // 已绑定且状态正常，更新登录信息
            updateOAuthLoginInfo(existingOAuth, userInfo);
            User user = userService.getById(existingOAuth.getUserId());

            log.info("OAuth2用户登录成功: userId={}, provider={}", user.getId(), userInfo.getProvider());
            return OAuth2LoginResult.existingUser(user);
        }

        // 2. 检查邮箱是否已存在
        User existingUser = userService.getUserByEmail(userInfo.getEmail());
        if (existingUser != null) {
            // 邮箱已存在，绑定OAuth账号
            UserOAuth newOAuth = createOAuthBinding(existingUser.getId(), userInfo);
            userOAuthDao.insert(newOAuth);

            log.info("OAuth2账号绑定成功: userId={}, provider={}", existingUser.getId(), userInfo.getProvider());
            return OAuth2LoginResult.newBinding(existingUser);
        }

        // 3. 创建新用户
        User newUser = createUserFromOAuth(userInfo);
        UserOAuth newOAuth = createOAuthBinding(newUser.getId(), userInfo);
        userOAuthDao.insert(newOAuth);

        log.info("OAuth2新用户注册成功: userId={}, provider={}", newUser.getId(), userInfo.getProvider());
        return OAuth2LoginResult.newUser(newUser);
    }



    @Override
    public boolean isProviderSupported(String provider) {
        return SUPPORTED_PROVIDERS.contains(provider.toLowerCase());
    }

    @Override
    public List<String> getSupportedProviders() {
        return new ArrayList<>(SUPPORTED_PROVIDERS);
    }

    // 私有辅助方法
    private String buildGoogleAuthUrl(String state) {
        String baseUrl = "https://accounts.google.com/o/oauth2/v2/auth";
        String redirectUri = "http://localhost:8080/api/auth/oauth2/callback/google"; // 需要配置
        
        return baseUrl + "?client_id=" + googleClientId +
               "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
               "&response_type=code" +
               "&scope=" + URLEncoder.encode("openid profile email", StandardCharsets.UTF_8) +
               "&state=" + state;
    }

    private String buildGithubAuthUrl(String state) {
        String baseUrl = "https://github.com/login/oauth/authorize";
        String redirectUri = "http://localhost:8080/api/auth/oauth2/callback/github"; // 需要配置
        
        return baseUrl + "?client_id=" + githubClientId +
               "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
               "&scope=" + URLEncoder.encode("user:email", StandardCharsets.UTF_8) +
               "&state=" + state;
    }

    /**
     * 获取Google用户信息
     */
    private OAuth2UserInfo getGoogleUserInfo(String code) {
        try {
            log.debug("开始获取Google用户信息，code: {}", code.substring(0, Math.min(code.length(), 10)) + "...");

            // 1. 用authorization code换取access token
            String accessToken = exchangeGoogleCodeForToken(code);

            // 2. 用access token获取用户信息
            return getGoogleUserInfoByToken(accessToken);

        } catch (Exception e) {
            log.error("获取Google用户信息失败: {}", e.getMessage(), e);
            throw new OAuth2Exception("GOOGLE_USER_INFO_ERROR", "获取Google用户信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取GitHub用户信息
     */
    private OAuth2UserInfo getGithubUserInfo(String code) {
        try {
            log.debug("开始获取GitHub用户信息，code: {}", code.substring(0, Math.min(code.length(), 10)) + "...");

            // 1. 用authorization code换取access token
            String accessToken = exchangeGithubCodeForToken(code);

            // 2. 用access token获取用户信息
            return getGithubUserInfoByToken(accessToken);

        } catch (Exception e) {
            log.error("获取GitHub用户信息失败: {}", e.getMessage(), e);
            throw new OAuth2Exception("GITHUB_USER_INFO_ERROR", "获取GitHub用户信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 用Google authorization code换取access token
     */
    private String exchangeGoogleCodeForToken(String code) {
        try {
            String tokenUrl = "https://oauth2.googleapis.com/token";
            String redirectUri = "http://localhost:8080/api/auth/oauth2/callback/google";

            // 构建请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("code", code);
            params.add("grant_type", "authorization_code");
            params.add("redirect_uri", redirectUri);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            log.debug("向Google请求access token: {}", tokenUrl);
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                String accessToken = jsonNode.get("access_token").asText();
                log.debug("成功获取Google access token");
                return accessToken;
            } else {
                throw new OAuth2Exception("TOKEN_EXCHANGE_FAILED", "Google token交换失败: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Google token交换失败: {}", e.getMessage(), e);
            throw new OAuth2Exception("TOKEN_EXCHANGE_ERROR", "Google token交换异常: " + e.getMessage(), e);
        }
    }

    /**
     * 用Google access token获取用户信息
     */
    private OAuth2UserInfo getGoogleUserInfoByToken(String accessToken) {
        try {
            String userInfoUrl = "https://www.googleapis.com/oauth2/v2/userinfo";

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            log.debug("向Google请求用户信息: {}", userInfoUrl);
            ResponseEntity<String> response = restTemplate.exchange(userInfoUrl, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());

                OAuth2UserInfo userInfo = new OAuth2UserInfo();
                userInfo.setProvider("google");
                userInfo.setId(jsonNode.get("id").asText());
                userInfo.setEmail(jsonNode.get("email").asText());
                userInfo.setName(jsonNode.get("name").asText());
                userInfo.setAvatar(jsonNode.has("picture") ? jsonNode.get("picture").asText() : null);
                userInfo.setRawData(response.getBody());

                log.debug("成功获取Google用户信息: email={}", userInfo.getEmail());
                return userInfo;
            } else {
                throw new OAuth2Exception("USER_INFO_REQUEST_FAILED", "Google用户信息请求失败: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Google用户信息请求失败: {}", e.getMessage(), e);
            throw new OAuth2Exception("USER_INFO_REQUEST_ERROR", "Google用户信息请求异常: " + e.getMessage(), e);
        }
    }

    /**
     * 用GitHub authorization code换取access token
     */
    private String exchangeGithubCodeForToken(String code) {
        try {
            String tokenUrl = "https://github.com/login/oauth/access_token";
            String redirectUri = "http://localhost:8080/api/auth/oauth2/callback/github";

            // 构建请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", githubClientId);
            params.add("client_secret", githubClientSecret);
            params.add("code", code);
            params.add("redirect_uri", redirectUri);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Accept", "application/json"); // GitHub需要这个头

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            log.debug("向GitHub请求access token: {}", tokenUrl);
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());

                if (jsonNode.has("error")) {
                    String error = jsonNode.get("error").asText();
                    String errorDescription = jsonNode.has("error_description") ?
                        jsonNode.get("error_description").asText() : "未知错误";
                    throw new OAuth2Exception("GITHUB_TOKEN_ERROR", "GitHub token错误: " + error + " - " + errorDescription);
                }

                String accessToken = jsonNode.get("access_token").asText();
                log.debug("成功获取GitHub access token");
                return accessToken;
            } else {
                throw new OAuth2Exception("TOKEN_EXCHANGE_FAILED", "GitHub token交换失败: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("GitHub token交换失败: {}", e.getMessage(), e);
            throw new OAuth2Exception("TOKEN_EXCHANGE_ERROR", "GitHub token交换异常: " + e.getMessage(), e);
        }
    }

    /**
     * 用GitHub access token获取用户信息
     */
    private OAuth2UserInfo getGithubUserInfoByToken(String accessToken) {
        try {
            String userInfoUrl = "https://api.github.com/user";

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("Accept", "application/vnd.github.v3+json");

            HttpEntity<String> request = new HttpEntity<>(headers);

            log.debug("向GitHub请求用户信息: {}", userInfoUrl);
            ResponseEntity<String> response = restTemplate.exchange(userInfoUrl, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());

                OAuth2UserInfo userInfo = new OAuth2UserInfo();
                userInfo.setProvider("github");
                userInfo.setId(jsonNode.get("id").asText());
                userInfo.setName(jsonNode.has("name") && !jsonNode.get("name").isNull() ?
                    jsonNode.get("name").asText() : jsonNode.get("login").asText());
                userInfo.setAvatar(jsonNode.has("avatar_url") ? jsonNode.get("avatar_url").asText() : null);
                userInfo.setRawData(response.getBody());

                // GitHub可能没有公开邮箱，需要单独获取
                String email = getGithubUserEmail(accessToken);
                userInfo.setEmail(email);

                log.debug("成功获取GitHub用户信息: login={}, email={}", jsonNode.get("login").asText(), email);
                return userInfo;
            } else {
                throw new OAuth2Exception("USER_INFO_REQUEST_FAILED", "GitHub用户信息请求失败: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("GitHub用户信息请求失败: {}", e.getMessage(), e);
            throw new OAuth2Exception("USER_INFO_REQUEST_ERROR", "GitHub用户信息请求异常: " + e.getMessage(), e);
        }
    }

    /**
     * 获取GitHub用户邮箱
     */
    private String getGithubUserEmail(String accessToken) {
        try {
            String emailUrl = "https://api.github.com/user/emails";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("Accept", "application/vnd.github.v3+json");

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(emailUrl, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode emailsArray = objectMapper.readTree(response.getBody());

                // 查找主邮箱或第一个验证的邮箱
                for (JsonNode emailNode : emailsArray) {
                    if (emailNode.get("primary").asBoolean() && emailNode.get("verified").asBoolean()) {
                        return emailNode.get("email").asText();
                    }
                }

                // 如果没有主邮箱，返回第一个验证的邮箱
                for (JsonNode emailNode : emailsArray) {
                    if (emailNode.get("verified").asBoolean()) {
                        return emailNode.get("email").asText();
                    }
                }

                // 如果都没有验证，返回第一个邮箱
                if (emailsArray.size() > 0) {
                    return emailsArray.get(0).get("email").asText();
                }
            }

            log.warn("无法获取GitHub用户邮箱");
            return null;

        } catch (Exception e) {
            log.warn("获取GitHub用户邮箱失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void bindOAuthAccount(Long userId, String provider, String code, String clientIp) {
        log.info("绑定OAuth账号: userId={}, provider={}", userId, provider);

        // 检查用户是否已绑定该提供商
        UserOAuth existingBinding = userOAuthDao.findByUserIdAndProvider(userId, provider);
        if (existingBinding != null && existingBinding.getStatus() == 1) {
            throw new OAuth2Exception("BINDING_EXISTS", "该OAuth账号已绑定");
        }

        // 获取OAuth用户信息
        OAuth2UserInfo userInfo = getUserInfo(provider, code);

        // 检查该OAuth账号是否已被其他用户绑定
        UserOAuth existingOAuth = userOAuthDao.findByProviderAndProviderId(provider, userInfo.getId());
        if (existingOAuth != null && !existingOAuth.getUserId().equals(userId)) {
            throw new OAuth2Exception("BINDING_EXISTS", "该OAuth账号已被其他用户绑定");
        }

        // 创建或更新绑定
        if (existingBinding != null) {
            // 更新现有绑定
            updateOAuthBinding(existingBinding, userInfo);
            userOAuthDao.updateById(existingBinding);
        } else {
            // 创建新绑定
            UserOAuth newBinding = createOAuthBinding(userId, userInfo);
            userOAuthDao.insert(newBinding);
        }

        log.info("OAuth账号绑定成功: userId={}, provider={}", userId, provider);
    }

    @Override
    public void unbindOAuthAccount(Long userId, String provider) {
        UserOAuth binding = userOAuthDao.findByUserIdAndProvider(userId, provider);
        if (binding == null) {
            throw new OAuth2Exception("BINDING_NOT_FOUND", "未找到OAuth绑定");
        }

        // 软删除绑定
        binding.setDeleted(1);
        userOAuthDao.updateById(binding);

        log.info("OAuth账号解绑成功: userId={}, provider={}", userId, provider);
    }

    @Override
    public List<OAuth2BindingInfo> getUserOAuthBindings(Long userId) {
        List<UserOAuth> bindings = userOAuthDao.findByUserId(userId);
        List<OAuth2BindingInfo> result = new ArrayList<>();

        // 添加所有支持的提供商信息
        for (String provider : SUPPORTED_PROVIDERS) {
            OAuth2BindingInfo info = new OAuth2BindingInfo();
            info.setProvider(provider);
            info.setProviderName(getProviderDisplayName(provider));
            info.setIsBound(false);

            // 查找是否已绑定
            for (UserOAuth binding : bindings) {
                if (provider.equals(binding.getProvider()) && binding.getStatus() == 1) {
                    info.setName(binding.getName());
                    info.setEmail(binding.getEmail());
                    info.setAvatar(binding.getAvatar());
                    info.setBindTime(binding.getCreateTime());
                    info.setLastLoginTime(binding.getLastLoginTime());
                    info.setLoginCount(binding.getLoginCount());
                    info.setStatus(binding.getStatus());
                    info.setIsBound(true);
                    break;
                }
            }

            result.add(info);
        }

        return result;
    }

    // 私有辅助方法
    private void updateOAuthLoginInfo(UserOAuth oauth, OAuth2UserInfo userInfo) {
        oauth.setLastLoginTime(LocalDateTime.now());
        oauth.setLoginCount(oauth.getLoginCount() + 1);

        // 更新用户信息（如果有变化）
        if (!Objects.equals(oauth.getName(), userInfo.getName())) {
            oauth.setName(userInfo.getName());
        }
        if (!Objects.equals(oauth.getAvatar(), userInfo.getAvatar())) {
            oauth.setAvatar(userInfo.getAvatar());
        }

        userOAuthDao.updateById(oauth);
    }

    private User createUserFromOAuth(OAuth2UserInfo userInfo) {
        User user = new User();
        user.setUsername(generateUniqueUsername(userInfo.getName()));
        user.setEmail(userInfo.getEmail());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // 随机密码
        user.setAvatar(userInfo.getAvatar());
        user.setRole("user");
        user.setStatus(1);

        userService.save(user);

        // 为新用户创建账户信息
        userAccountService.initUserAccount(user.getId());

        return user;
    }

    private UserOAuth createOAuthBinding(Long userId, OAuth2UserInfo userInfo) {
        UserOAuth oauth = new UserOAuth();
        oauth.setUserId(userId);
        oauth.setProvider(userInfo.getProvider());
        oauth.setProviderId(userInfo.getId());
        oauth.setEmail(userInfo.getEmail());
        oauth.setName(userInfo.getName());
        oauth.setAvatar(userInfo.getAvatar());
        oauth.setLoginCount(1);
        oauth.setLastLoginTime(LocalDateTime.now());
        oauth.setStatus(1);

        // 加密存储令牌（如果有）
        if (userInfo.getAccessToken() != null) {
            oauth.setAccessToken(securityUtil.encryptToken(userInfo.getAccessToken()));
        }
        if (userInfo.getRefreshToken() != null) {
            oauth.setRefreshToken(securityUtil.encryptToken(userInfo.getRefreshToken()));
        }
        if (userInfo.getExpiresAt() != null) {
            oauth.setExpiresAt(userInfo.getExpiresAt());
        }

        return oauth;
    }

    private void updateOAuthBinding(UserOAuth binding, OAuth2UserInfo userInfo) {
        binding.setEmail(userInfo.getEmail());
        binding.setName(userInfo.getName());
        binding.setAvatar(userInfo.getAvatar());
        binding.setStatus(1);
        binding.setDeleted(0);

        // 更新令牌
        if (userInfo.getAccessToken() != null) {
            binding.setAccessToken(securityUtil.encryptToken(userInfo.getAccessToken()));
        }
        if (userInfo.getRefreshToken() != null) {
            binding.setRefreshToken(securityUtil.encryptToken(userInfo.getRefreshToken()));
        }
        if (userInfo.getExpiresAt() != null) {
            binding.setExpiresAt(userInfo.getExpiresAt());
        }
    }

    private String generateUniqueUsername(String baseName) {
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = "user";
        }

        // 清理用户名，只保留字母数字和下划线
        String cleanName = baseName.replaceAll("[^a-zA-Z0-9_]", "");
        if (cleanName.isEmpty()) {
            cleanName = "user";
        }

        // 检查用户名是否已存在
        String username = cleanName;
        int counter = 1;
        while (userService.getUserByUsername(username) != null) {
            username = cleanName + "_" + counter++;
        }

        return username;
    }

    private String getProviderDisplayName(String provider) {
        switch (provider.toLowerCase()) {
            case "google":
                return "Google";
            case "github":
                return "GitHub";
            default:
                return provider;
        }
    }
}
