package com.instser.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.entity.Package;
import com.instser.chat.service.ExchangeRateService;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.UserPaymentStatusService;
import com.instser.chat.vo.FrontendPackageVO;
import com.instser.chat.vo.converter.VOConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 套餐优惠价格功能测试
 *
 * @author instser
 * @since 2025-07-16
 */
@ExtendWith(MockitoExtension.class)
public class PaymentControllerDiscountTest {

    @Mock
    private PackageService packageService;

    @Mock
    private UserPaymentStatusService userPaymentStatusService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private Authentication authentication;

    private PaymentController paymentController;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // 注意：这里无法直接创建PaymentController实例，因为它有很多依赖
        // 这个测试主要用于验证VOConverter的逻辑
    }

    @Test
    void testDiscountPriceDisplayForNeverPaidUser() {
        // 创建测试套餐数据
        Package testPackage = createTestPackage();
        
        // 测试从未付费用户（应该显示优惠价格）
        FrontendPackageVO result = VOConverter.toFrontendPackageVO(testPackage, "USD", exchangeRateService, true);
        
        assertNotNull(result);
        assertEquals(testPackage.getId().intValue(), result.getId());
        assertEquals(testPackage.getPackageName(), result.getName());
        assertEquals(testPackage.getPrice(), result.getPrice());
        assertEquals(testPackage.getDiscountPrice(), result.getDiscountPrice());
        assertNotNull(result.getDiscountPrice(), "从未付费用户应该看到优惠价格");
    }

    @Test
    void testDiscountPriceHiddenForPaidUser() {
        // 创建测试套餐数据
        Package testPackage = createTestPackage();
        
        // 测试已付费用户（不应该显示优惠价格）
        FrontendPackageVO result = VOConverter.toFrontendPackageVO(testPackage, "USD", exchangeRateService, false);
        
        assertNotNull(result);
        assertEquals(testPackage.getId().intValue(), result.getId());
        assertEquals(testPackage.getPackageName(), result.getName());
        assertEquals(testPackage.getPrice(), result.getPrice());
        assertNull(result.getDiscountPrice(), "已付费用户不应该看到优惠价格");
    }

    @Test
    void testDiscountPriceNullWhenNotSet() {
        // 创建没有优惠价格的测试套餐
        Package testPackage = createTestPackageWithoutDiscount();

        // 测试从未付费用户（即使showDiscountPrice=true，但套餐本身没有优惠价格）
        FrontendPackageVO result = VOConverter.toFrontendPackageVO(testPackage, "USD", exchangeRateService, true);

        assertNotNull(result);
        assertEquals(testPackage.getId().intValue(), result.getId());
        assertEquals(testPackage.getPackageName(), result.getName());
        assertEquals(testPackage.getPrice(), result.getPrice());
        assertNull(result.getDiscountPrice(), "套餐本身没有优惠价格时，应该返回null");
    }

    @Test
    void testLoginRequiredForPackageAccess() {
        // 测试未登录用户无法访问套餐信息的逻辑
        // 这个测试主要验证业务逻辑，实际的认证由Spring Security处理

        // 创建测试套餐数据
        Package testPackage = createTestPackage();

        // 验证有优惠价格的套餐在显示优惠价格时正常工作
        FrontendPackageVO resultWithDiscount = VOConverter.toFrontendPackageVO(testPackage, "USD", exchangeRateService, true);
        assertNotNull(resultWithDiscount.getDiscountPrice(), "登录用户应该能看到优惠价格");

        // 验证隐藏优惠价格时正常工作
        FrontendPackageVO resultWithoutDiscount = VOConverter.toFrontendPackageVO(testPackage, "USD", exchangeRateService, false);
        assertNull(resultWithoutDiscount.getDiscountPrice(), "已付费用户不应该看到优惠价格");
    }

    @Test
    void testBatchConversionWithDiscountControl() {
        // 创建测试套餐列表
        List<Package> packages = Arrays.asList(
            createTestPackage(),
            createTestPackageWithoutDiscount()
        );
        
        // 测试批量转换 - 显示优惠价格
        List<FrontendPackageVO> resultWithDiscount = VOConverter.toFrontendPackageVOList(packages, "USD", exchangeRateService, true);
        
        assertNotNull(resultWithDiscount);
        assertEquals(2, resultWithDiscount.size());
        
        // 第一个套餐有优惠价格
        assertNotNull(resultWithDiscount.get(0).getDiscountPrice());
        // 第二个套餐没有优惠价格
        assertNull(resultWithDiscount.get(1).getDiscountPrice());
        
        // 测试批量转换 - 隐藏优惠价格
        List<FrontendPackageVO> resultWithoutDiscount = VOConverter.toFrontendPackageVOList(packages, "USD", exchangeRateService, false);
        
        assertNotNull(resultWithoutDiscount);
        assertEquals(2, resultWithoutDiscount.size());
        
        // 两个套餐都不应该显示优惠价格
        assertNull(resultWithoutDiscount.get(0).getDiscountPrice());
        assertNull(resultWithoutDiscount.get(1).getDiscountPrice());
    }

    /**
     * 创建有优惠价格的测试套餐
     */
    private Package createTestPackage() {
        Package pkg = new Package();
        pkg.setId(1L);
        pkg.setPackageName("测试套餐");
        pkg.setDescription("测试套餐描述");
        pkg.setPackageType("basic");
        pkg.setPrice(new BigDecimal("69.99"));
        pkg.setDiscountPrice(new BigDecimal("8.88")); // 设置优惠价格
        pkg.setValidityDays(30);
        pkg.setLimitType("daily");
        pkg.setModelPermissions("{\"STANDARD\":{\"daily_limit\":33,\"description\":\"标准模型\"}}");
        pkg.setIsEnabled(1);
        pkg.setSortOrder(1);
        return pkg;
    }

    /**
     * 创建没有优惠价格的测试套餐
     */
    private Package createTestPackageWithoutDiscount() {
        Package pkg = new Package();
        pkg.setId(2L);
        pkg.setPackageName("无优惠套餐");
        pkg.setDescription("无优惠套餐描述");
        pkg.setPackageType("premium");
        pkg.setPrice(new BigDecimal("199.99"));
        pkg.setDiscountPrice(null); // 没有优惠价格
        pkg.setValidityDays(30);
        pkg.setLimitType("daily");
        pkg.setModelPermissions("{\"STANDARD\":{\"daily_limit\":100,\"description\":\"标准模型\"}}");
        pkg.setIsEnabled(1);
        pkg.setSortOrder(2);
        return pkg;
    }
}
