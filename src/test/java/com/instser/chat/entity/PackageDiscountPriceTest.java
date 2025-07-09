package com.instser.chat.entity;

import com.instser.chat.vo.FrontendPackageVO;
import com.instser.chat.vo.PackageVO;
import com.instser.chat.vo.converter.VOConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 套餐优惠价格字段测试类
 *
 * @author instser
 * @since 2025-07-09
 */
@DisplayName("套餐优惠价格字段测试")
public class PackageDiscountPriceTest {

    @Test
    @DisplayName("测试Package实体类优惠价格字段")
    public void testPackageDiscountPriceField() {
        // 创建套餐实体
        Package packageEntity = new Package();
        packageEntity.setId(1L);
        packageEntity.setPackageName("测试套餐");
        packageEntity.setDescription("测试套餐描述");
        packageEntity.setPackageType("basic");
        packageEntity.setPrice(new BigDecimal("99.00"));
        packageEntity.setDiscountPrice(new BigDecimal("79.00"));
        packageEntity.setValidityDays(30);
        packageEntity.setLimitType("daily");
        packageEntity.setIsEnabled(1);
        packageEntity.setSortOrder(1);

        // 验证优惠价格字段
        assertNotNull(packageEntity.getDiscountPrice());
        assertEquals(new BigDecimal("79.00"), packageEntity.getDiscountPrice());
        assertTrue(packageEntity.getDiscountPrice().compareTo(packageEntity.getPrice()) < 0);
    }

    @Test
    @DisplayName("测试Package实体类优惠价格为NULL的情况")
    public void testPackageDiscountPriceNull() {
        // 创建套餐实体（无优惠价格）
        Package packageEntity = new Package();
        packageEntity.setId(2L);
        packageEntity.setPackageName("无优惠套餐");
        packageEntity.setPrice(new BigDecimal("99.00"));
        packageEntity.setDiscountPrice(null);

        // 验证优惠价格为NULL
        assertNull(packageEntity.getDiscountPrice());
    }

    @Test
    @DisplayName("测试PackageVO转换包含优惠价格")
    public void testPackageVOConversion() {
        // 创建套餐实体
        Package packageEntity = new Package();
        packageEntity.setId(1L);
        packageEntity.setPackageName("测试套餐");
        packageEntity.setDescription("测试套餐描述");
        packageEntity.setPackageType("basic");
        packageEntity.setPrice(new BigDecimal("99.00"));
        packageEntity.setDiscountPrice(new BigDecimal("79.00"));
        packageEntity.setValidityDays(30);
        packageEntity.setLimitType("daily");
        packageEntity.setIsEnabled(1);
        packageEntity.setSortOrder(1);

        // 转换为PackageVO
        PackageVO packageVO = VOConverter.toPackageVO(packageEntity);

        // 验证转换结果
        assertNotNull(packageVO);
        assertNotNull(packageVO.getDiscountPrice());
        assertEquals(new BigDecimal("79.00"), packageVO.getDiscountPrice());
    }

    @Test
    @DisplayName("测试FrontendPackageVO转换包含优惠价格")
    public void testFrontendPackageVOConversion() {
        // 创建套餐实体
        Package packageEntity = new Package();
        packageEntity.setId(1L);
        packageEntity.setPackageName("测试套餐");
        packageEntity.setDescription("测试套餐描述");
        packageEntity.setPackageType("basic");
        packageEntity.setPrice(new BigDecimal("99.00"));
        packageEntity.setDiscountPrice(new BigDecimal("79.00"));
        packageEntity.setValidityDays(30);
        packageEntity.setLimitType("daily");
        packageEntity.setIsEnabled(1);
        packageEntity.setSortOrder(1);

        // 转换为FrontendPackageVO
        FrontendPackageVO frontendPackageVO = VOConverter.toFrontendPackageVO(packageEntity, "CNY");

        // 验证转换结果
        assertNotNull(frontendPackageVO);
        assertNotNull(frontendPackageVO.getDiscountPrice());
        assertEquals(new BigDecimal("79.00"), frontendPackageVO.getDiscountPrice());
    }

    @Test
    @DisplayName("测试优惠价格为NULL时的VO转换")
    public void testVOConversionWithNullDiscountPrice() {
        // 创建套餐实体（无优惠价格）
        Package packageEntity = new Package();
        packageEntity.setId(2L);
        packageEntity.setPackageName("无优惠套餐");
        packageEntity.setDescription("无优惠套餐描述");
        packageEntity.setPackageType("basic");
        packageEntity.setPrice(new BigDecimal("99.00"));
        packageEntity.setDiscountPrice(null);
        packageEntity.setValidityDays(30);
        packageEntity.setLimitType("daily");
        packageEntity.setIsEnabled(1);
        packageEntity.setSortOrder(1);

        // 转换为PackageVO
        PackageVO packageVO = VOConverter.toPackageVO(packageEntity);
        assertNotNull(packageVO);
        assertNull(packageVO.getDiscountPrice());

        // 转换为FrontendPackageVO
        FrontendPackageVO frontendPackageVO = VOConverter.toFrontendPackageVO(packageEntity, "CNY");
        assertNotNull(frontendPackageVO);
        assertNull(frontendPackageVO.getDiscountPrice());
    }
}
