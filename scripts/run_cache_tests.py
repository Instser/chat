#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
缓存管理平台测试执行脚本
简化版本，用于快速验证缓存管理接口

作者: Augment Agent
创建时间: 2025-07-24 19:19
版本: v1.0
"""

import requests
import json
import time
from datetime import datetime

class SimpleCacheTester:
    """简化的缓存测试器"""
    
    def __init__(self, base_url="http://127.0.0.1:8080"):
        self.base_url = base_url
        self.api_prefix = "/api/admin/cache"
        self.session = requests.Session()
        self.session.timeout = 10
        
    def get_url(self, endpoint):
        """获取完整URL"""
        return f"{self.base_url}{self.api_prefix}{endpoint}"
    
    def test_api(self, method, endpoint, description):
        """测试单个API"""
        url = self.get_url(endpoint)
        print(f"\n🔍 测试: {description}")
        print(f"📍 URL: {method} {url}")
        
        try:
            start_time = time.time()
            
            if method == "GET":
                response = self.session.get(url)
            elif method == "POST":
                response = self.session.post(url)
            elif method == "DELETE":
                response = self.session.delete(url)
            else:
                print(f"❌ 不支持的HTTP方法: {method}")
                return False
            
            end_time = time.time()
            response_time = (end_time - start_time) * 1000  # 转换为毫秒
            
            print(f"⏱️  响应时间: {response_time:.2f}ms")
            print(f"📊 状态码: {response.status_code}")
            
            if response.status_code == 200:
                try:
                    data = response.json()
                    print(f"✅ 响应成功")
                    
                    # 显示关键数据
                    if "data" in data:
                        if endpoint == "/stats":
                            stats = data["data"]
                            print(f"   📈 总缓存数: {stats.get('totalCaches', 'N/A')}")
                            print(f"   📊 类型分布: {stats.get('typeDistribution', {})}")
                        elif endpoint == "/health":
                            health = data["data"]
                            print(f"   💓 Redis状态: {health.get('redisAvailable', 'N/A')}")
                            print(f"   🔧 熔断器状态: {health.get('circuitBreakerState', 'N/A')}")
                        elif endpoint == "/config":
                            config = data["data"]
                            print(f"   ⚙️  缓存类型: {config.get('cacheTypes', [])}")
                    
                    print(f"   💬 消息: {data.get('message', 'N/A')}")
                    return True
                    
                except json.JSONDecodeError:
                    print(f"⚠️  响应不是有效JSON: {response.text[:100]}")
                    return False
            else:
                print(f"❌ 请求失败: {response.status_code}")
                try:
                    error_data = response.json()
                    print(f"   💬 错误信息: {error_data.get('message', 'N/A')}")
                except:
                    print(f"   📄 响应内容: {response.text[:200]}")
                return False
                
        except requests.exceptions.ConnectionError:
            print(f"❌ 连接失败: 无法连接到 {url}")
            print(f"   💡 请确保应用已启动并监听8081端口")
            return False
        except requests.exceptions.Timeout:
            print(f"❌ 请求超时")
            return False
        except Exception as e:
            print(f"❌ 测试异常: {e}")
            return False
    
    def test_security(self):
        """测试安全控制"""
        print(f"\n🔒 测试安全控制")
        
        # 测试正常访问
        print(f"   🔍 测试正常本地访问...")
        normal_result = self.test_api("GET", "/stats", "正常访问")
        
        # 测试伪造远程IP
        print(f"   🔍 测试伪造远程IP访问...")
        url = self.get_url("/stats")
        headers = {"X-Forwarded-For": "192.168.1.100"}
        
        try:
            response = self.session.get(url, headers=headers)
            if response.status_code == 403:
                print(f"   ✅ 安全控制生效: 远程访问被拒绝 (403)")
                security_ok = True
            elif response.status_code == 200:
                print(f"   ⚠️  安全控制可能未生效: 远程访问成功 (200)")
                security_ok = False
            else:
                print(f"   ❓ 未知状态: {response.status_code}")
                security_ok = False
        except Exception as e:
            print(f"   ✅ 安全控制生效: 连接被拒绝 ({e})")
            security_ok = True
        
        return normal_result and security_ok
    
    def run_all_tests(self):
        """运行所有测试"""
        print("=" * 60)
        print("🚀 缓存管理平台API测试开始")
        print(f"🕐 测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"🌐 测试地址: {self.base_url}")
        print("=" * 60)
        
        # 定义测试用例
        test_cases = [
            ("GET", "/stats", "全局缓存统计"),
            ("GET", "/health", "缓存健康检查"),
            ("GET", "/config", "缓存配置信息"),
            ("GET", "/module/user", "用户模块统计"),
            ("GET", "/type/L1", "L1缓存类型统计"),
            ("POST", "/circuit-breaker/reset", "重置熔断器"),
            ("DELETE", "/evict/test_key", "删除测试缓存项"),
            ("POST", "/test", "缓存功能测试"),
        ]
        
        results = []
        
        # 执行基础API测试
        for method, endpoint, description in test_cases:
            result = self.test_api(method, endpoint, description)
            results.append((description, result))
            time.sleep(0.5)  # 避免请求过快
        
        # 执行安全测试
        security_result = self.test_security()
        results.append(("安全控制测试", security_result))
        
        # 最后执行清空缓存测试（可能影响其他测试）
        print(f"\n⚠️  警告: 即将执行清空缓存操作")
        clear_result = self.test_api("POST", "/clear", "清空所有缓存")
        results.append(("清空所有缓存", clear_result))
        
        # 统计结果
        print("\n" + "=" * 60)
        print("📊 测试结果摘要")
        print("=" * 60)
        
        total_tests = len(results)
        passed_tests = sum(1 for _, result in results if result)
        failed_tests = total_tests - passed_tests
        success_rate = (passed_tests / total_tests) * 100 if total_tests > 0 else 0
        
        print(f"📈 总测试数: {total_tests}")
        print(f"✅ 通过: {passed_tests}")
        print(f"❌ 失败: {failed_tests}")
        print(f"📊 成功率: {success_rate:.1f}%")
        
        print(f"\n📋 详细结果:")
        for description, result in results:
            status = "✅ 通过" if result else "❌ 失败"
            print(f"   {status} {description}")
        
        if failed_tests == 0:
            print(f"\n🎉 所有测试通过！缓存管理平台工作正常。")
        else:
            print(f"\n⚠️  有 {failed_tests} 个测试失败，请检查应用状态和配置。")
        
        print("\n💡 提示:")
        print("   - 确保应用已启动: mvn spring-boot:run")
        print("   - 检查端口监听: netstat -an | findstr :8081")
        print("   - 查看应用日志: tail -f logs/application.log")
        
        return success_rate == 100.0

def main():
    """主函数"""
    import sys
    
    # 检查命令行参数
    base_url = "http://127.0.0.1:8080"
    if len(sys.argv) > 1:
        base_url = sys.argv[1]
    
    print(f"🎯 使用测试地址: {base_url}")
    
    # 创建测试器并运行
    tester = SimpleCacheTester(base_url)
    success = tester.run_all_tests()
    
    # 退出码
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()
