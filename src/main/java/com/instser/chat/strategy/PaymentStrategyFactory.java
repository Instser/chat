package com.instser.chat.strategy;

import com.instser.chat.common.exception.PaymentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 支付策略工厂
 * 管理和获取不同的支付策略实现
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Component
public class PaymentStrategyFactory {

    private final List<PaymentStrategy> paymentStrategies;
    private final Map<String, PaymentStrategy> strategyMap;

    /**
     * 构造函数，初始化策略映射
     */
    public PaymentStrategyFactory(List<PaymentStrategy> paymentStrategies) {
        this.paymentStrategies = paymentStrategies;
        this.strategyMap = paymentStrategies.stream()
                .collect(Collectors.toMap(
                    PaymentStrategy::getPaymentMethod,
                    Function.identity()
                ));

        log.info("支付策略工厂初始化完成，支持的支付方式: {}", strategyMap.keySet());
    }

    /**
     * 根据支付方式获取对应的策略
     *
     * @param paymentMethod 支付方式
     * @return 支付策略
     * @throws PaymentException 如果不支持该支付方式
     */
    public PaymentStrategy getStrategy(String paymentMethod) {
        PaymentStrategy strategy = strategyMap.get(paymentMethod);
        if (strategy == null) {
            log.error("不支持的支付方式: {}, 支持的方式: {}", paymentMethod, strategyMap.keySet());
            throw PaymentException.unsupportedPaymentMethod(paymentMethod);
        }
        return strategy;
    }

    /**
     * 获取所有支持的支付方式
     *
     * @return 支付方式列表
     */
    public List<String> getSupportedPaymentMethods() {
        return paymentStrategies.stream()
                .map(PaymentStrategy::getPaymentMethod)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有配置完整的支付方式
     *
     * @return 可用的支付方式列表
     */
    public List<String> getAvailablePaymentMethods() {
        return paymentStrategies.stream()
                .filter(PaymentStrategy::isConfigComplete)
                .map(PaymentStrategy::getPaymentMethod)
                .collect(Collectors.toList());
    }

    /**
     * 检查支付方式是否支持
     *
     * @param paymentMethod 支付方式
     * @return 是否支持
     */
    public boolean isSupported(String paymentMethod) {
        return strategyMap.containsKey(paymentMethod);
    }

    /**
     * 检查支付方式是否可用（配置完整）
     *
     * @param paymentMethod 支付方式
     * @return 是否可用
     */
    public boolean isAvailable(String paymentMethod) {
        PaymentStrategy strategy = strategyMap.get(paymentMethod);
        return strategy != null && strategy.isConfigComplete();
    }
}
