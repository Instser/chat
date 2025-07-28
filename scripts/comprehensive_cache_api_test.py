#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
全面的系统缓存模块API测试方案
涵盖所有缓存管理接口的功能测试、性能测试、安全测试和稳定性测试

作者: Augment Agent
创建时间: 2025-07-28 16:03
版本: v1.0 - 全面测试方案
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
import gc
from urllib.parse import urljoin

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('comprehensive_cache_api_test.log', encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)

@dataclass
class TestConfig:
    """测试配置"""
    # 基础配置
    base_url: str = "http://127.0.0.1:8080"
    management_url: str = "http://127.0.0.1:8081"
    api_key: str = ""
    timeout: int = 30
    
    # 测试配置
    enable_functional_tests: bool = True
    enable_performance_tests: bool = True
    enable_security_tests: bool = True
    enable_stability_tests: bool = True
    
    # 性能测试配置
    concurrent_users: int = 20
    test_duration: int = 120
    requests_per_user: int = 50
    
    # 输出配置
    output_dir: str = "./comprehensive_test_results"
    detailed_logging: bool = True

@dataclass
class TestResult:
    """测试结果"""
    test_name: str
    test_type: str
    success: bool
    response_time: float
    status_code: int
    error_message: str = ""
    response_data: Any = None
    timestamp: datetime = None
    
    def __post_init__(self):
        if self.timestamp is None:
            self.timestamp = datetime.now()

class CacheAPITester:
    """缓存API测试器"""
    
    def __init__(self, config: TestConfig):
        self.config = config
        self.session = requests.Session()
        self.session.timeout = config.timeout
        self.test_results = []
        self.lock = threading.Lock()
        
        # 设置API密钥
        if config.api_key:
            self.session.headers.update({'X-API-Key': config.api_key})
        
        # 创建输出目录
        os.makedirs(config.output_dir, exist_ok=True)
        
        logger.info(f"缓存API测试器初始化完成")
        logger.info(f"基础URL: {config.base_url}")
        logger.info(f"管理URL: {config.management_url}")
    
    def record_result(self, result: TestResult):
        """记录测试结果"""
        with self.lock:
            self.test_results.append(result)
    
    def make_request(self, method: str, url: str, **kwargs) -> TestResult:
        """发送HTTP请求并记录结果"""
        start_time = time.time()
        test_name = kwargs.pop('test_name', f"{method} {url}")
        test_type = kwargs.pop('test_type', 'FUNCTIONAL')
        
        try:
            response = self.session.request(method, url, **kwargs)
            response_time = time.time() - start_time
            
            success = response.status_code < 400
            error_message = "" if success else response.text[:200]
            
            try:
                response_data = response.json() if response.content else None
            except:
                response_data = response.text[:200] if response.content else None
            
            result = TestResult(
                test_name=test_name,
                test_type=test_type,
                success=success,
                response_time=response_time,
                status_code=response.status_code,
                error_message=error_message,
                response_data=response_data
            )
            
            self.record_result(result)
            return result
            
        except Exception as e:
            response_time = time.time() - start_time
            result = TestResult(
                test_name=test_name,
                test_type=test_type,
                success=False,
                response_time=response_time,
                status_code=0,
                error_message=str(e)
            )
            
            self.record_result(result)
            return result

class FunctionalTester:
    """功能测试器"""
    
    def __init__(self, api_tester: CacheAPITester):
        self.api_tester = api_tester
        self.config = api_tester.config
    
    def test_all_endpoints(self):
        """测试所有API端点"""
        logger.info("开始功能测试...")
        
        # 1. 基础连接测试
        self.test_basic_connectivity()
        
        # 2. 统计查询接口测试
        self.test_statistics_endpoints()
        
        # 3. 缓存管理操作测试
        self.test_management_operations()
        
        # 4. 缓存操作测试
        self.test_cache_operations()
        
        # 5. 监控和健康检查测试
        self.test_monitoring_endpoints()
        
        # 6. 安全和配置测试
        self.test_security_endpoints()
        
        logger.info("功能测试完成")
    
    def test_basic_connectivity(self):
        """基础连接测试"""
        logger.info("执行基础连接测试...")
        
        # 测试控制器基础端点
        self.api_tester.make_request(
            "GET", f"{self.config.base_url}/api/admin/cache/test",
            test_name="基础连接测试",
            test_type="CONNECTIVITY"
        )
    
    def test_statistics_endpoints(self):
        """统计查询接口测试"""
        logger.info("执行统计查询接口测试...")
        
        # 全局统计
        self.api_tester.make_request(
            "GET", f"{self.config.base_url}/api/admin/cache/stats",
            test_name="全局缓存统计",
            test_type="STATISTICS"
        )
        
        # 详细统计
        self.api_tester.make_request(
            "GET", f"{self.config.base_url}/api/admin/cache/stats?detailed=true",
            test_name="详细缓存统计",
            test_type="STATISTICS"
        )
        
        # 分页统计
        self.api_tester.make_request(
            "GET", f"{self.config.base_url}/api/admin/cache/stats?page=0&size=10",
            test_name="分页缓存统计",
            test_type="STATISTICS"
        )
        
        # 缓存列表
        self.api_tester.make_request(
            "GET", f"{self.config.base_url}/api/admin/cache/list",
            test_name="缓存列表查询",
            test_type="STATISTICS"
        )
        
        # 按模块查询
        modules = ["payment", "user", "chat", "ai", "websearch"]
        for module in modules:
            self.api_tester.make_request(
                "GET", f"{self.config.base_url}/api/admin/cache/module/{module}",
                test_name=f"模块缓存查询-{module}",
                test_type="STATISTICS"
            )
        
        # 按类型查询
        cache_types = ["REDIS", "LOCAL", "HYBRID", "SPRING_CACHE"]
        for cache_type in cache_types:
            self.api_tester.make_request(
                "GET", f"{self.config.base_url}/api/admin/cache/type/{cache_type}",
                test_name=f"类型缓存查询-{cache_type}",
                test_type="STATISTICS"
            )
    
    def test_management_operations(self):
        """缓存管理操作测试"""
        logger.info("执行缓存管理操作测试...")
        
        # 缓存预热
        self.api_tester.make_request(
            "POST", f"{self.config.base_url}/api/admin/cache/warmup",
            test_name="缓存预热",
            test_type="MANAGEMENT"
        )
        
        # 按模块预热
        self.api_tester.make_request(
            "POST", f"{self.config.base_url}/api/admin/cache/warmup?module=payment",
            test_name="模块缓存预热",
            test_type="MANAGEMENT"
        )
        
        # 清空指定模块缓存
        self.api_tester.make_request(
            "POST", f"{self.config.base_url}/api/admin/cache/clear/module/test",
            test_name="清空模块缓存",
            test_type="MANAGEMENT"
        )
        
        # 清空指定类型缓存
        self.api_tester.make_request(
            "POST", f"{self.config.base_url}/api/admin/cache/clear/type/LOCAL",
            test_name="清空类型缓存",
            test_type="MANAGEMENT"
        )
        
        # 删除指定缓存项
        test_key = f"test_key_{uuid.uuid4().hex[:8]}"
        self.api_tester.make_request(
            "DELETE", f"{self.config.base_url}/api/admin/cache/evict/{test_key}",
            test_name="删除缓存项",
            test_type="MANAGEMENT"
        )
    
    def test_cache_operations(self):
        """缓存操作测试"""
        logger.info("执行缓存操作测试...")
        
        # 缓存功能测试
        test_data = {
            "key": f"test_key_{uuid.uuid4().hex[:8]}",
            "value": f"test_value_{uuid.uuid4().hex[:8]}"
        }
        
        self.api_tester.make_request(
            "POST", f"{self.config.base_url}/api/admin/cache/test",
            params=test_data,
            test_name="缓存功能测试",
            test_type="CACHE_OPERATION"
        )
        
        # 大数据量测试
        large_value = ''.join(random.choices(string.ascii_letters + string.digits, k=10240))  # 10KB
        large_data = {
            "key": f"large_test_key_{uuid.uuid4().hex[:8]}",
            "value": large_value
        }
        
        self.api_tester.make_request(
            "POST", f"{self.config.base_url}/api/admin/cache/test",
            params=large_data,
            test_name="大数据缓存测试",
            test_type="CACHE_OPERATION"
        )

    def test_monitoring_endpoints(self):
        """监控和健康检查测试"""
        logger.info("执行监控和健康检查测试...")

        # 健康检查
        self.api_tester.make_request(
            "GET", f"{self.config.base_url}/api/admin/cache/health",
            test_name="缓存健康检查",
            test_type="MONITORING"
        )

        # 性能监控
        self.api_tester.make_request(
            "GET", f"{self.config.base_url}/api/admin/cache/performance",
            test_name="缓存性能监控",
            test_type="MONITORING"
        )

        # 操作历史
        self.api_tester.make_request(
            "GET", f"{self.config.base_url}/api/admin/cache/history",
            test_name="操作历史查询",
            test_type="MONITORING"
        )

        # 限制历史记录数量
        self.api_tester.make_request(
            "GET", f"{self.config.base_url}/api/admin/cache/history?limit=5",
            test_name="限制操作历史查询",
            test_type="MONITORING"
        )

    def test_security_endpoints(self):
        """安全和配置测试"""
        logger.info("执行安全和配置测试...")

        # 配置信息
        self.api_tester.make_request(
            "GET", f"{self.config.base_url}/api/admin/cache/config",
            test_name="缓存配置查询",
            test_type="SECURITY"
        )

        # API密钥统计
        self.api_tester.make_request(
            "GET", f"{self.config.base_url}/api/admin/cache/security/api-keys",
            test_name="API密钥统计",
            test_type="SECURITY"
        )

class PerformanceTester:
    """性能测试器"""

    def __init__(self, api_tester: CacheAPITester):
        self.api_tester = api_tester
        self.config = api_tester.config

    def run_performance_tests(self):
        """运行性能测试"""
        logger.info("开始性能测试...")

        # 1. 响应时间测试
        self.test_response_times()

        # 2. 并发性能测试
        self.test_concurrent_performance()

        # 3. 大数据量性能测试
        self.test_large_data_performance()

        # 4. 持续负载测试
        self.test_sustained_load()

        logger.info("性能测试完成")

    def test_response_times(self):
        """响应时间测试"""
        logger.info("执行响应时间测试...")

        endpoints = [
            ("GET", "/api/admin/cache/stats", "统计查询"),
            ("GET", "/api/admin/cache/health", "健康检查"),
            ("GET", "/api/admin/cache/config", "配置查询"),
            ("POST", "/api/admin/cache/test", "缓存测试"),
        ]

        for method, endpoint, name in endpoints:
            for i in range(10):  # 每个端点测试10次
                if method == "POST":
                    params = {"key": f"perf_test_{i}", "value": f"value_{i}"}
                    self.api_tester.make_request(
                        method, f"{self.config.base_url}{endpoint}",
                        params=params,
                        test_name=f"{name}-响应时间测试-{i+1}",
                        test_type="PERFORMANCE"
                    )
                else:
                    self.api_tester.make_request(
                        method, f"{self.config.base_url}{endpoint}",
                        test_name=f"{name}-响应时间测试-{i+1}",
                        test_type="PERFORMANCE"
                    )

    def test_concurrent_performance(self):
        """并发性能测试"""
        logger.info("执行并发性能测试...")

        def worker_thread(worker_id: int):
            """工作线程"""
            for i in range(self.config.requests_per_user):
                # 随机选择测试端点
                endpoints = [
                    ("GET", "/api/admin/cache/stats"),
                    ("GET", "/api/admin/cache/health"),
                    ("POST", "/api/admin/cache/test"),
                ]

                method, endpoint = random.choice(endpoints)

                if method == "POST":
                    params = {
                        "key": f"concurrent_test_{worker_id}_{i}",
                        "value": f"value_{worker_id}_{i}"
                    }
                    self.api_tester.make_request(
                        method, f"{self.config.base_url}{endpoint}",
                        params=params,
                        test_name=f"并发测试-线程{worker_id}-请求{i+1}",
                        test_type="CONCURRENT"
                    )
                else:
                    self.api_tester.make_request(
                        method, f"{self.config.base_url}{endpoint}",
                        test_name=f"并发测试-线程{worker_id}-请求{i+1}",
                        test_type="CONCURRENT"
                    )

                # 随机延迟
                time.sleep(random.uniform(0.1, 0.5))

        # 创建并发线程
        with ThreadPoolExecutor(max_workers=self.config.concurrent_users) as executor:
            futures = [executor.submit(worker_thread, i) for i in range(self.config.concurrent_users)]

            # 等待所有线程完成
            for future in as_completed(futures):
                try:
                    future.result()
                except Exception as e:
                    logger.error(f"并发测试线程异常: {e}")

    def test_large_data_performance(self):
        """大数据量性能测试"""
        logger.info("执行大数据量性能测试...")

        data_sizes = [1024, 5120, 10240, 51200]  # 1KB, 5KB, 10KB, 50KB

        for size in data_sizes:
            large_value = ''.join(random.choices(string.ascii_letters + string.digits, k=size))
            params = {
                "key": f"large_data_test_{size}",
                "value": large_value
            }

            self.api_tester.make_request(
                "POST", f"{self.config.base_url}/api/admin/cache/test",
                params=params,
                test_name=f"大数据测试-{size}字节",
                test_type="LARGE_DATA"
            )

    def test_sustained_load(self):
        """持续负载测试"""
        logger.info("执行持续负载测试...")

        start_time = time.time()
        request_count = 0

        while time.time() - start_time < self.config.test_duration:
            # 轮询不同的端点
            endpoints = [
                ("GET", "/api/admin/cache/stats"),
                ("GET", "/api/admin/cache/health"),
                ("GET", "/api/admin/cache/list"),
            ]

            method, endpoint = endpoints[request_count % len(endpoints)]

            self.api_tester.make_request(
                method, f"{self.config.base_url}{endpoint}",
                test_name=f"持续负载测试-{request_count+1}",
                test_type="SUSTAINED_LOAD"
            )

            request_count += 1
            time.sleep(0.5)  # 每0.5秒一个请求

class SecurityTester:
    """安全测试器"""

    def __init__(self, api_tester: CacheAPITester):
        self.api_tester = api_tester
        self.config = api_tester.config

    def run_security_tests(self):
        """运行安全测试"""
        logger.info("开始安全测试...")

        # 1. API密钥验证测试
        self.test_api_key_validation()

        # 2. 输入验证测试
        self.test_input_validation()

        # 3. 权限控制测试
        self.test_access_control()

        logger.info("安全测试完成")

    def test_api_key_validation(self):
        """API密钥验证测试"""
        logger.info("执行API密钥验证测试...")

        # 保存原始密钥
        original_key = self.api_tester.session.headers.get('X-API-Key')

        # 测试无密钥访问
        self.api_tester.session.headers.pop('X-API-Key', None)
        self.api_tester.make_request(
            "GET", f"{self.config.base_url}/api/admin/cache/stats",
            test_name="无API密钥访问测试",
            test_type="SECURITY"
        )

        # 测试错误密钥
        self.api_tester.session.headers['X-API-Key'] = "invalid_key_12345"
        self.api_tester.make_request(
            "GET", f"{self.config.base_url}/api/admin/cache/stats",
            test_name="错误API密钥访问测试",
            test_type="SECURITY"
        )

        # 恢复原始密钥
        if original_key:
            self.api_tester.session.headers['X-API-Key'] = original_key

    def test_input_validation(self):
        """输入验证测试"""
        logger.info("执行输入验证测试...")

        # 测试SQL注入
        malicious_inputs = [
            "'; DROP TABLE users; --",
            "<script>alert('xss')</script>",
            "../../etc/passwd",
            "null",
            "",
            "a" * 10000,  # 超长输入
        ]

        for malicious_input in malicious_inputs:
            # 测试模块参数
            self.api_tester.make_request(
                "GET", f"{self.config.base_url}/api/admin/cache/module/{malicious_input}",
                test_name=f"恶意输入测试-模块-{malicious_input[:20]}",
                test_type="SECURITY"
            )

            # 测试缓存键
            self.api_tester.make_request(
                "DELETE", f"{self.config.base_url}/api/admin/cache/evict/{malicious_input}",
                test_name=f"恶意输入测试-缓存键-{malicious_input[:20]}",
                test_type="SECURITY"
            )

    def test_access_control(self):
        """权限控制测试"""
        logger.info("执行权限控制测试...")

        # 测试危险操作的权限控制
        dangerous_operations = [
            ("POST", "/api/admin/cache/clear", "清空所有缓存"),
            ("POST", "/api/admin/cache/circuit-breaker/force-open", "强制熔断"),
        ]

        for method, endpoint, name in dangerous_operations:
            self.api_tester.make_request(
                method, f"{self.config.base_url}{endpoint}",
                test_name=f"权限控制测试-{name}",
                test_type="SECURITY"
            )

class StabilityTester:
    """稳定性测试器"""

    def __init__(self, api_tester: CacheAPITester):
        self.api_tester = api_tester
        self.config = api_tester.config

    def run_stability_tests(self):
        """运行稳定性测试"""
        logger.info("开始稳定性测试...")

        # 1. 长时间运行测试
        self.test_long_running()

        # 2. 内存泄漏测试
        self.test_memory_leak()

        # 3. 错误恢复测试
        self.test_error_recovery()

        # 4. 边界条件测试
        self.test_boundary_conditions()

        logger.info("稳定性测试完成")

    def test_long_running(self):
        """长时间运行测试"""
        logger.info("执行长时间运行测试...")

        start_time = time.time()
        request_count = 0

        # 运行30分钟的稳定性测试
        test_duration = 1800  # 30分钟

        while time.time() - start_time < test_duration:
            # 执行各种操作
            operations = [
                ("GET", "/api/admin/cache/stats"),
                ("GET", "/api/admin/cache/health"),
                ("POST", "/api/admin/cache/test"),
                ("GET", "/api/admin/cache/list"),
            ]

            method, endpoint = random.choice(operations)

            if method == "POST":
                params = {
                    "key": f"stability_test_{request_count}",
                    "value": f"value_{request_count}"
                }
                self.api_tester.make_request(
                    method, f"{self.config.base_url}{endpoint}",
                    params=params,
                    test_name=f"长时间运行测试-{request_count+1}",
                    test_type="STABILITY"
                )
            else:
                self.api_tester.make_request(
                    method, f"{self.config.base_url}{endpoint}",
                    test_name=f"长时间运行测试-{request_count+1}",
                    test_type="STABILITY"
                )

            request_count += 1

            # 每100个请求休息一下
            if request_count % 100 == 0:
                logger.info(f"长时间运行测试进度: {request_count} 个请求完成")
                time.sleep(5)
            else:
                time.sleep(random.uniform(1, 3))

    def test_memory_leak(self):
        """内存泄漏测试"""
        logger.info("执行内存泄漏测试...")

        # 创建大量缓存项
        for i in range(1000):
            large_value = ''.join(random.choices(string.ascii_letters + string.digits, k=1024))
            params = {
                "key": f"memory_test_{i}",
                "value": large_value
            }

            self.api_tester.make_request(
                "POST", f"{self.config.base_url}/api/admin/cache/test",
                params=params,
                test_name=f"内存泄漏测试-{i+1}",
                test_type="MEMORY_LEAK"
            )

            if i % 100 == 0:
                # 强制垃圾回收
                gc.collect()
                logger.info(f"内存泄漏测试进度: {i+1}/1000")

    def test_error_recovery(self):
        """错误恢复测试"""
        logger.info("执行错误恢复测试...")

        # 故意触发错误，然后测试恢复
        error_scenarios = [
            ("GET", "/api/admin/cache/module/nonexistent", "不存在的模块"),
            ("GET", "/api/admin/cache/type/INVALID", "无效的缓存类型"),
            ("DELETE", "/api/admin/cache/evict/", "空缓存键"),
        ]

        for method, endpoint, description in error_scenarios:
            # 触发错误
            self.api_tester.make_request(
                method, f"{self.config.base_url}{endpoint}",
                test_name=f"错误恢复测试-{description}",
                test_type="ERROR_RECOVERY"
            )

            # 测试系统是否能正常恢复
            self.api_tester.make_request(
                "GET", f"{self.config.base_url}/api/admin/cache/health",
                test_name=f"错误恢复验证-{description}",
                test_type="ERROR_RECOVERY"
            )

    def test_boundary_conditions(self):
        """边界条件测试"""
        logger.info("执行边界条件测试...")

        # 测试各种边界条件
        boundary_tests = [
            # 分页边界
            ("GET", "/api/admin/cache/stats?page=-1&size=10", "负数页码"),
            ("GET", "/api/admin/cache/stats?page=0&size=0", "零大小分页"),
            ("GET", "/api/admin/cache/stats?page=999999&size=1", "超大页码"),
            ("GET", "/api/admin/cache/list?size=1000", "超大分页大小"),

            # 参数边界
            ("GET", "/api/admin/cache/history?limit=0", "零限制"),
            ("GET", "/api/admin/cache/history?limit=-1", "负数限制"),
            ("GET", "/api/admin/cache/history?limit=999999", "超大限制"),
        ]

        for method, url, description in boundary_tests:
            self.api_tester.make_request(
                method, f"{self.config.base_url}{url}",
                test_name=f"边界条件测试-{description}",
                test_type="BOUNDARY"
            )

class TestReportGenerator:
    """测试报告生成器"""

    def __init__(self, api_tester: CacheAPITester):
        self.api_tester = api_tester
        self.config = api_tester.config

    def generate_comprehensive_report(self):
        """生成全面的测试报告"""
        logger.info("生成测试报告...")

        results = self.api_tester.test_results

        # 按测试类型分组统计
        stats_by_type = {}
        for result in results:
            test_type = result.test_type
            if test_type not in stats_by_type:
                stats_by_type[test_type] = {
                    'total': 0,
                    'success': 0,
                    'failed': 0,
                    'response_times': [],
                    'errors': []
                }

            stats = stats_by_type[test_type]
            stats['total'] += 1
            stats['response_times'].append(result.response_time)

            if result.success:
                stats['success'] += 1
            else:
                stats['failed'] += 1
                stats['errors'].append({
                    'test_name': result.test_name,
                    'status_code': result.status_code,
                    'error_message': result.error_message
                })

        # 生成统计摘要
        summary = self.generate_summary(stats_by_type)

        # 生成详细报告
        detailed_report = self.generate_detailed_report(stats_by_type)

        # 保存报告
        self.save_reports(summary, detailed_report, results)

        # 打印摘要
        self.print_summary(summary)

    def generate_summary(self, stats_by_type: Dict) -> Dict:
        """生成测试摘要"""
        total_tests = sum(stats['total'] for stats in stats_by_type.values())
        total_success = sum(stats['success'] for stats in stats_by_type.values())
        total_failed = sum(stats['failed'] for stats in stats_by_type.values())

        all_response_times = []
        for stats in stats_by_type.values():
            all_response_times.extend(stats['response_times'])

        summary = {
            'test_overview': {
                'total_tests': total_tests,
                'total_success': total_success,
                'total_failed': total_failed,
                'success_rate': total_success / total_tests if total_tests > 0 else 0,
                'test_types': len(stats_by_type)
            },
            'performance_metrics': {
                'avg_response_time': statistics.mean(all_response_times) if all_response_times else 0,
                'median_response_time': statistics.median(all_response_times) if all_response_times else 0,
                'min_response_time': min(all_response_times) if all_response_times else 0,
                'max_response_time': max(all_response_times) if all_response_times else 0,
                'p95_response_time': statistics.quantiles(all_response_times, n=20)[18] if len(all_response_times) >= 20 else max(all_response_times) if all_response_times else 0,
                'p99_response_time': statistics.quantiles(all_response_times, n=100)[98] if len(all_response_times) >= 100 else max(all_response_times) if all_response_times else 0
            },
            'test_types_summary': {}
        }

        # 按测试类型的摘要
        for test_type, stats in stats_by_type.items():
            response_times = stats['response_times']
            summary['test_types_summary'][test_type] = {
                'total': stats['total'],
                'success': stats['success'],
                'failed': stats['failed'],
                'success_rate': stats['success'] / stats['total'] if stats['total'] > 0 else 0,
                'avg_response_time': statistics.mean(response_times) if response_times else 0,
                'error_count': len(stats['errors'])
            }

        return summary

    def generate_detailed_report(self, stats_by_type: Dict) -> Dict:
        """生成详细报告"""
        detailed_report = {
            'test_configuration': asdict(self.config),
            'test_execution_time': datetime.now().isoformat(),
            'test_results_by_type': {},
            'failed_tests': [],
            'performance_analysis': {}
        }

        # 按类型的详细结果
        for test_type, stats in stats_by_type.items():
            detailed_report['test_results_by_type'][test_type] = {
                'statistics': {
                    'total_tests': stats['total'],
                    'successful_tests': stats['success'],
                    'failed_tests': stats['failed'],
                    'success_rate': stats['success'] / stats['total'] if stats['total'] > 0 else 0
                },
                'performance': {
                    'avg_response_time': statistics.mean(stats['response_times']) if stats['response_times'] else 0,
                    'median_response_time': statistics.median(stats['response_times']) if stats['response_times'] else 0,
                    'min_response_time': min(stats['response_times']) if stats['response_times'] else 0,
                    'max_response_time': max(stats['response_times']) if stats['response_times'] else 0
                },
                'errors': stats['errors']
            }

        # 收集所有失败的测试
        for stats in stats_by_type.values():
            detailed_report['failed_tests'].extend(stats['errors'])

        return detailed_report

    def save_reports(self, summary: Dict, detailed_report: Dict, results: List[TestResult]):
        """保存报告文件"""
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')

        # 保存摘要报告
        summary_file = os.path.join(self.config.output_dir, f"test_summary_{timestamp}.json")
        with open(summary_file, 'w', encoding='utf-8') as f:
            json.dump(summary, f, ensure_ascii=False, indent=2, default=str)

        # 保存详细报告
        detailed_file = os.path.join(self.config.output_dir, f"test_detailed_{timestamp}.json")
        with open(detailed_file, 'w', encoding='utf-8') as f:
            json.dump(detailed_report, f, ensure_ascii=False, indent=2, default=str)

        # 保存原始结果
        raw_results = [asdict(result) for result in results]
        raw_file = os.path.join(self.config.output_dir, f"test_raw_results_{timestamp}.json")
        with open(raw_file, 'w', encoding='utf-8') as f:
            json.dump(raw_results, f, ensure_ascii=False, indent=2, default=str)

        logger.info(f"测试报告已保存:")
        logger.info(f"  摘要报告: {summary_file}")
        logger.info(f"  详细报告: {detailed_file}")
        logger.info(f"  原始结果: {raw_file}")

    def print_summary(self, summary: Dict):
        """打印测试摘要"""
        logger.info("=" * 80)
        logger.info("全面缓存API测试完成摘要")
        logger.info("=" * 80)

        overview = summary['test_overview']
        logger.info(f"总测试数: {overview['total_tests']}")
        logger.info(f"成功测试: {overview['total_success']}")
        logger.info(f"失败测试: {overview['total_failed']}")
        logger.info(f"成功率: {overview['success_rate']:.2%}")
        logger.info(f"测试类型: {overview['test_types']}")

        performance = summary['performance_metrics']
        logger.info(f"\n性能指标:")
        logger.info(f"  平均响应时间: {performance['avg_response_time']:.3f}s")
        logger.info(f"  中位数响应时间: {performance['median_response_time']:.3f}s")
        logger.info(f"  95%响应时间: {performance['p95_response_time']:.3f}s")
        logger.info(f"  99%响应时间: {performance['p99_response_time']:.3f}s")

        logger.info(f"\n按测试类型统计:")
        for test_type, stats in summary['test_types_summary'].items():
            logger.info(f"  {test_type}:")
            logger.info(f"    总数: {stats['total']}, 成功: {stats['success']}, 失败: {stats['failed']}")
            logger.info(f"    成功率: {stats['success_rate']:.2%}, 平均响应时间: {stats['avg_response_time']:.3f}s")

        logger.info("=" * 80)

class ComprehensiveCacheAPITestRunner:
    """全面缓存API测试运行器"""

    def __init__(self, config: TestConfig):
        self.config = config
        self.api_tester = CacheAPITester(config)

        # 初始化各种测试器
        self.functional_tester = FunctionalTester(self.api_tester)
        self.performance_tester = PerformanceTester(self.api_tester)
        self.security_tester = SecurityTester(self.api_tester)
        self.stability_tester = StabilityTester(self.api_tester)
        self.report_generator = TestReportGenerator(self.api_tester)

    def run_all_tests(self):
        """运行所有测试"""
        logger.info("开始全面缓存API测试")
        logger.info("=" * 80)

        start_time = datetime.now()

        try:
            # 1. 功能测试
            if self.config.enable_functional_tests:
                logger.info("第1阶段: 功能测试")
                self.functional_tester.test_all_endpoints()

            # 2. 性能测试
            if self.config.enable_performance_tests:
                logger.info("第2阶段: 性能测试")
                self.performance_tester.run_performance_tests()

            # 3. 安全测试
            if self.config.enable_security_tests:
                logger.info("第3阶段: 安全测试")
                self.security_tester.run_security_tests()

            # 4. 稳定性测试
            if self.config.enable_stability_tests:
                logger.info("第4阶段: 稳定性测试")
                self.stability_tester.run_stability_tests()

        except KeyboardInterrupt:
            logger.info("测试被用户中断")
        except Exception as e:
            logger.error(f"测试执行异常: {e}")
        finally:
            # 生成报告
            end_time = datetime.now()
            duration = (end_time - start_time).total_seconds()

            logger.info(f"测试执行完成，总耗时: {duration:.2f}秒")
            self.report_generator.generate_comprehensive_report()

def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="全面的系统缓存模块API测试方案")

    # 基础配置
    parser.add_argument("--base-url", default="http://127.0.0.1:8080", help="API基础URL")
    parser.add_argument("--management-url", default="http://127.0.0.1:8081", help="管理接口URL")
    parser.add_argument("--api-key", required=True, help="API密钥")
    parser.add_argument("--timeout", type=int, default=30, help="请求超时时间")

    # 测试开关
    parser.add_argument("--skip-functional", action="store_true", help="跳过功能测试")
    parser.add_argument("--skip-performance", action="store_true", help="跳过性能测试")
    parser.add_argument("--skip-security", action="store_true", help="跳过安全测试")
    parser.add_argument("--skip-stability", action="store_true", help="跳过稳定性测试")

    # 性能测试配置
    parser.add_argument("--concurrent-users", type=int, default=20, help="并发用户数")
    parser.add_argument("--test-duration", type=int, default=120, help="测试时长（秒）")
    parser.add_argument("--requests-per-user", type=int, default=50, help="每用户请求数")

    # 输出配置
    parser.add_argument("--output-dir", default="./comprehensive_test_results", help="输出目录")
    parser.add_argument("--detailed-logging", action="store_true", help="详细日志")

    args = parser.parse_args()

    # 创建配置
    config = TestConfig(
        base_url=args.base_url,
        management_url=args.management_url,
        api_key=args.api_key,
        timeout=args.timeout,
        enable_functional_tests=not args.skip_functional,
        enable_performance_tests=not args.skip_performance,
        enable_security_tests=not args.skip_security,
        enable_stability_tests=not args.skip_stability,
        concurrent_users=args.concurrent_users,
        test_duration=args.test_duration,
        requests_per_user=args.requests_per_user,
        output_dir=args.output_dir,
        detailed_logging=args.detailed_logging
    )

    # 打印配置信息
    logger.info("全面缓存API测试配置:")
    logger.info(f"  基础URL: {config.base_url}")
    logger.info(f"  管理URL: {config.management_url}")
    logger.info(f"  功能测试: {'启用' if config.enable_functional_tests else '禁用'}")
    logger.info(f"  性能测试: {'启用' if config.enable_performance_tests else '禁用'}")
    logger.info(f"  安全测试: {'启用' if config.enable_security_tests else '禁用'}")
    logger.info(f"  稳定性测试: {'启用' if config.enable_stability_tests else '禁用'}")
    logger.info(f"  并发用户数: {config.concurrent_users}")
    logger.info(f"  测试时长: {config.test_duration}秒")
    logger.info(f"  输出目录: {config.output_dir}")

    # 创建测试运行器并执行
    try:
        runner = ComprehensiveCacheAPITestRunner(config)
        runner.run_all_tests()
    except KeyboardInterrupt:
        logger.info("测试被用户中断")
    except Exception as e:
        logger.error(f"测试执行异常: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
