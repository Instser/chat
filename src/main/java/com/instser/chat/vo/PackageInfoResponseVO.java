package com.instser.chat.vo;

import lombok.Data;
import java.util.List;

/**
 * 套餐信息响应VO类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
public class PackageInfoResponseVO {

    /**
     * 套餐信息
     */
    private FrontendPackageVO packageInfo;

    /**
     * 可用支付方式列表
     */
    private List<PaymentMethodVO> paymentMethods;

    public PackageInfoResponseVO() {}

    public PackageInfoResponseVO(FrontendPackageVO packageInfo, List<PaymentMethodVO> paymentMethods) {
        this.packageInfo = packageInfo;
        this.paymentMethods = paymentMethods;
    }
}
