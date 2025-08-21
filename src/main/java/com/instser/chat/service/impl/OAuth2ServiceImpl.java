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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.*;

import com.instser.chat.common.util.LogUtils;
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
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;



    @Value("${oauth2.providers.google.client-id:}")
    private String googleClientId;

    @Value("${oauth2.providers.google.client-secret:}")
    private String googleClientSecret;

    @Value("${oauth2.providers.google.token-uri:https://oauth2.googleapis.com/token}")
    private String googleTokenUri;

    @Value("${oauth2.providers.google.user-info-uri:https://www.googleapis.com/oauth2/v3/userinfo}")
    private String googleUserInfoUri;

    @Value("${oauth2.providers.google.redirect-uri:http://localhost:3000/auth/callback/google}")
    private String googleRedirectUri;

    @Value("${oauth2.providers.google.scope:openid,email,profile}")
    private String googleScope;

    @Value("${oauth2.providers.github.client-id:}")
    private String githubClientId;

    @Value("${oauth2.providers.github.client-secret:}")
    private String githubClientSecret;

    @Value("${oauth2.providers.github.token-uri:https://github.com/login/oauth/access_token}")
    private String githubTokenUri;

    @Value("${oauth2.providers.github.user-info-uri:https://api.github.com/user}")
    private String githubUserInfoUri;

    @Value("${oauth2.providers.github.redirect-uri:http://localhost:3000/auth/callback/github}")
    private String githubRedirectUri;

    @Value("${oauth2.providers.github.scope:user:email}")
    private String githubScope;

    private static final List<String> SUPPORTED_PROVIDERS = Arrays.asList("google", "github");





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
            LogUtils.logUserError(log, null, "OAuth2", "获取OAuth用户信息失败: provider={}, error={}", e, provider, e.getMessage());
            throw new OAuth2Exception("USER_INFO_ERROR", "获取用户信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public OAuth2LoginResult processLogin(OAuth2UserInfo userInfo, String clientIp, String userAgent) {
        LogUtils.logUserOperation(log, null, "OAuth2登录", "处理OAuth2登录: provider={}, email={}, ip={}",
            userInfo.getProvider(), userInfo.getEmail(), clientIp);

        // 1. 检查是否已绑定OAuth账号
        UserOAuth existingOAuth = userOAuthDao.findByProviderAndProviderId(
            userInfo.getProvider(), userInfo.getId());

        if (existingOAuth != null && existingOAuth.getStatus() == 1) {
            // 已绑定且状态正常，更新登录信息
            updateOAuthLoginInfo(existingOAuth, userInfo);
            User user = userService.getById(existingOAuth.getUserId());

            LogUtils.logUserOperation(log, user.getId(), "OAuth2登录", "OAuth2用户登录成功: provider={}", userInfo.getProvider());
            return OAuth2LoginResult.existingUser(user);
        }

        // 2. 检查邮箱是否已存在
        User existingUser = userService.getUserByEmail(userInfo.getEmail());
        if (existingUser != null) {
            // 邮箱已存在，绑定OAuth账号
            UserOAuth newOAuth = createOAuthBinding(existingUser.getId(), userInfo);
            userOAuthDao.insert(newOAuth);

            // 更新User表的最后登录时间（新绑定也算登录）
            userService.updateLastLoginTime(existingUser.getId());

            LogUtils.logUserOperation(log, existingUser.getId(), "OAuth2登录", "OAuth2账号绑定成功: provider={}", userInfo.getProvider());
            return OAuth2LoginResult.newBinding(existingUser);
        }

        // 3. 创建新用户
        User newUser = createUserFromOAuth(userInfo);
        UserOAuth newOAuth = createOAuthBinding(newUser.getId(), userInfo);
        userOAuthDao.insert(newOAuth);

        LogUtils.logUserOperation(log, newUser.getId(), "OAuth2注册", "OAuth2新用户注册成功: provider={}", userInfo.getProvider());
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

    /**
     * 获取Google用户信息
     */
    private OAuth2UserInfo getGoogleUserInfo(String code) {
        try {
            log.debug("开始获取Google用户信息，code: {}", code.substring(0, Math.min(code.length(), 10)) + "...");

            // 1. 用authorization code换取token（可能包含id_token）
            String tokenResult = exchangeGoogleCodeForToken(code);

            // 2. 解析token结果
            if (tokenResult.contains("|")) {
                // 包含id_token，优先使用id_token解码
                String[] tokens = tokenResult.split("\\|", 2);
                String idToken = tokens[0];
                String accessToken = tokens[1];

                log.info("【Google用户信息】使用id_token解码获取用户信息");
                return getUserInfoFromIdToken(idToken, accessToken);
            } else {
                // 只有access_token，使用API调用
                log.info("【Google用户信息】使用access_token调用API获取用户信息");
                return getGoogleUserInfoByToken(tokenResult);
            }

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
            // 构建请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("code", code);
            params.add("grant_type", "authorization_code");
            params.add("redirect_uri", googleRedirectUri);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            log.info("【Google Token交换】开始请求access token");
            log.debug("【Google Token交换】请求URL: {}", googleTokenUri);
            log.debug("【Google Token交换】client_id: {}", googleClientId);
            log.debug("【Google Token交换】redirect_uri: {}", googleRedirectUri);
            log.debug("【Google Token交换】code长度: {}", code != null ? code.length() : 0);

            ResponseEntity<String> response = restTemplate.postForEntity(googleTokenUri, request, String.class);

            log.info("【Google Token交换】响应状态码: {}", response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                String accessToken = jsonNode.get("access_token").asText();
                String idToken = jsonNode.has("id_token") ? jsonNode.get("id_token").asText() : null;

                log.info("【Google Token交换】成功获取access token，id_token存在: {}", idToken != null);

                // 将id_token存储起来，供后续使用
                if (idToken != null) {
                    // 使用ThreadLocal或其他方式临时存储id_token
                    return idToken + "|" + accessToken; // 临时方案：用分隔符连接
                }
                return accessToken;
            } else {
                String responseBody = response.getBody();
                log.error("【Google Token交换】失败 - 状态码: {}, 响应体: {}", response.getStatusCode(), responseBody);
                throw new OAuth2Exception("TOKEN_EXCHANGE_FAILED", "Google token交换失败: " + response.getStatusCode() + ", 响应: " + responseBody);
            }

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("【Google Token交换】HTTP客户端错误 - 状态码: {}, 响应体: {}", e.getStatusCode(), responseBody);

            // 尝试解析Google的错误响应
            try {
                JsonNode errorNode = objectMapper.readTree(responseBody);
                String error = errorNode.has("error") ? errorNode.get("error").asText() : "unknown_error";
                String errorDescription = errorNode.has("error_description") ? errorNode.get("error_description").asText() : "无详细描述";

                log.error("【Google Token交换】Google错误详情 - 错误代码: {}, 错误描述: {}", error, errorDescription);
                throw new OAuth2Exception("GOOGLE_TOKEN_ERROR", "Google token交换失败: " + error + " - " + errorDescription);
            } catch (Exception parseException) {
                log.error("【Google Token交换】无法解析错误响应: {}", responseBody);
                throw new OAuth2Exception("TOKEN_EXCHANGE_ERROR", "Google token交换失败: " + e.getStatusCode() + " - " + responseBody);
            }
        } catch (Exception e) {
            log.error("【Google Token交换】异常: {}", e.getMessage(), e);
            throw new OAuth2Exception("TOKEN_EXCHANGE_ERROR", "Google token交换异常: " + e.getMessage(), e);
        }
    }

    /**
     * 从Google id_token解码获取用户信息（推荐方式）
     */
    private OAuth2UserInfo getUserInfoFromIdToken(String idToken, String accessToken) {
        try {
            log.debug("开始解码Google id_token");

            // 解码JWT（不验证签名，因为这是从Google直接获取的）
            // 在生产环境中，应该验证签名以确保安全性
            String[] chunks = idToken.split("\\.");
            if (chunks.length != 3) {
                throw new OAuth2Exception("INVALID_ID_TOKEN", "无效的id_token格式");
            }

            // 解码payload部分（Base64URL解码）
            String payload = new String(java.util.Base64.getUrlDecoder().decode(chunks[1]));
            log.debug("id_token payload: {}", payload);

            JsonNode jsonNode = objectMapper.readTree(payload);

            OAuth2UserInfo userInfo = new OAuth2UserInfo();
            userInfo.setProvider("google");

            // 从id_token中获取用户信息
            userInfo.setId(jsonNode.has("sub") ? jsonNode.get("sub").asText() : null);
            userInfo.setEmail(jsonNode.has("email") ? jsonNode.get("email").asText() : null);
            userInfo.setName(jsonNode.has("name") ? jsonNode.get("name").asText() : null);
            userInfo.setAvatar(jsonNode.has("picture") ? jsonNode.get("picture").asText() : null);
            userInfo.setRawData(payload);

            // 验证必要字段
            if (userInfo.getId() == null) {
                throw new OAuth2Exception("USER_ID_MISSING", "id_token中缺少用户ID");
            }

            log.info("成功从id_token解码用户信息: userId={}, email={}", userInfo.getId(), userInfo.getEmail());
            return userInfo;

        } catch (Exception e) {
            log.error("解码id_token失败: {}", e.getMessage(), e);
            // 如果id_token解码失败，fallback到API调用
            log.warn("id_token解码失败，fallback到API调用");
            return getGoogleUserInfoByToken(accessToken);
        }
    }

    /**
     * 用Google access token获取用户信息（备用方式）
     */
    private OAuth2UserInfo getGoogleUserInfoByToken(String accessToken) {
        try {
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            log.info("向Google请求用户信息: {}", googleUserInfoUri);
            ResponseEntity<String> response = restTemplate.exchange(googleUserInfoUri, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                log.debug("Google用户信息响应: {}", responseBody);

                JsonNode jsonNode = objectMapper.readTree(responseBody);

                OAuth2UserInfo userInfo = new OAuth2UserInfo();
                userInfo.setProvider("google");

                // Google OAuth2 v3 API使用 'sub' 字段作为用户ID，v2使用 'id'
                String userId = jsonNode.has("sub") ? jsonNode.get("sub").asText() :
                               (jsonNode.has("id") ? jsonNode.get("id").asText() : null);
                if (userId == null) {
                    throw new OAuth2Exception("USER_ID_MISSING", "Google响应中缺少用户ID字段");
                }
                userInfo.setId(userId);

                // 安全获取其他字段
                userInfo.setEmail(jsonNode.has("email") ? jsonNode.get("email").asText() : null);
                userInfo.setName(jsonNode.has("name") ? jsonNode.get("name").asText() : null);
                userInfo.setAvatar(jsonNode.has("picture") ? jsonNode.get("picture").asText() : null);
                userInfo.setRawData(responseBody);

                log.info("成功获取Google用户信息: userId={}, email={}", userInfo.getId(), userInfo.getEmail());
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
            // 构建请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", githubClientId);
            params.add("client_secret", githubClientSecret);
            params.add("code", code);
            params.add("redirect_uri", githubRedirectUri);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Accept", "application/json"); // GitHub需要这个头

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            log.debug("向GitHub请求access token: {}", githubTokenUri);
            ResponseEntity<String> response = restTemplate.postForEntity(githubTokenUri, request, String.class);

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
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("Accept", "application/vnd.github.v3+json");

            HttpEntity<String> request = new HttpEntity<>(headers);

            log.debug("向GitHub请求用户信息: {}", githubUserInfoUri);
            ResponseEntity<String> response = restTemplate.exchange(githubUserInfoUri, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());

                OAuth2UserInfo userInfo = new OAuth2UserInfo();
                userInfo.setProvider("github");
                userInfo.setId(jsonNode.get("id").asText());

                // GitHub用户名处理：login是不可变的用户标识，name是可变的显示名称
                String githubLogin = jsonNode.get("login").asText();
                String githubName = jsonNode.has("name") && !jsonNode.get("name").isNull() ?
                    jsonNode.get("name").asText() : null;

                // OAuth绑定记录中保存login（不可变），用于生成系统用户名
                userInfo.setName(githubLogin);

                log.debug("【GitHub用户信息】login: {}, name: {}, OAuth记录保存login", githubLogin, githubName);

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
            String emailUrl = githubUserInfoUri.replace("/user", "/user/emails");

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
        LogUtils.logUserOperation(log, userId, "OAuth2绑定", "绑定OAuth账号: provider={}", provider);

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

        LogUtils.logUserOperation(log, userId, "OAuth2绑定", "OAuth2账号绑定成功: provider={}", provider);
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
        LocalDateTime now = LocalDateTime.now();

        // 更新OAuth表的登录信息
        oauth.setLastLoginTime(now);
        oauth.setLoginCount(oauth.getLoginCount() + 1);

        // 更新用户信息（如果有变化）
        if (!Objects.equals(oauth.getName(), userInfo.getName())) {
            oauth.setName(userInfo.getName());
        }
        if (!Objects.equals(oauth.getAvatar(), userInfo.getAvatar())) {
            oauth.setAvatar(userInfo.getAvatar());
        }

        userOAuthDao.updateById(oauth);

        // 同步更新User表的最后登录时间
        userService.updateLastLoginTime(oauth.getUserId());

        log.debug("【OAuth登录信息同步】已同步更新User表和OAuth表的最后登录时间，用户ID：{}", oauth.getUserId());
    }

    private User createUserFromOAuth(OAuth2UserInfo userInfo) {
        // 生成唯一用户名和随机密码
        String username = generateUniqueUsername(userInfo.getName());
        String randomPassword = UUID.randomUUID().toString();

        log.debug("【OAuth2用户创建】用户名：{}，邮箱：{}，提供商：{}",
                username, userInfo.getEmail(), userInfo.getProvider());

        // 使用重构后的registerUser方法，包含完整的用户创建和套餐绑定逻辑
        return userService.registerUser(username, userInfo.getEmail(), randomPassword, userInfo.getAvatar());
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

        // 优化用户名处理逻辑
        String cleanName = cleanUsername(baseName);

        log.debug("【用户名生成】原始名称: {}, 清理后: {}", baseName, cleanName);

        // 检查用户名是否已存在
        String username = cleanName;
        int counter = 1;
        while (userService.getUserByUsername(username) != null) {
            username = cleanName + "_" + counter++;
        }

        log.debug("【用户名生成】最终用户名: {}", username);
        return username;
    }

    /**
     * 清理用户名，确保符合用户名规范
     * GitHub的login字段本身就符合规范，主要处理其他特殊情况
     */
    private String cleanUsername(String baseName) {
        if (baseName == null || baseName.trim().isEmpty()) {
            return "user";
        }

        // 1. GitHub login通常只包含字母、数字、连字符和下划线，将连字符转为下划线
        String cleanName = baseName.replace("-", "_").replaceAll("[^a-zA-Z0-9_]", "");

        // 2. 如果清理后为空（极少情况），使用默认策略
        if (cleanName.isEmpty()) {
            cleanName = "user_" + System.currentTimeMillis() % 10000;
        }

        // 3. 确保用户名长度合理（3-30字符）
        if (cleanName.length() > 30) {
            cleanName = cleanName.substring(0, 30);
        } else if (cleanName.length() < 3) {
            cleanName = cleanName + "_" + System.currentTimeMillis() % 1000;
        }

        // 4. 确保不以数字开头
        if (cleanName.matches("^[0-9].*")) {
            cleanName = "u_" + cleanName;
        }

        return cleanName;
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
