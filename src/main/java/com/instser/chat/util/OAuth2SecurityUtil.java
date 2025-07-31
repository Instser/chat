package com.instser.chat.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * OAuth2安全工具类
 * 提供状态令牌生成、验证和令牌加密解密功能
 *
 * @author instser
 * @since 2025-07-16
 */
@Component
@Slf4j
public class OAuth2SecurityUtil {
    
    @Value("${oauth2.security.encryption-key:change-me-32-char-encryption-key}")
    private String encryptionKey;
    

    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    

    
    /**
     * 加密OAuth令牌
     */
    public String encryptToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        
        try {
            // 确保密钥长度为32字符（256位）
            String key = ensureKeyLength(encryptionKey);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            byte[] encryptedBytes = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
            
        } catch (Exception e) {
            log.error("令牌加密失败: {}", e.getMessage());
            throw new OAuth2SecurityException("令牌加密失败", e);
        }
    }
    
    /**
     * 解密OAuth令牌
     */
    public String decryptToken(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.isEmpty()) {
            return null;
        }
        
        try {
            // 确保密钥长度为32字符（256位）
            String key = ensureKeyLength(encryptionKey);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedToken));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("令牌解密失败: {}", e.getMessage());
            throw new OAuth2SecurityException("令牌解密失败", e);
        }
    }
    
    /**
     * 生成设备指纹
     */
    public String generateDeviceFingerprint(String userAgent, String clientIp) {
        if (userAgent == null) userAgent = "";
        if (clientIp == null) clientIp = "";
        
        String combined = userAgent + "|" + clientIp + "|" + System.currentTimeMillis();
        return hashString(combined);
    }
    

    
    /**
     * 计算风险评分
     */
    public int calculateRiskScore(String clientIp, String userAgent, int loginCount) {
        int riskScore = 0;
        
        // IP风险评估（简化版）
        if (clientIp != null) {
            if (clientIp.startsWith("127.") || clientIp.startsWith("192.168.") || clientIp.startsWith("10.")) {
                riskScore += 10; // 内网IP风险较低
            } else {
                riskScore += 20; // 外网IP风险中等
            }
        }
        
        // User-Agent风险评估
        if (userAgent == null || userAgent.isEmpty()) {
            riskScore += 30; // 缺少User-Agent风险较高
        } else if (userAgent.toLowerCase().contains("bot") || userAgent.toLowerCase().contains("crawler")) {
            riskScore += 50; // 机器人风险高
        }
        
        // 登录频率风险评估
        if (loginCount > 100) {
            riskScore += 10; // 频繁登录用户风险较低
        } else if (loginCount < 5) {
            riskScore += 20; // 新用户风险中等
        }
        
        return Math.min(riskScore, 100); // 最大风险评分100
    }
    
    /**
     * 确保密钥长度为32字符
     */
    private String ensureKeyLength(String key) {
        if (key.length() >= 32) {
            return key.substring(0, 32);
        } else {
            // 如果密钥长度不足，用默认字符填充
            StringBuilder sb = new StringBuilder(key);
            while (sb.length() < 32) {
                sb.append("0");
            }
            return sb.toString();
        }
    }
    
    /**
     * 字符串哈希
     */
    private String hashString(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            String hex = hexString.toString();
            // 安全截取，避免StringIndexOutOfBoundsException
            return hex.length() >= 16 ? hex.substring(0, 16) : hex; // 返回前16位或全部
        } catch (Exception e) {
            log.error("字符串哈希失败: {}", e.getMessage());
            String uuid = UUID.randomUUID().toString().replace("-", "");
            return uuid.length() >= 16 ? uuid.substring(0, 16) : uuid;
        }
    }
    
    /**
     * OAuth2安全异常
     */
    public static class OAuth2SecurityException extends RuntimeException {
        public OAuth2SecurityException(String message) {
            super(message);
        }
        
        public OAuth2SecurityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
