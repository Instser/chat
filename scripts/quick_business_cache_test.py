#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
快速业务缓存验证测试
专门用于快速验证系统中业务功能的缓存使用情况

作者: Augment Agent
创建时间: 2025-07-28 16:17
版本: v1.0 - 快速验证版本
"""

import requests
import time
import json
import logging
from datetime import datetime

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class QuickBusinessCacheTest:
    """快速业务缓存测试"""
    
    def __init__(self, base_url="http://127.0.0.1:8080"):
        self.base_url = base_url
        self.session = requests.Session()
        self.session.timeout = 10
        self.results = []
    
    def test_request(self, method, endpoint, name, expected_cache=True, **kwargs):
        """执行测试请求"""
        url = f"{self.base_url}{endpoint}"
        start_time = time.time()
        
        try:
            response = self.session.request(method, url, **kwargs)
            response_time = time.time() - start_time
            
            # 根据响应时间判断是否命中缓存
            cache_hit = response_time < 0.05 if expected_cache else False
            
            result = {
                'name': name,
                'endpoint': endpoint,
                'success': response.status_code < 400,
                'status_code': response.status_code,
                'response_time': response_time,
                'cache_hit': cache_hit,
                'timestamp': datetime.now().isoformat()
            }
            
            self.results.append(result)
            
            status = "✅" if result['success'] else "❌"
            cache_status = "🎯" if cache_hit else "🔍"
            
            logger.info(f"{status} {cache_status} {name}: {response_time:.3f}s (状态码: {response.status_code})")
            
            return result
            
        except Exception as e:
            result = {
                'name': name,
                'endpoint': endpoint,
                'success': False,
                'error': str(e),
                'response_time': time.time() - start_time,
                'cache_hit': False,
                'timestamp': datetime.now().isoformat()
            }
            
            self.results.append(result)
            logger.error(f"❌ {name}: 请求失败 - {str(e)}")
            return result
    
    def run_quick_tests(self):
        """运行快速测试"""
        logger.info("🚀 开始快速业务缓存验证测试")
        logger.info("=" * 60)
        
        # 1. 用户认证相关测试
        logger.info("📱 测试用户认证缓存...")
        
        # 测试登录（可能需要有效凭据）
        login_data = {"usernameOrEmail": "test@example.com", "password": "test123456"}
        self.test_request("POST", "/api/auth/login", "用户登录", False, json=login_data)
        
        # 测试获取用户信息
        self.test_request("GET", "/api/auth/me", "获取用户信息", True)
        
        # 2. 聊天相关测试
        logger.info("💬 测试聊天缓存...")
        
        # 测试获取会话列表
        self.test_request("GET", "/api/chat/sessions", "获取聊天会话", True)
        
        # 测试发送消息
        chat_data = {
            "sessionId": "test_session",
            "message": "测试消息",
            "model": "gpt-3.5-turbo"
        }
        self.test_request("POST", "/api/chat", "发送聊天消息", False, json=chat_data)
        
        # 3. 支付相关测试
        logger.info("💰 测试支付缓存...")
        
        # 测试创建支付
        payment_data = {
            "packageId": 1,
            "paymentMethod": "alipay",
            "amount": 10.00
        }
        self.test_request("POST", "/api/payment/create", "创建支付订单", False, json=payment_data)
        
        # 测试查询支付状态
        self.test_request("GET", "/api/payment/status/test_order", "查询支付状态", True)
        
        # 4. OAuth2相关测试
        logger.info("🔐 测试OAuth2缓存...")
        
        # 测试OAuth2登录
        oauth_data = {"code": "test_code", "state": "test_state"}
        self.test_request("POST", "/api/oauth2/login/google", "OAuth2登录", False, json=oauth_data)
        
        # 5. 其他业务模块测试
        logger.info("🌐 测试其他业务缓存...")
        
        # 测试汇率查询
        self.test_request("GET", "/api/exchange/rates", "汇率查询", True)
        
        # 测试网络搜索
        search_data = {"query": "test", "limit": 5}
        self.test_request("POST", "/api/websearch/search", "网络搜索", False, json=search_data)
        
        # 生成报告
        self.generate_quick_report()
    
    def test_cache_performance(self):
        """测试缓存性能对比"""
        logger.info("⚡ 测试缓存性能对比...")
        
        # 测试同一接口多次访问，观察缓存效果
        endpoint = "/api/auth/me"
        
        logger.info("首次访问（建立缓存）:")
        first_result = self.test_request("GET", endpoint, "首次访问", False)
        
        time.sleep(0.5)
        
        logger.info("后续访问（应该命中缓存）:")
        for i in range(3):
            self.test_request("GET", endpoint, f"缓存访问-{i+1}", True)
            time.sleep(0.2)
        
        # 分析性能差异
        auth_results = [r for r in self.results if r['endpoint'] == endpoint]
        if len(auth_results) >= 2:
            first_time = auth_results[0]['response_time']
            cached_times = [r['response_time'] for r in auth_results[1:]]
            avg_cached_time = sum(cached_times) / len(cached_times)
            
            improvement = ((first_time - avg_cached_time) / first_time) * 100
            logger.info(f"📊 性能提升: {improvement:.1f}% (首次: {first_time:.3f}s, 缓存: {avg_cached_time:.3f}s)")
    
    def test_cache_degradation(self):
        """测试缓存降级（简单版本）"""
        logger.info("🛡️ 测试缓存降级机制...")
        
        # 通过快速连续请求来测试系统压力下的表现
        endpoint = "/api/auth/me"
        
        logger.info("发送连续请求测试降级...")
        for i in range(5):
            self.test_request("GET", endpoint, f"压力测试-{i+1}", True)
            time.sleep(0.1)
    
    def generate_quick_report(self):
        """生成快速报告"""
        logger.info("=" * 60)
        logger.info("📊 快速测试报告")
        logger.info("=" * 60)
        
        total_tests = len(self.results)
        successful_tests = len([r for r in self.results if r['success']])
        cache_hits = len([r for r in self.results if r.get('cache_hit', False)])
        
        # 计算平均响应时间
        response_times = [r['response_time'] for r in self.results if 'response_time' in r]
        avg_response_time = sum(response_times) / len(response_times) if response_times else 0
        
        # 按状态码分组
        status_codes = {}
        for result in self.results:
            code = result.get('status_code', 'error')
            status_codes[code] = status_codes.get(code, 0) + 1
        
        logger.info(f"总测试数: {total_tests}")
        logger.info(f"成功测试: {successful_tests} ({successful_tests/total_tests*100:.1f}%)")
        logger.info(f"缓存命中: {cache_hits} ({cache_hits/total_tests*100:.1f}%)")
        logger.info(f"平均响应时间: {avg_response_time:.3f}s")
        
        logger.info("\n状态码分布:")
        for code, count in sorted(status_codes.items()):
            logger.info(f"  {code}: {count} 次")
        
        # 按业务模块分析
        logger.info("\n业务模块分析:")
        modules = {
            'auth': [r for r in self.results if '/auth/' in r['endpoint']],
            'chat': [r for r in self.results if '/chat' in r['endpoint']],
            'payment': [r for r in self.results if '/payment/' in r['endpoint']],
            'oauth2': [r for r in self.results if '/oauth2/' in r['endpoint']],
            'other': [r for r in self.results if not any(x in r['endpoint'] for x in ['/auth/', '/chat', '/payment/', '/oauth2/'])]
        }
        
        for module, results in modules.items():
            if results:
                success_rate = len([r for r in results if r['success']]) / len(results) * 100
                avg_time = sum(r['response_time'] for r in results) / len(results)
                cache_rate = len([r for r in results if r.get('cache_hit', False)]) / len(results) * 100
                
                logger.info(f"  {module}: {len(results)}个测试, 成功率{success_rate:.1f}%, 缓存命中率{cache_rate:.1f}%, 平均{avg_time:.3f}s")
        
        # 保存详细结果
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        report_file = f"quick_cache_test_report_{timestamp}.json"
        
        with open(report_file, 'w', encoding='utf-8') as f:
            json.dump({
                'summary': {
                    'total_tests': total_tests,
                    'successful_tests': successful_tests,
                    'cache_hits': cache_hits,
                    'avg_response_time': avg_response_time,
                    'status_codes': status_codes
                },
                'detailed_results': self.results,
                'timestamp': datetime.now().isoformat()
            }, f, ensure_ascii=False, indent=2)
        
        logger.info(f"\n📄 详细报告已保存到: {report_file}")
        
        # 给出建议
        logger.info("\n💡 测试建议:")
        if successful_tests / total_tests < 0.7:
            logger.info("  ⚠️  成功率较低，建议检查API接口和认证配置")
        if cache_hits / total_tests < 0.3:
            logger.info("  ⚠️  缓存命中率较低，建议检查缓存配置")
        if avg_response_time > 0.5:
            logger.info("  ⚠️  平均响应时间较长，建议优化性能")
        
        if successful_tests / total_tests >= 0.7 and cache_hits / total_tests >= 0.3:
            logger.info("  ✅ 缓存系统运行良好！")
        
        logger.info("=" * 60)

def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description="快速业务缓存验证测试")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080", help="API基础URL")
    parser.add_argument("--include-performance", action="store_true", help="包含性能测试")
    parser.add_argument("--include-degradation", action="store_true", help="包含降级测试")
    
    args = parser.parse_args()
    
    # 创建测试实例
    tester = QuickBusinessCacheTest(args.base_url)
    
    try:
        # 运行基础测试
        tester.run_quick_tests()
        
        # 可选的性能测试
        if args.include_performance:
            tester.test_cache_performance()
        
        # 可选的降级测试
        if args.include_degradation:
            tester.test_cache_degradation()
        
        # 最终报告
        if args.include_performance or args.include_degradation:
            tester.generate_quick_report()
            
    except KeyboardInterrupt:
        logger.info("测试被用户中断")
    except Exception as e:
        logger.error(f"测试执行异常: {e}")

if __name__ == "__main__":
    main()
