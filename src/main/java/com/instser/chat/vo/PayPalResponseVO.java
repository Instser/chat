package com.instser.chat.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * PayPal支付响应VO类
 * 继承基础PaymentResponseVO，添加PayPal特有字段
 *
 * @author instser
 * @since 2024-09-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayPalResponseVO extends PaymentResponseVO {

    /**
     * PayPal订单ID
     */
    private String paypalOrderId;

    /**
     * PayPal审批链接（用户需要访问此链接完成支付）
     */
    private String approvalUrl;

    /**
     * PayPal应用上下文
     */
    private PayPalAppContext appContext;

    /**
     * 支付授权链接
     */
    private String checkoutUrl;

    /**
     * PayPal环境信息
     */
    private String environment;

    /**
     * PayPal客户端ID（前端SDK需要）
     */
    private String paypalClientId;

    /**
     * 构造成功响应
     */
    public static PayPalResponseVO success(String orderNo, String paypalOrderId, 
                                          String approvalUrl, String paypalClientId,
                                          BigDecimal amount, String currency, 
                                          String environment) {
        PayPalResponseVO response = new PayPalResponseVO();
        response.setSuccess(true);
        response.setOrderNo(orderNo);
        response.setPaymentMethod("paypal");
        response.setAmount(amount);
        response.setCurrency(currency);
        response.setPaypalOrderId(paypalOrderId);
        response.setApprovalUrl(approvalUrl);
        response.setCheckoutUrl(approvalUrl); // 同一个URL
        response.setPaypalClientId(paypalClientId);
        response.setEnvironment(environment);
        response.setMessage("PayPal支付订单创建成功");
        return response;
    }

    /**
     * 构造失败响应
     */
    public static PayPalResponseVO failure(String orderNo, String errorMessage) {
        PayPalResponseVO response = new PayPalResponseVO();
        response.setSuccess(false);
        response.setOrderNo(orderNo);
        response.setPaymentMethod("paypal");
        response.setErrorMessage(errorMessage);
        response.setMessage("PayPal支付订单创建失败");
        return response;
    }

    /**
     * PayPal应用上下文配置
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PayPalAppContext {
        
        /**
         * 品牌名称
         */
        private String brandName;
        
        /**
         * 用户操作类型
         */
        private String userAction = "PAY_NOW";
        
        /**
         * 登陆页面类型
         */
        private String landingPage = "LOGIN";
        
        /**
         * 配送偏好
         */
        private String shippingPreference = "NO_SHIPPING";
        
        /**
         * 支付成功跳转URL
         */
        private String returnUrl;
        
        /**
         * 支付取消跳转URL
         */
        private String cancelUrl;

        public static PayPalAppContext defaultContext() {
            PayPalAppContext context = new PayPalAppContext();
            context.setBrandName("金鱼聊天");
            context.setUserAction("PAY_NOW");
            context.setLandingPage("LOGIN");
            context.setShippingPreference("NO_SHIPPING");
            return context;
        }
    }

    /**
     * 设置默认的应用上下文
     */
    public void setDefaultAppContext() {
        this.appContext = PayPalAppContext.defaultContext();
    }

    /**
     * 设置跳转URL
     */
    public void setRedirectUrls(String returnUrl, String cancelUrl) {
        if (this.appContext == null) {
            setDefaultAppContext();
        }
        this.appContext.setReturnUrl(returnUrl);
        this.appContext.setCancelUrl(cancelUrl);
    }
}