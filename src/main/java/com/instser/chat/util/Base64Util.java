package com.instser.chat.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Base64;

/**
 * Base64编码工具类
 * 用于图片和二进制文件的Base64编码处理
 *
 * @author instser
 * @since 2025-06-27
 */
@Slf4j
public class Base64Util {

    /**
     * 将字节数组编码为Base64字符串
     *
     * @param bytes 字节数组
     * @return Base64编码字符串
     */
    public static String encodeToString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            log.warn("尝试编码空字节数组");
            return "";
        }
        
        try {
            String encoded = Base64.getEncoder().encodeToString(bytes);
            log.debug("Base64编码成功，原始大小: {} bytes，编码后长度: {} 字符", bytes.length, encoded.length());
            return encoded;
        } catch (Exception e) {
            log.error("Base64编码失败", e);
            throw new RuntimeException("Base64编码失败：" + e.getMessage());
        }
    }

    /**
     * 将Base64字符串解码为字节数组
     *
     * @param base64String Base64编码字符串
     * @return 解码后的字节数组
     */
    public static byte[] decodeFromString(String base64String) {
        if (base64String == null || base64String.trim().isEmpty()) {
            log.warn("尝试解码空Base64字符串");
            return new byte[0];
        }
        
        try {
            byte[] decoded = Base64.getDecoder().decode(base64String.trim());
            log.debug("Base64解码成功，编码长度: {} 字符，解码后大小: {} bytes", base64String.length(), decoded.length);
            return decoded;
        } catch (Exception e) {
            log.error("Base64解码失败，输入字符串长度: {}", base64String.length(), e);
            throw new RuntimeException("Base64解码失败：" + e.getMessage());
        }
    }

    /**
     * 将图片字节数组转换为data URL格式的Base64字符串
     *
     * @param imageBytes 图片字节数组
     * @param mimeType   图片MIME类型（如image/png, image/jpeg等）
     * @return data URL格式的Base64字符串
     */
    public static String encodeImageToDataUrl(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("尝试编码空图片字节数组");
            return "";
        }
        
        if (mimeType == null || mimeType.trim().isEmpty()) {
            log.warn("图片MIME类型为空，使用默认类型 image/png");
            mimeType = "image/png";
        }
        
        try {
            String base64Data = encodeToString(imageBytes);
            String dataUrl = "data:" + mimeType + ";base64," + base64Data;
            log.debug("图片转换为data URL成功，MIME类型: {}，原始大小: {} bytes", mimeType, imageBytes.length);
            return dataUrl;
        } catch (Exception e) {
            log.error("图片转换为data URL失败，MIME类型: {}", mimeType, e);
            throw new RuntimeException("图片转换为data URL失败：" + e.getMessage());
        }
    }

    /**
     * 从data URL格式的Base64字符串中提取纯Base64数据
     *
     * @param dataUrl data URL格式的字符串
     * @return 纯Base64数据字符串
     */
    public static String extractBase64FromDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.trim().isEmpty()) {
            log.warn("尝试从空data URL提取Base64数据");
            return "";
        }
        
        try {
            // data URL格式: data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...
            if (dataUrl.startsWith("data:") && dataUrl.contains("base64,")) {
                int base64Index = dataUrl.indexOf("base64,") + 7;
                String base64Data = dataUrl.substring(base64Index);
                log.debug("从data URL提取Base64数据成功，数据长度: {} 字符", base64Data.length());
                return base64Data;
            } else {
                log.debug("输入不是data URL格式，直接返回原始字符串");
                return dataUrl;
            }
        } catch (Exception e) {
            log.error("从data URL提取Base64数据失败", e);
            throw new RuntimeException("从data URL提取Base64数据失败：" + e.getMessage());
        }
    }

    /**
     * 验证Base64字符串格式是否正确
     *
     * @param base64String Base64字符串
     * @return 是否为有效的Base64格式
     */
    public static boolean isValidBase64(String base64String) {
        if (base64String == null || base64String.trim().isEmpty()) {
            return false;
        }
        
        try {
            Base64.getDecoder().decode(base64String.trim());
            return true;
        } catch (Exception e) {
            log.debug("Base64格式验证失败: {}", e.getMessage());
            return false;
        }
    }
}
