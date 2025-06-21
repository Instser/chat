package com.instser.chat.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 二维码生成工具类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
public class QrCodeUtil {

    /**
     * 默认二维码API服务地址
     */
    private static final String DEFAULT_QR_API_URL = "https://api.qrserver.com/v1/create-qr-code/";

    /**
     * 默认二维码尺寸
     */
    private static final String DEFAULT_SIZE = "200x200";

    /**
     * 生成二维码URL
     *
     * @param content 二维码内容（通常是支付链接）
     * @return 二维码图片URL
     */
    public static String generateQrCodeUrl(String content) {
        return generateQrCodeUrl(content, DEFAULT_SIZE);
    }

    /**
     * 生成二维码URL
     *
     * @param content 二维码内容（通常是支付链接）
     * @param size    二维码尺寸，格式如"200x200"
     * @return 二维码图片URL
     */
    public static String generateQrCodeUrl(String content, String size) {
        if (!StringUtils.hasText(content)) {
            log.warn("二维码内容为空，无法生成二维码");
            return null;
        }

        try {
            // URL编码内容
            String encodedContent = URLEncoder.encode(content, StandardCharsets.UTF_8);
            
            // 验证尺寸格式
            String qrSize = StringUtils.hasText(size) ? size : DEFAULT_SIZE;
            
            // 构建二维码URL
            String qrCodeUrl = DEFAULT_QR_API_URL + "?size=" + qrSize + "&data=" + encodedContent;
            
            log.debug("生成二维码URL成功，内容长度: {}, 尺寸: {}", content.length(), qrSize);
            return qrCodeUrl;
            
        } catch (Exception e) {
            log.error("生成二维码URL失败，内容: {}", content, e);
            // 如果二维码生成失败，返回原始内容
            return content;
        }
    }

    /**
     * 生成支付二维码URL
     * 专门用于支付场景，会添加一些支付相关的优化
     *
     * @param paymentUrl 支付链接
     * @return 二维码图片URL
     */
    public static String generatePaymentQrCodeUrl(String paymentUrl) {
        if (!StringUtils.hasText(paymentUrl)) {
            log.warn("支付链接为空，无法生成支付二维码");
            return null;
        }

        try {
            // 对于支付场景，使用稍大的尺寸以便扫描
            String paymentQrSize = "250x250";
            
            // URL编码
            String encodedUrl = URLEncoder.encode(paymentUrl, StandardCharsets.UTF_8);
            
            // 构建二维码URL，添加支付相关的参数
            StringBuilder qrUrlBuilder = new StringBuilder(DEFAULT_QR_API_URL);
            qrUrlBuilder.append("?size=").append(paymentQrSize);
            qrUrlBuilder.append("&data=").append(encodedUrl);
            qrUrlBuilder.append("&format=png"); // 指定PNG格式
            qrUrlBuilder.append("&margin=10");  // 添加边距
            
            String qrCodeUrl = qrUrlBuilder.toString();
            
            log.info("生成支付二维码URL成功，支付链接: {}", paymentUrl);
            return qrCodeUrl;
            
        } catch (Exception e) {
            log.error("生成支付二维码URL失败", e);
            // 如果二维码生成失败，返回原始支付链接
            return paymentUrl;
        }
    }


}
