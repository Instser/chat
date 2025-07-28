#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
缓存系统全面压力测试脚本
针对聊天系统的缓存功能进行全面压力测试，支持关闭权限检测

作者: Augment Agent
创建时间: 2025-07-28 15:37
版本: v1.0
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
try:
    import psutil
    PSUTIL_AVAILABLE = True
except ImportError:
    PSUTIL_AVAILABLE = False
from urllib.parse import urljoin

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('cache_stress_test.log', encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)

@dataclass
class StressTestConfig:
    """压力测试配置"""
    # 基础配置
    base_url: str = "http://127.0.0.1:8080"
    management_url: str = "http://127.0.0.1:8081"
    api_prefix: str = "/api"
    cache_prefix: str = "/api/admin/cache"
    timeout: int = 30
    
    # 压力测试配置
    concurrent_users: int = 50
    test_duration: int = 300  # 5分钟
    requests_per_user: int = 100
    ramp_up_time: int = 30  # 30秒内逐步增加负载
    
    # 缓存测试配置
    cache_key_prefix: str = "stress_test"
    cache_value_size: int = 1024  # 1KB
    cache_ttl: int = 300  # 5分钟
    
    # 权限配置
    disable_auth: bool = False
    api_key: str = ""
    
    # 输出配置
    output_dir: str = "./stress_test_results"
    detailed_logging: bool = True

@dataclass
class TestMetrics:
    """测试指标"""
    total_requests: int = 0
    successful_requests: int = 0
    failed_requests: int = 0
    response_times: List[float] = None
    error_types: Dict[str, int] = None
    throughput: float = 0.0
    start_time: datetime = None
    end_time: datetime = None
    
    def __post_init__(self):
        if self.response_times is None:
            self.response_times = []
        if self.error_types is None:
            self.error_types = {}

class SystemMonitor:
    """系统资源监控器"""
    
    def __init__(self):
        self.monitoring = False
        self.metrics = []
        self.monitor_thread = None
    
    def start_monitoring(self):
        """开始监控系统资源"""
        self.monitoring = True
        self.monitor_thread = threading.Thread(target=self._monitor_loop)
        self.monitor_thread.daemon = True
        self.monitor_thread.start()
        logger.info("系统资源监控已启动")
    
    def stop_monitoring(self):
        """停止监控"""
        self.monitoring = False
        if self.monitor_thread:
            self.monitor_thread.join(timeout=5)
        logger.info("系统资源监控已停止")
    
    def _monitor_loop(self):
        """监控循环"""
        if not PSUTIL_AVAILABLE:
            logger.warning("psutil不可用，系统监控功能已禁用")
            return

        while self.monitoring:
            try:
                cpu_percent = psutil.cpu_percent(interval=1)
                memory = psutil.virtual_memory()
                disk = psutil.disk_usage('/')

                metric = {
                    'timestamp': datetime.now(),
                    'cpu_percent': cpu_percent,
                    'memory_percent': memory.percent,
                    'memory_used_gb': memory.used / (1024**3),
                    'memory_available_gb': memory.available / (1024**3),
                    'disk_percent': disk.percent,
                    'disk_used_gb': disk.used / (1024**3),
                    'disk_free_gb': disk.free / (1024**3)
                }

                self.metrics.append(metric)

                # 保留最近1000个监控点
                if len(self.metrics) > 1000:
                    self.metrics = self.metrics[-1000:]

            except Exception as e:
                logger.error(f"系统监控异常: {e}")

            time.sleep(1)
    
    def get_summary(self) -> Dict:
        """获取监控摘要"""
        if not self.metrics:
            return {}
        
        cpu_values = [m['cpu_percent'] for m in self.metrics]
        memory_values = [m['memory_percent'] for m in self.metrics]
        
        return {
            'cpu_avg': statistics.mean(cpu_values),
            'cpu_max': max(cpu_values),
            'cpu_min': min(cpu_values),
            'memory_avg': statistics.mean(memory_values),
            'memory_max': max(memory_values),
            'memory_min': min(memory_values),
            'sample_count': len(self.metrics)
        }

class CacheStressTester:
    """缓存压力测试器"""
    
    def __init__(self, config: StressTestConfig):
        self.config = config
        self.session = requests.Session()
        self.session.timeout = config.timeout
        self.metrics = TestMetrics()
        self.system_monitor = SystemMonitor()
        self.test_data = {}
        
        # 创建输出目录
        os.makedirs(config.output_dir, exist_ok=True)
        
        # 设置请求头
        if config.api_key:
            self.session.headers.update({'X-API-Key': config.api_key})
        
        logger.info(f"缓存压力测试器初始化完成")
        logger.info(f"基础URL: {config.base_url}")
        logger.info(f"管理URL: {config.management_url}")
        logger.info(f"并发用户数: {config.concurrent_users}")
        logger.info(f"测试时长: {config.test_duration}秒")
    
    def generate_test_data(self, size: int) -> str:
        """生成指定大小的测试数据"""
        return ''.join(random.choices(string.ascii_letters + string.digits, k=size))
    
    def get_cache_url(self, endpoint: str) -> str:
        """获取缓存管理URL"""
        return urljoin(self.config.management_url, self.config.cache_prefix + endpoint)
    
    def get_api_url(self, endpoint: str) -> str:
        """获取API URL"""
        return urljoin(self.config.base_url, self.config.api_prefix + endpoint)
    
    def make_request(self, method: str, url: str, **kwargs) -> Tuple[requests.Response, float]:
        """发送HTTP请求并记录响应时间"""
        start_time = time.time()
        try:
            response = self.session.request(method, url, **kwargs)
            end_time = time.time()
            response_time = end_time - start_time
            return response, response_time
        except Exception as e:
            end_time = time.time()
            response_time = end_time - start_time
            logger.error(f"请求异常: {method} {url} - {e}")
            raise
    
    def record_result(self, success: bool, response_time: float, error_type: str = None):
        """记录测试结果"""
        self.metrics.total_requests += 1
        self.metrics.response_times.append(response_time)
        
        if success:
            self.metrics.successful_requests += 1
        else:
            self.metrics.failed_requests += 1
            if error_type:
                self.metrics.error_types[error_type] = self.metrics.error_types.get(error_type, 0) + 1

class CacheOperationTester:
    """缓存操作测试器"""
    
    def __init__(self, stress_tester: CacheStressTester):
        self.stress_tester = stress_tester
        self.config = stress_tester.config
    
    def test_cache_stats(self) -> bool:
        """测试缓存统计接口"""
        try:
            url = self.stress_tester.get_cache_url("/stats")
            response, response_time = self.stress_tester.make_request("GET", url)
            
            success = response.status_code == 200
            error_type = None if success else f"HTTP_{response.status_code}"
            
            self.stress_tester.record_result(success, response_time, error_type)
            return success
            
        except Exception as e:
            self.stress_tester.record_result(False, 0, "EXCEPTION")
            return False
    
    def test_cache_health(self) -> bool:
        """测试缓存健康检查"""
        try:
            url = self.stress_tester.get_cache_url("/health")
            response, response_time = self.stress_tester.make_request("GET", url)
            
            success = response.status_code == 200
            error_type = None if success else f"HTTP_{response.status_code}"
            
            self.stress_tester.record_result(success, response_time, error_type)
            return success
            
        except Exception as e:
            self.stress_tester.record_result(False, 0, "EXCEPTION")
            return False
    
    def test_cache_operations(self) -> bool:
        """测试缓存基本操作"""
        try:
            # 生成测试键值
            test_key = f"{self.config.cache_key_prefix}_{uuid.uuid4().hex[:8]}"
            test_value = self.stress_tester.generate_test_data(self.config.cache_value_size)
            
            # 测试缓存设置（通过测试接口）
            url = self.stress_tester.get_cache_url("/test")
            params = {"key": test_key, "value": test_value}
            response, response_time = self.stress_tester.make_request("POST", url, params=params)
            
            success = response.status_code == 200
            error_type = None if success else f"HTTP_{response.status_code}"
            
            self.stress_tester.record_result(success, response_time, error_type)
            return success
            
        except Exception as e:
            self.stress_tester.record_result(False, 0, "EXCEPTION")
            return False

    def test_cache_eviction(self) -> bool:
        """测试缓存删除操作"""
        try:
            test_key = f"{self.config.cache_key_prefix}_evict_{uuid.uuid4().hex[:8]}"
            url = self.stress_tester.get_cache_url(f"/evict/{test_key}")
            response, response_time = self.stress_tester.make_request("DELETE", url)

            success = response.status_code == 200
            error_type = None if success else f"HTTP_{response.status_code}"

            self.stress_tester.record_result(success, response_time, error_type)
            return success

        except Exception as e:
            self.stress_tester.record_result(False, 0, "EXCEPTION")
            return False

    def test_cache_clear(self) -> bool:
        """测试缓存清空操作"""
        try:
            url = self.stress_tester.get_cache_url("/clear")
            response, response_time = self.stress_tester.make_request("POST", url)

            success = response.status_code == 200
            error_type = None if success else f"HTTP_{response.status_code}"

            self.stress_tester.record_result(success, response_time, error_type)
            return success

        except Exception as e:
            self.stress_tester.record_result(False, 0, "EXCEPTION")
            return False

class ChatSystemTester:
    """聊天系统缓存测试器"""

    def __init__(self, stress_tester: CacheStressTester):
        self.stress_tester = stress_tester
        self.config = stress_tester.config
        self.test_users = []
        self.test_sessions = []

    def setup_test_users(self, count: int = 10):
        """设置测试用户"""
        for i in range(count):
            user_data = {
                'username': f'test_user_{i}_{uuid.uuid4().hex[:8]}',
                'email': f'test_{i}@example.com',
                'password': 'test_password_123'
            }
            self.test_users.append(user_data)
        logger.info(f"创建了 {count} 个测试用户")

    def test_user_registration(self) -> bool:
        """测试用户注册（会触发缓存操作）"""
        try:
            if not self.test_users:
                self.setup_test_users(1)

            user_data = random.choice(self.test_users)
            url = self.stress_tester.get_api_url("/auth/register")

            # 添加随机后缀避免重复
            register_data = {
                'username': f"{user_data['username']}_{random.randint(1000, 9999)}",
                'email': f"test_{random.randint(1000, 9999)}@example.com",
                'password': user_data['password']
            }

            response, response_time = self.stress_tester.make_request(
                "POST", url, json=register_data
            )

            success = response.status_code in [200, 201]
            error_type = None if success else f"HTTP_{response.status_code}"

            self.stress_tester.record_result(success, response_time, error_type)
            return success

        except Exception as e:
            self.stress_tester.record_result(False, 0, "EXCEPTION")
            return False

    def test_user_login(self) -> bool:
        """测试用户登录（会触发缓存操作）"""
        try:
            if not self.test_users:
                self.setup_test_users(1)

            user_data = random.choice(self.test_users)
            url = self.stress_tester.get_api_url("/auth/login")

            login_data = {
                'username': user_data['username'],
                'password': user_data['password']
            }

            response, response_time = self.stress_tester.make_request(
                "POST", url, json=login_data
            )

            success = response.status_code == 200
            error_type = None if success else f"HTTP_{response.status_code}"

            self.stress_tester.record_result(success, response_time, error_type)
            return success

        except Exception as e:
            self.stress_tester.record_result(False, 0, "EXCEPTION")
            return False

    def test_chat_session_creation(self) -> bool:
        """测试聊天会话创建（会触发缓存操作）"""
        try:
            url = self.stress_tester.get_api_url("/chat/sessions")

            session_data = {
                'title': f'测试会话_{uuid.uuid4().hex[:8]}',
                'model': 'gpt-3.5-turbo'
            }

            response, response_time = self.stress_tester.make_request(
                "POST", url, json=session_data
            )

            success = response.status_code in [200, 201]
            error_type = None if success else f"HTTP_{response.status_code}"

            if success and response.json().get('data'):
                session_id = response.json()['data'].get('id')
                if session_id:
                    self.test_sessions.append(session_id)

            self.stress_tester.record_result(success, response_time, error_type)
            return success

        except Exception as e:
            self.stress_tester.record_result(False, 0, "EXCEPTION")
            return False

    def test_chat_message_send(self) -> bool:
        """测试发送聊天消息（会触发缓存操作）"""
        try:
            if not self.test_sessions:
                # 如果没有会话，先创建一个
                if not self.test_chat_session_creation():
                    return False

            session_id = random.choice(self.test_sessions) if self.test_sessions else "test_session"
            url = self.stress_tester.get_api_url("/chat")

            message_data = {
                'sessionId': session_id,
                'message': f'这是一条测试消息 {uuid.uuid4().hex[:8]}',
                'model': 'gpt-3.5-turbo'
            }

            response, response_time = self.stress_tester.make_request(
                "POST", url, json=message_data
            )

            success = response.status_code == 200
            error_type = None if success else f"HTTP_{response.status_code}"

            self.stress_tester.record_result(success, response_time, error_type)
            return success

        except Exception as e:
            self.stress_tester.record_result(False, 0, "EXCEPTION")
            return False

class StressTestRunner:
    """压力测试运行器"""

    def __init__(self, config: StressTestConfig):
        self.config = config
        self.stress_tester = CacheStressTester(config)
        self.cache_tester = CacheOperationTester(self.stress_tester)
        self.chat_tester = ChatSystemTester(self.stress_tester)
        self.test_scenarios = []

        # 定义测试场景
        self.setup_test_scenarios()

    def setup_test_scenarios(self):
        """设置测试场景"""
        # 缓存管理接口测试场景
        self.test_scenarios.extend([
            ('cache_stats', self.cache_tester.test_cache_stats, 0.3),
            ('cache_health', self.cache_tester.test_cache_health, 0.2),
            ('cache_operations', self.cache_tester.test_cache_operations, 0.2),
            ('cache_eviction', self.cache_tester.test_cache_eviction, 0.1),
            ('cache_clear', self.cache_tester.test_cache_clear, 0.05),
        ])

        # 聊天系统缓存测试场景（如果不禁用权限）
        if not self.config.disable_auth:
            self.test_scenarios.extend([
                ('user_registration', self.chat_tester.test_user_registration, 0.05),
                ('user_login', self.chat_tester.test_user_login, 0.05),
                ('chat_session_creation', self.chat_tester.test_chat_session_creation, 0.05),
                ('chat_message_send', self.chat_tester.test_chat_message_send, 0.05),
            ])

        logger.info(f"设置了 {len(self.test_scenarios)} 个测试场景")

    def select_test_scenario(self) -> callable:
        """根据权重选择测试场景"""
        scenarios = [(func, weight) for name, func, weight in self.test_scenarios]
        functions, weights = zip(*scenarios)
        return random.choices(functions, weights=weights, k=1)[0]

    def worker_thread(self, worker_id: int, start_delay: float):
        """工作线程函数"""
        # 等待启动延迟（实现渐进式加载）
        time.sleep(start_delay)

        logger.info(f"工作线程 {worker_id} 开始执行")

        requests_count = 0
        start_time = time.time()

        while requests_count < self.config.requests_per_user:
            if time.time() - start_time > self.config.test_duration:
                break

            # 选择测试场景并执行
            test_func = self.select_test_scenario()
            try:
                test_func()
                requests_count += 1

                # 随机延迟，模拟真实用户行为
                time.sleep(random.uniform(0.1, 1.0))

            except Exception as e:
                logger.error(f"工作线程 {worker_id} 执行异常: {e}")
                self.stress_tester.record_result(False, 0, "THREAD_EXCEPTION")

        logger.info(f"工作线程 {worker_id} 完成，执行了 {requests_count} 个请求")

    def run_stress_test(self):
        """运行压力测试"""
        logger.info("开始缓存系统压力测试")
        logger.info("=" * 60)

        # 启动系统监控
        self.stress_tester.system_monitor.start_monitoring()

        # 记录开始时间
        self.stress_tester.metrics.start_time = datetime.now()

        try:
            # 创建线程池
            with ThreadPoolExecutor(max_workers=self.config.concurrent_users) as executor:
                # 计算每个线程的启动延迟（实现渐进式加载）
                delay_step = self.config.ramp_up_time / self.config.concurrent_users

                # 提交所有工作线程
                futures = []
                for i in range(self.config.concurrent_users):
                    start_delay = i * delay_step
                    future = executor.submit(self.worker_thread, i, start_delay)
                    futures.append(future)

                # 等待所有线程完成
                for future in as_completed(futures):
                    try:
                        future.result()
                    except Exception as e:
                        logger.error(f"线程执行异常: {e}")

        finally:
            # 记录结束时间
            self.stress_tester.metrics.end_time = datetime.now()

            # 停止系统监控
            self.stress_tester.system_monitor.stop_monitoring()

            # 生成报告
            self.generate_report()

    def calculate_statistics(self) -> Dict:
        """计算统计数据"""
        metrics = self.stress_tester.metrics
        response_times = metrics.response_times

        if not response_times:
            return {}

        # 计算吞吐量
        if metrics.start_time and metrics.end_time:
            duration = (metrics.end_time - metrics.start_time).total_seconds()
            throughput = metrics.total_requests / duration if duration > 0 else 0
        else:
            throughput = 0

        # 计算响应时间统计
        stats = {
            'total_requests': metrics.total_requests,
            'successful_requests': metrics.successful_requests,
            'failed_requests': metrics.failed_requests,
            'success_rate': metrics.successful_requests / metrics.total_requests if metrics.total_requests > 0 else 0,
            'throughput_rps': throughput,
            'avg_response_time': statistics.mean(response_times),
            'median_response_time': statistics.median(response_times),
            'min_response_time': min(response_times),
            'max_response_time': max(response_times),
            'p95_response_time': statistics.quantiles(response_times, n=20)[18] if len(response_times) >= 20 else max(response_times),
            'p99_response_time': statistics.quantiles(response_times, n=100)[98] if len(response_times) >= 100 else max(response_times),
            'error_types': metrics.error_types,
            'test_duration': duration if metrics.start_time and metrics.end_time else 0
        }

        return stats

    def generate_report(self):
        """生成测试报告"""
        logger.info("生成压力测试报告...")

        # 计算统计数据
        stats = self.calculate_statistics()
        system_stats = self.stress_tester.system_monitor.get_summary()

        # 创建报告数据
        report_data = {
            'test_config': asdict(self.config),
            'test_results': stats,
            'system_metrics': system_stats,
            'test_scenarios': [{'name': name, 'weight': weight} for name, func, weight in self.test_scenarios],
            'timestamp': datetime.now().isoformat()
        }

        # 保存JSON报告
        json_file = os.path.join(self.config.output_dir, f"stress_test_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json")
        with open(json_file, 'w', encoding='utf-8') as f:
            json.dump(report_data, f, ensure_ascii=False, indent=2, default=str)

        # 打印摘要报告
        self.print_summary_report(stats, system_stats)

        logger.info(f"详细报告已保存到: {json_file}")

    def print_summary_report(self, stats: Dict, system_stats: Dict):
        """打印摘要报告"""
        logger.info("=" * 60)
        logger.info("压力测试完成摘要")
        logger.info("=" * 60)

        if stats:
            logger.info(f"总请求数: {stats['total_requests']}")
            logger.info(f"成功请求: {stats['successful_requests']}")
            logger.info(f"失败请求: {stats['failed_requests']}")
            logger.info(f"成功率: {stats['success_rate']:.2%}")
            logger.info(f"吞吐量: {stats['throughput_rps']:.2f} RPS")
            logger.info(f"平均响应时间: {stats['avg_response_time']:.3f}s")
            logger.info(f"中位数响应时间: {stats['median_response_time']:.3f}s")
            logger.info(f"95%响应时间: {stats['p95_response_time']:.3f}s")
            logger.info(f"99%响应时间: {stats['p99_response_time']:.3f}s")
            logger.info(f"测试时长: {stats['test_duration']:.2f}s")

            if stats['error_types']:
                logger.info("错误类型分布:")
                for error_type, count in stats['error_types'].items():
                    logger.info(f"  {error_type}: {count}")

        if system_stats:
            logger.info("\n系统资源使用:")
            logger.info(f"平均CPU使用率: {system_stats.get('cpu_avg', 0):.1f}%")
            logger.info(f"最大CPU使用率: {system_stats.get('cpu_max', 0):.1f}%")
            logger.info(f"平均内存使用率: {system_stats.get('memory_avg', 0):.1f}%")
            logger.info(f"最大内存使用率: {system_stats.get('memory_max', 0):.1f}%")

        logger.info("=" * 60)

def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="缓存系统压力测试脚本")

    # 基础配置
    parser.add_argument("--base-url", default="http://127.0.0.1:8080", help="API基础URL")
    parser.add_argument("--management-url", default="http://127.0.0.1:8081", help="管理接口URL")
    parser.add_argument("--api-key", default="", help="API密钥")
    parser.add_argument("--disable-auth", action="store_true", help="禁用权限检测")

    # 压力测试配置
    parser.add_argument("--concurrent-users", type=int, default=50, help="并发用户数")
    parser.add_argument("--test-duration", type=int, default=300, help="测试时长（秒）")
    parser.add_argument("--requests-per-user", type=int, default=100, help="每用户请求数")
    parser.add_argument("--ramp-up-time", type=int, default=30, help="渐进加载时间（秒）")

    # 缓存配置
    parser.add_argument("--cache-value-size", type=int, default=1024, help="缓存值大小（字节）")
    parser.add_argument("--cache-ttl", type=int, default=300, help="缓存TTL（秒）")

    # 输出配置
    parser.add_argument("--output-dir", default="./stress_test_results", help="输出目录")
    parser.add_argument("--detailed-logging", action="store_true", help="详细日志")
    parser.add_argument("--timeout", type=int, default=30, help="请求超时时间")

    args = parser.parse_args()

    # 创建配置
    config = StressTestConfig(
        base_url=args.base_url,
        management_url=args.management_url,
        api_key=args.api_key,
        disable_auth=args.disable_auth,
        concurrent_users=args.concurrent_users,
        test_duration=args.test_duration,
        requests_per_user=args.requests_per_user,
        ramp_up_time=args.ramp_up_time,
        cache_value_size=args.cache_value_size,
        cache_ttl=args.cache_ttl,
        output_dir=args.output_dir,
        detailed_logging=args.detailed_logging,
        timeout=args.timeout
    )

    # 打印配置信息
    logger.info("缓存系统压力测试配置:")
    logger.info(f"  基础URL: {config.base_url}")
    logger.info(f"  管理URL: {config.management_url}")
    logger.info(f"  并发用户数: {config.concurrent_users}")
    logger.info(f"  测试时长: {config.test_duration}秒")
    logger.info(f"  每用户请求数: {config.requests_per_user}")
    logger.info(f"  渐进加载时间: {config.ramp_up_time}秒")
    logger.info(f"  缓存值大小: {config.cache_value_size}字节")
    logger.info(f"  禁用权限检测: {config.disable_auth}")
    logger.info(f"  输出目录: {config.output_dir}")

    # 检查依赖
    if PSUTIL_AVAILABLE:
        logger.info("系统监控功能已启用")
    else:
        logger.warning("psutil未安装，系统监控功能将被禁用")
        logger.warning("安装命令: pip install psutil")

    # 创建测试运行器并执行
    try:
        runner = StressTestRunner(config)
        runner.run_stress_test()
    except KeyboardInterrupt:
        logger.info("测试被用户中断")
    except Exception as e:
        logger.error(f"测试执行异常: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
