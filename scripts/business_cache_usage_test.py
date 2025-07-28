#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
业务缓存使用测试方案
测试系统中所有业务功能对缓存的实际使用情况，包括缓存命中、降级、熔断机制

作者: Augment Agent
创建时间: 2025-07-28 16:15
版本: v1.0 - 业务缓存测试
"""

import requests
import json
import time
import threading
import logging
import os
import sys
import random
import string
import uuid
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple
from dataclasses import dataclass, asdict
from concurrent.futures import ThreadPoolExecutor, as_completed
import argparse
import statistics

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('business_cache_usage_test.log', encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)

@dataclass
class TestConfig:
    """测试配置"""
    base_url: str = "http://127.0.0.1:8080"
    timeout: int = 30
    
    # 测试用户配置
    test_users: List[Dict] = None
    test_sessions: List[str] = None
    
    # 测试配置
    enable_cache_hit_tests: bool = True
    enable_cache_miss_tests: bool = True
    enable_degradation_tests: bool = True
    enable_circuit_breaker_tests: bool = True
    enable_performance_tests: bool = True
    
    # 性能测试配置
    concurrent_users: int = 10
    test_duration: int = 120
    
    # 输出配置
    output_dir: str = "./business_cache_test_results"
    
    def __post_init__(self):
        if self.test_users is None:
            self.test_users = []
        if self.test_sessions is None:
            self.test_sessions = []

@dataclass
class CacheTestResult:
    """缓存测试结果"""
    test_name: str
    business_module: str
    cache_type: str
    operation: str
    success: bool
    response_time: float
    cache_hit: bool = False
    cache_miss: bool = False
    fallback_used: bool = False
    circuit_breaker_open: bool = False
    error_message: str = ""
    timestamp: datetime = None
    
    def __post_init__(self):
        if self.timestamp is None:
            self.timestamp = datetime.now()

class BusinessCacheUsageTester:
    """业务缓存使用测试器"""
    
    def __init__(self, config: TestConfig):
        self.config = config
        self.session = requests.Session()
        self.session.timeout = config.timeout
        self.test_results = []
        self.lock = threading.Lock()
        
        # 创建输出目录
        os.makedirs(config.output_dir, exist_ok=True)
        
        logger.info(f"业务缓存使用测试器初始化完成")
        logger.info(f"基础URL: {config.base_url}")
    
    def record_result(self, result: CacheTestResult):
        """记录测试结果"""
        with self.lock:
            self.test_results.append(result)
    
    def make_business_request(self, method: str, endpoint: str, test_name: str, 
                             business_module: str, cache_type: str, **kwargs) -> CacheTestResult:
        """发送业务请求并分析缓存使用情况"""
        start_time = time.time()
        url = f"{self.config.base_url}{endpoint}"
        
        try:
            response = self.session.request(method, url, **kwargs)
            response_time = time.time() - start_time
            
            success = response.status_code < 400
            
            # 分析响应时间来推断缓存命中情况
            cache_hit = response_time < 0.05  # 小于50ms认为是缓存命中
            cache_miss = response_time > 0.2   # 大于200ms认为是缓存未命中
            
            result = CacheTestResult(
                test_name=test_name,
                business_module=business_module,
                cache_type=cache_type,
                operation=f"{method} {endpoint}",
                success=success,
                response_time=response_time,
                cache_hit=cache_hit,
                cache_miss=cache_miss,
                error_message="" if success else response.text[:200]
            )
            
            self.record_result(result)
            return result
            
        except Exception as e:
            response_time = time.time() - start_time
            result = CacheTestResult(
                test_name=test_name,
                business_module=business_module,
                cache_type=cache_type,
                operation=f"{method} {endpoint}",
                success=False,
                response_time=response_time,
                error_message=str(e)
            )
            
            self.record_result(result)
            return result

class UserAuthCacheTester:
    """用户认证缓存测试器"""
    
    def __init__(self, cache_tester: BusinessCacheUsageTester):
        self.cache_tester = cache_tester
        self.config = cache_tester.config
    
    def test_user_authentication_cache(self):
        """测试用户认证缓存"""
        logger.info("测试用户认证缓存...")
        
        # 1. 测试用户登录缓存
        self.test_user_login_cache()
        
        # 2. 测试用户信息缓存
        self.test_user_info_cache()
        
        # 3. 测试用户权限缓存
        self.test_user_permission_cache()
    
    def test_user_login_cache(self):
        """测试用户登录缓存"""
        login_data = {
            "usernameOrEmail": "test@example.com",
            "password": "test123456"
        }
        
        # 第一次登录 - 应该缓存未命中
        result1 = self.cache_tester.make_business_request(
            "POST", "/api/auth/login",
            test_name="用户登录-首次",
            business_module="user_auth",
            cache_type="user_session",
            json=login_data
        )
        
        # 短时间内再次登录 - 可能命中缓存
        time.sleep(1)
        result2 = self.cache_tester.make_business_request(
            "POST", "/api/auth/login",
            test_name="用户登录-重复",
            business_module="user_auth",
            cache_type="user_session",
            json=login_data
        )
        
        logger.info(f"登录缓存测试: 首次={result1.response_time:.3f}s, 重复={result2.response_time:.3f}s")
    
    def test_user_info_cache(self):
        """测试用户信息缓存"""
        # 测试获取当前用户信息
        self.cache_tester.make_business_request(
            "GET", "/api/auth/me",
            test_name="获取用户信息",
            business_module="user_auth",
            cache_type="user_info"
        )
    
    def test_user_permission_cache(self):
        """测试用户权限缓存"""
        # 测试用户权限检查（通过访问需要权限的接口）
        self.cache_tester.make_business_request(
            "GET", "/api/user/profile",
            test_name="用户权限检查",
            business_module="user_auth",
            cache_type="user_permission"
        )

class ChatCacheTester:
    """聊天缓存测试器"""
    
    def __init__(self, cache_tester: BusinessCacheUsageTester):
        self.cache_tester = cache_tester
        self.config = cache_tester.config
    
    def test_chat_cache_usage(self):
        """测试聊天缓存使用"""
        logger.info("测试聊天缓存使用...")
        
        # 1. 测试聊天会话缓存
        self.test_chat_session_cache()
        
        # 2. 测试聊天消息缓存
        self.test_chat_message_cache()
        
        # 3. 测试聊天频率限制缓存
        self.test_chat_rate_limit_cache()
    
    def test_chat_session_cache(self):
        """测试聊天会话缓存"""
        # 创建聊天会话
        session_data = {
            "title": f"测试会话_{uuid.uuid4().hex[:8]}",
            "model": "gpt-3.5-turbo"
        }
        
        result = self.cache_tester.make_business_request(
            "POST", "/api/chat/sessions",
            test_name="创建聊天会话",
            business_module="chat",
            cache_type="chat_session",
            json=session_data
        )
        
        # 获取会话列表 - 应该命中缓存
        self.cache_tester.make_business_request(
            "GET", "/api/chat/sessions",
            test_name="获取会话列表",
            business_module="chat",
            cache_type="chat_session"
        )
    
    def test_chat_message_cache(self):
        """测试聊天消息缓存"""
        # 发送聊天消息
        chat_data = {
            "sessionId": "test_session_123",
            "message": f"测试消息 {uuid.uuid4().hex[:8]}",
            "model": "gpt-3.5-turbo"
        }
        
        self.cache_tester.make_business_request(
            "POST", "/api/chat",
            test_name="发送聊天消息",
            business_module="chat",
            cache_type="chat_message",
            json=chat_data
        )
        
        # 获取会话消息 - 应该命中缓存
        self.cache_tester.make_business_request(
            "GET", "/api/chat/sessions/test_session_123/messages",
            test_name="获取会话消息",
            business_module="chat",
            cache_type="chat_message"
        )
    
    def test_chat_rate_limit_cache(self):
        """测试聊天频率限制缓存"""
        # 快速发送多条消息测试频率限制
        for i in range(3):
            chat_data = {
                "sessionId": "rate_limit_test",
                "message": f"频率测试消息 {i+1}",
                "model": "gpt-3.5-turbo"
            }
            
            self.cache_tester.make_business_request(
                "POST", "/api/chat",
                test_name=f"频率限制测试-{i+1}",
                business_module="chat",
                cache_type="chat_rate_limit",
                json=chat_data
            )
            
            time.sleep(0.5)  # 短间隔发送

class PaymentCacheTester:
    """支付缓存测试器"""
    
    def __init__(self, cache_tester: BusinessCacheUsageTester):
        self.cache_tester = cache_tester
        self.config = cache_tester.config
    
    def test_payment_cache_usage(self):
        """测试支付缓存使用"""
        logger.info("测试支付缓存使用...")
        
        # 1. 测试支付频率限制缓存
        self.test_payment_frequency_cache()
        
        # 2. 测试支付会话缓存
        self.test_payment_session_cache()
        
        # 3. 测试支付回调防重放缓存
        self.test_payment_callback_cache()
    
    def test_payment_frequency_cache(self):
        """测试支付频率限制缓存"""
        payment_data = {
            "packageId": 1,
            "paymentMethod": "alipay",
            "amount": 10.00
        }
        
        # 连续发起多次支付请求测试频率限制
        for i in range(3):
            self.cache_tester.make_business_request(
                "POST", "/api/payment/create",
                test_name=f"支付频率测试-{i+1}",
                business_module="payment",
                cache_type="payment_frequency",
                json=payment_data
            )
            
            time.sleep(1)
    
    def test_payment_session_cache(self):
        """测试支付会话缓存"""
        payment_data = {
            "packageId": 1,
            "paymentMethod": "alipay",
            "amount": 10.00
        }
        
        # 测试支付会话锁
        self.cache_tester.make_business_request(
            "POST", "/api/payment/create",
            test_name="支付会话锁测试",
            business_module="payment",
            cache_type="payment_session",
            json=payment_data
        )
    
    def test_payment_callback_cache(self):
        """测试支付回调防重放缓存"""
        # 模拟支付回调（这里只是测试接口访问，实际回调需要特殊处理）
        callback_data = {
            "trade_no": f"test_trade_{uuid.uuid4().hex[:8]}",
            "out_trade_no": f"test_order_{uuid.uuid4().hex[:8]}",
            "trade_status": "TRADE_SUCCESS"
        }
        
        # 第一次回调
        self.cache_tester.make_business_request(
            "POST", "/api/payment/alipay/notify",
            test_name="支付回调-首次",
            business_module="payment",
            cache_type="payment_callback",
            data=callback_data
        )
        
        # 重复回调 - 应该被防重放机制拦截
        self.cache_tester.make_business_request(
            "POST", "/api/payment/alipay/notify",
            test_name="支付回调-重复",
            business_module="payment",
            cache_type="payment_callback",
            data=callback_data
        )

class OAuth2CacheTester:
    """OAuth2缓存测试器"""

    def __init__(self, cache_tester: BusinessCacheUsageTester):
        self.cache_tester = cache_tester
        self.config = cache_tester.config

    def test_oauth2_cache_usage(self):
        """测试OAuth2缓存使用"""
        logger.info("测试OAuth2缓存使用...")

        # 1. 测试OAuth2用户信息缓存
        self.test_oauth2_user_info_cache()

        # 2. 测试OAuth2提供商配置缓存
        self.test_oauth2_provider_config_cache()

    def test_oauth2_user_info_cache(self):
        """测试OAuth2用户信息缓存"""
        oauth_data = {
            "code": "test_auth_code",
            "state": "test_state"
        }

        # 测试Google OAuth2登录
        self.cache_tester.make_business_request(
            "POST", "/api/oauth2/login/google",
            test_name="OAuth2用户信息缓存-Google",
            business_module="oauth2",
            cache_type="oauth2_user_info",
            json=oauth_data
        )

        # 测试GitHub OAuth2登录
        self.cache_tester.make_business_request(
            "POST", "/api/oauth2/login/github",
            test_name="OAuth2用户信息缓存-GitHub",
            business_module="oauth2",
            cache_type="oauth2_user_info",
            json=oauth_data
        )

    def test_oauth2_provider_config_cache(self):
        """测试OAuth2提供商配置缓存"""
        # 测试获取OAuth2配置（通过访问OAuth2相关接口）
        self.cache_tester.make_business_request(
            "GET", "/api/oauth2/config/google",
            test_name="OAuth2配置缓存-Google",
            business_module="oauth2",
            cache_type="oauth2_provider_config"
        )

class ExchangeRateCacheTester:
    """汇率缓存测试器"""

    def __init__(self, cache_tester: BusinessCacheUsageTester):
        self.cache_tester = cache_tester
        self.config = cache_tester.config

    def test_exchange_rate_cache(self):
        """测试汇率缓存"""
        logger.info("测试汇率缓存...")

        # 测试获取汇率信息
        self.cache_tester.make_business_request(
            "GET", "/api/exchange/rates",
            test_name="汇率缓存测试",
            business_module="exchange",
            cache_type="exchange_rates"
        )

        # 短时间内再次获取 - 应该命中缓存
        time.sleep(1)
        self.cache_tester.make_business_request(
            "GET", "/api/exchange/rates",
            test_name="汇率缓存命中测试",
            business_module="exchange",
            cache_type="exchange_rates"
        )

class WebSearchCacheTester:
    """网络搜索缓存测试器"""

    def __init__(self, cache_tester: BusinessCacheUsageTester):
        self.cache_tester = cache_tester
        self.config = cache_tester.config

    def test_websearch_cache(self):
        """测试网络搜索缓存"""
        logger.info("测试网络搜索缓存...")

        # 测试网络搜索权限缓存
        search_data = {
            "query": "test search query",
            "limit": 5
        }

        self.cache_tester.make_business_request(
            "POST", "/api/websearch/search",
            test_name="网络搜索权限缓存",
            business_module="websearch",
            cache_type="websearch_permission",
            json=search_data
        )

class CacheDegradationTester:
    """缓存降级测试器"""

    def __init__(self, cache_tester: BusinessCacheUsageTester):
        self.cache_tester = cache_tester
        self.config = cache_tester.config

    def test_cache_degradation(self):
        """测试缓存降级机制"""
        logger.info("测试缓存降级机制...")

        # 1. 测试Redis不可用时的降级
        self.test_redis_unavailable_degradation()

        # 2. 测试缓存超时降级
        self.test_cache_timeout_degradation()

    def test_redis_unavailable_degradation(self):
        """测试Redis不可用时的降级"""
        # 通过大量并发请求来模拟Redis压力，可能触发熔断
        logger.info("模拟Redis压力测试...")

        def concurrent_request():
            self.cache_tester.make_business_request(
                "GET", "/api/auth/me",
                test_name="Redis压力测试",
                business_module="degradation",
                cache_type="redis_degradation"
            )

        # 创建大量并发请求
        with ThreadPoolExecutor(max_workers=20) as executor:
            futures = [executor.submit(concurrent_request) for _ in range(50)]

            for future in as_completed(futures):
                try:
                    future.result()
                except Exception as e:
                    logger.error(f"并发请求异常: {e}")

    def test_cache_timeout_degradation(self):
        """测试缓存超时降级"""
        # 测试长时间运行的请求，可能触发缓存超时
        self.cache_tester.make_business_request(
            "POST", "/api/chat",
            test_name="缓存超时降级测试",
            business_module="degradation",
            cache_type="timeout_degradation",
            json={
                "sessionId": "timeout_test",
                "message": "这是一个用于测试缓存超时的长消息" * 100,
                "model": "gpt-3.5-turbo"
            }
        )

class CircuitBreakerTester:
    """熔断器测试器"""

    def __init__(self, cache_tester: BusinessCacheUsageTester):
        self.cache_tester = cache_tester
        self.config = cache_tester.config

    def test_circuit_breaker(self):
        """测试熔断器机制"""
        logger.info("测试熔断器机制...")

        # 1. 测试正常状态
        self.test_circuit_breaker_closed()

        # 2. 尝试触发熔断
        self.test_circuit_breaker_trigger()

        # 3. 测试熔断恢复
        self.test_circuit_breaker_recovery()

    def test_circuit_breaker_closed(self):
        """测试熔断器关闭状态（正常状态）"""
        self.cache_tester.make_business_request(
            "GET", "/api/auth/me",
            test_name="熔断器关闭状态测试",
            business_module="circuit_breaker",
            cache_type="circuit_breaker_closed"
        )

    def test_circuit_breaker_trigger(self):
        """尝试触发熔断器"""
        # 通过快速发送大量请求来尝试触发熔断
        logger.info("尝试触发熔断器...")

        for i in range(10):
            self.cache_tester.make_business_request(
                "POST", "/api/payment/create",
                test_name=f"熔断器触发测试-{i+1}",
                business_module="circuit_breaker",
                cache_type="circuit_breaker_trigger",
                json={
                    "packageId": 999,  # 不存在的套餐ID，可能导致错误
                    "paymentMethod": "invalid",
                    "amount": -1  # 无效金额
                }
            )

            time.sleep(0.1)  # 快速请求

    def test_circuit_breaker_recovery(self):
        """测试熔断器恢复"""
        # 等待一段时间后测试恢复
        logger.info("等待熔断器恢复...")
        time.sleep(5)

        self.cache_tester.make_business_request(
            "GET", "/api/auth/me",
            test_name="熔断器恢复测试",
            business_module="circuit_breaker",
            cache_type="circuit_breaker_recovery"
        )

class PerformanceCacheTester:
    """性能缓存测试器"""

    def __init__(self, cache_tester: BusinessCacheUsageTester):
        self.cache_tester = cache_tester
        self.config = cache_tester.config

    def test_cache_performance(self):
        """测试缓存性能"""
        logger.info("测试缓存性能...")

        # 1. 测试缓存命中性能
        self.test_cache_hit_performance()

        # 2. 测试缓存未命中性能
        self.test_cache_miss_performance()

        # 3. 测试并发缓存性能
        self.test_concurrent_cache_performance()

    def test_cache_hit_performance(self):
        """测试缓存命中性能"""
        # 先访问一次建立缓存
        self.cache_tester.make_business_request(
            "GET", "/api/auth/me",
            test_name="建立缓存",
            business_module="performance",
            cache_type="cache_setup"
        )

        # 然后多次访问测试缓存命中性能
        for i in range(5):
            self.cache_tester.make_business_request(
                "GET", "/api/auth/me",
                test_name=f"缓存命中性能测试-{i+1}",
                business_module="performance",
                cache_type="cache_hit_performance"
            )

            time.sleep(0.1)

    def test_cache_miss_performance(self):
        """测试缓存未命中性能"""
        # 访问不同的资源测试缓存未命中性能
        for i in range(3):
            self.cache_tester.make_business_request(
                "GET", f"/api/user/profile/{random.randint(1000, 9999)}",
                test_name=f"缓存未命中性能测试-{i+1}",
                business_module="performance",
                cache_type="cache_miss_performance"
            )

    def test_concurrent_cache_performance(self):
        """测试并发缓存性能"""
        def concurrent_cache_request():
            self.cache_tester.make_business_request(
                "GET", "/api/auth/me",
                test_name="并发缓存性能测试",
                business_module="performance",
                cache_type="concurrent_cache_performance"
            )

        # 创建并发请求
        with ThreadPoolExecutor(max_workers=self.config.concurrent_users) as executor:
            futures = [executor.submit(concurrent_cache_request) for _ in range(self.config.concurrent_users)]

            for future in as_completed(futures):
                try:
                    future.result()
                except Exception as e:
                    logger.error(f"并发缓存请求异常: {e}")

class TestReportGenerator:
    """测试报告生成器"""

    def __init__(self, cache_tester: BusinessCacheUsageTester):
        self.cache_tester = cache_tester
        self.config = cache_tester.config

    def generate_report(self):
        """生成测试报告"""
        logger.info("生成业务缓存使用测试报告...")

        results = self.cache_tester.test_results

        # 按业务模块分组统计
        module_stats = {}
        cache_type_stats = {}

        for result in results:
            # 按模块统计
            module = result.business_module
            if module not in module_stats:
                module_stats[module] = {
                    'total': 0, 'success': 0, 'cache_hits': 0, 'cache_misses': 0,
                    'response_times': [], 'fallback_used': 0
                }

            stats = module_stats[module]
            stats['total'] += 1
            if result.success:
                stats['success'] += 1
            if result.cache_hit:
                stats['cache_hits'] += 1
            if result.cache_miss:
                stats['cache_misses'] += 1
            if result.fallback_used:
                stats['fallback_used'] += 1
            stats['response_times'].append(result.response_time)

            # 按缓存类型统计
            cache_type = result.cache_type
            if cache_type not in cache_type_stats:
                cache_type_stats[cache_type] = {
                    'total': 0, 'cache_hits': 0, 'cache_misses': 0, 'response_times': []
                }

            cache_stats = cache_type_stats[cache_type]
            cache_stats['total'] += 1
            if result.cache_hit:
                cache_stats['cache_hits'] += 1
            if result.cache_miss:
                cache_stats['cache_misses'] += 1
            cache_stats['response_times'].append(result.response_time)

        # 生成报告
        report = {
            'test_summary': {
                'total_tests': len(results),
                'total_success': sum(1 for r in results if r.success),
                'total_cache_hits': sum(1 for r in results if r.cache_hit),
                'total_cache_misses': sum(1 for r in results if r.cache_miss),
                'total_fallbacks': sum(1 for r in results if r.fallback_used),
                'test_duration': (results[-1].timestamp - results[0].timestamp).total_seconds() if results else 0
            },
            'module_statistics': {},
            'cache_type_statistics': {},
            'performance_analysis': {},
            'timestamp': datetime.now().isoformat()
        }

        # 模块统计
        for module, stats in module_stats.items():
            response_times = stats['response_times']
            report['module_statistics'][module] = {
                'total_requests': stats['total'],
                'success_rate': stats['success'] / stats['total'] if stats['total'] > 0 else 0,
                'cache_hit_rate': stats['cache_hits'] / stats['total'] if stats['total'] > 0 else 0,
                'cache_miss_rate': stats['cache_misses'] / stats['total'] if stats['total'] > 0 else 0,
                'fallback_rate': stats['fallback_used'] / stats['total'] if stats['total'] > 0 else 0,
                'avg_response_time': statistics.mean(response_times) if response_times else 0,
                'median_response_time': statistics.median(response_times) if response_times else 0
            }

        # 缓存类型统计
        for cache_type, stats in cache_type_stats.items():
            response_times = stats['response_times']
            report['cache_type_statistics'][cache_type] = {
                'total_requests': stats['total'],
                'cache_hit_rate': stats['cache_hits'] / stats['total'] if stats['total'] > 0 else 0,
                'cache_miss_rate': stats['cache_misses'] / stats['total'] if stats['total'] > 0 else 0,
                'avg_response_time': statistics.mean(response_times) if response_times else 0
            }

        # 保存报告
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        report_file = os.path.join(self.config.output_dir, f"business_cache_test_report_{timestamp}.json")

        with open(report_file, 'w', encoding='utf-8') as f:
            json.dump(report, f, ensure_ascii=False, indent=2, default=str)

        # 打印摘要
        self.print_summary(report)

        logger.info(f"测试报告已保存到: {report_file}")

    def print_summary(self, report: Dict):
        """打印测试摘要"""
        summary = report['test_summary']

        logger.info("=" * 80)
        logger.info("业务缓存使用测试完成摘要")
        logger.info("=" * 80)

        logger.info(f"总测试数: {summary['total_tests']}")
        logger.info(f"成功测试: {summary['total_success']}")
        logger.info(f"缓存命中: {summary['total_cache_hits']}")
        logger.info(f"缓存未命中: {summary['total_cache_misses']}")
        logger.info(f"降级使用: {summary['total_fallbacks']}")
        logger.info(f"测试时长: {summary['test_duration']:.2f}秒")

        if summary['total_tests'] > 0:
            logger.info(f"成功率: {summary['total_success']/summary['total_tests']:.2%}")
            logger.info(f"缓存命中率: {summary['total_cache_hits']/summary['total_tests']:.2%}")

        logger.info("\n按业务模块统计:")
        for module, stats in report['module_statistics'].items():
            logger.info(f"  {module}:")
            logger.info(f"    请求数: {stats['total_requests']}")
            logger.info(f"    成功率: {stats['success_rate']:.2%}")
            logger.info(f"    缓存命中率: {stats['cache_hit_rate']:.2%}")
            logger.info(f"    平均响应时间: {stats['avg_response_time']:.3f}s")

        logger.info("=" * 80)

class BusinessCacheTestRunner:
    """业务缓存测试运行器"""

    def __init__(self, config: TestConfig):
        self.config = config
        self.cache_tester = BusinessCacheUsageTester(config)

        # 初始化各种测试器
        self.user_auth_tester = UserAuthCacheTester(self.cache_tester)
        self.chat_tester = ChatCacheTester(self.cache_tester)
        self.payment_tester = PaymentCacheTester(self.cache_tester)
        self.oauth2_tester = OAuth2CacheTester(self.cache_tester)
        self.exchange_tester = ExchangeRateCacheTester(self.cache_tester)
        self.websearch_tester = WebSearchCacheTester(self.cache_tester)
        self.degradation_tester = CacheDegradationTester(self.cache_tester)
        self.circuit_breaker_tester = CircuitBreakerTester(self.cache_tester)
        self.performance_tester = PerformanceCacheTester(self.cache_tester)
        self.report_generator = TestReportGenerator(self.cache_tester)

    def run_all_tests(self):
        """运行所有业务缓存测试"""
        logger.info("开始业务缓存使用测试")
        logger.info("=" * 80)

        start_time = datetime.now()

        try:
            # 1. 用户认证缓存测试
            if self.config.enable_cache_hit_tests:
                logger.info("第1阶段: 用户认证缓存测试")
                self.user_auth_tester.test_user_authentication_cache()

            # 2. 聊天缓存测试
            if self.config.enable_cache_hit_tests:
                logger.info("第2阶段: 聊天缓存测试")
                self.chat_tester.test_chat_cache_usage()

            # 3. 支付缓存测试
            if self.config.enable_cache_hit_tests:
                logger.info("第3阶段: 支付缓存测试")
                self.payment_tester.test_payment_cache_usage()

            # 4. OAuth2缓存测试
            if self.config.enable_cache_hit_tests:
                logger.info("第4阶段: OAuth2缓存测试")
                self.oauth2_tester.test_oauth2_cache_usage()

            # 5. 汇率缓存测试
            if self.config.enable_cache_hit_tests:
                logger.info("第5阶段: 汇率缓存测试")
                self.exchange_tester.test_exchange_rate_cache()

            # 6. 网络搜索缓存测试
            if self.config.enable_cache_hit_tests:
                logger.info("第6阶段: 网络搜索缓存测试")
                self.websearch_tester.test_websearch_cache()

            # 7. 缓存降级测试
            if self.config.enable_degradation_tests:
                logger.info("第7阶段: 缓存降级测试")
                self.degradation_tester.test_cache_degradation()

            # 8. 熔断器测试
            if self.config.enable_circuit_breaker_tests:
                logger.info("第8阶段: 熔断器测试")
                self.circuit_breaker_tester.test_circuit_breaker()

            # 9. 性能测试
            if self.config.enable_performance_tests:
                logger.info("第9阶段: 缓存性能测试")
                self.performance_tester.test_cache_performance()

        except KeyboardInterrupt:
            logger.info("测试被用户中断")
        except Exception as e:
            logger.error(f"测试执行异常: {e}")
        finally:
            # 生成报告
            end_time = datetime.now()
            duration = (end_time - start_time).total_seconds()

            logger.info(f"测试执行完成，总耗时: {duration:.2f}秒")
            self.report_generator.generate_report()

def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="业务缓存使用测试方案")

    # 基础配置
    parser.add_argument("--base-url", default="http://127.0.0.1:8080", help="API基础URL")
    parser.add_argument("--timeout", type=int, default=30, help="请求超时时间")

    # 测试开关
    parser.add_argument("--skip-cache-hit", action="store_true", help="跳过缓存命中测试")
    parser.add_argument("--skip-cache-miss", action="store_true", help="跳过缓存未命中测试")
    parser.add_argument("--skip-degradation", action="store_true", help="跳过降级测试")
    parser.add_argument("--skip-circuit-breaker", action="store_true", help="跳过熔断器测试")
    parser.add_argument("--skip-performance", action="store_true", help="跳过性能测试")

    # 性能测试配置
    parser.add_argument("--concurrent-users", type=int, default=10, help="并发用户数")
    parser.add_argument("--test-duration", type=int, default=120, help="测试时长（秒）")

    # 输出配置
    parser.add_argument("--output-dir", default="./business_cache_test_results", help="输出目录")

    args = parser.parse_args()

    # 创建配置
    config = TestConfig(
        base_url=args.base_url,
        timeout=args.timeout,
        enable_cache_hit_tests=not args.skip_cache_hit,
        enable_cache_miss_tests=not args.skip_cache_miss,
        enable_degradation_tests=not args.skip_degradation,
        enable_circuit_breaker_tests=not args.skip_circuit_breaker,
        enable_performance_tests=not args.skip_performance,
        concurrent_users=args.concurrent_users,
        test_duration=args.test_duration,
        output_dir=args.output_dir
    )

    # 打印配置信息
    logger.info("业务缓存使用测试配置:")
    logger.info(f"  基础URL: {config.base_url}")
    logger.info(f"  缓存命中测试: {'启用' if config.enable_cache_hit_tests else '禁用'}")
    logger.info(f"  缓存未命中测试: {'启用' if config.enable_cache_miss_tests else '禁用'}")
    logger.info(f"  降级测试: {'启用' if config.enable_degradation_tests else '禁用'}")
    logger.info(f"  熔断器测试: {'启用' if config.enable_circuit_breaker_tests else '禁用'}")
    logger.info(f"  性能测试: {'启用' if config.enable_performance_tests else '禁用'}")
    logger.info(f"  并发用户数: {config.concurrent_users}")
    logger.info(f"  输出目录: {config.output_dir}")

    # 创建测试运行器并执行
    try:
        runner = BusinessCacheTestRunner(config)
        runner.run_all_tests()
    except KeyboardInterrupt:
        logger.info("测试被用户中断")
    except Exception as e:
        logger.error(f"测试执行异常: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
