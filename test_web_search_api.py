#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
网络搜索内容修改功能API测试脚本
测试时间: 2025-07-20 11:40
作者: Augment Agent
"""

import requests
import json
import time
import threading
from datetime import datetime
import uuid

# 测试配置
BASE_URL = "http://localhost:8080/api"
ACTUATOR_URL = "http://localhost:8081/actuator"

# 测试用户凭据 (根据memory bank中的信息)
TEST_USERNAME = "admin"
TEST_PASSWORD = "admin123"

class WebSearchAPITester:
    def __init__(self):
        self.session = requests.Session()
        self.jwt_token = None
        self.user_id = None
        self.test_message_id = str(uuid.uuid4())
        
    def log(self, message):
        """打印带时间戳的日志"""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] {message}")
        
    def login(self):
        """用户登录获取JWT token"""
        self.log("🔐 开始用户登录...")
        
        login_data = {
            "usernameOrEmail": TEST_USERNAME,
            "password": TEST_PASSWORD
        }
        
        try:
            response = self.session.post(f"{BASE_URL}/auth/login", json=login_data)
            if response.status_code == 200:
                result = response.json()
                if result.get("code") == 200:
                    # JWT token通过Cookie返回，session会自动处理
                    self.user_id = result["data"]["id"]

                    # 检查是否有auth-token cookie
                    auth_cookie = None
                    for cookie in self.session.cookies:
                        if cookie.name == 'auth-token':
                            auth_cookie = cookie.value
                            break

                    if auth_cookie:
                        self.jwt_token = auth_cookie
                        self.log(f"✅ 登录成功，用户ID: {self.user_id}")
                        self.log(f"🔑 JWT Token: {auth_cookie[:50]}...")
                        return True
                    else:
                        self.log("❌ 未找到认证Cookie")
                else:
                    self.log(f"❌ 登录失败: {result.get('message', '未知错误')}")
            else:
                self.log(f"❌ 登录请求失败，状态码: {response.status_code}")
        except Exception as e:
            self.log(f"❌ 登录异常: {str(e)}")

        return False
    
    def create_test_message(self):
        """创建测试消息和网络搜索内容"""
        self.log("📝 创建测试消息和网络搜索内容...")
        
        # 这里应该调用创建消息的API，但为了简化测试，我们直接使用一个已知的messageId
        # 在实际测试中，你可能需要先创建一个真实的消息
        self.log(f"📋 使用测试消息ID: {self.test_message_id}")
        return True
    
    def test_update_web_search_content_success(self):
        """测试正常更新网络搜索内容"""
        self.log("🧪 测试1: 正常更新网络搜索内容")
        
        test_content = [
            {
                "url": "https://example.com/test1",
                "title": "测试标题1",
                "content": "这是测试内容1，用于验证网络搜索内容修改功能。",
                "favicon": "https://example.com/favicon1.ico"
            },
            {
                "url": "https://example.com/test2", 
                "title": "测试标题2",
                "content": "这是测试内容2，包含更多的详细信息。",
                "favicon": "https://example.com/favicon2.ico"
            }
        ]
        
        try:
            response = self.session.put(
                f"{BASE_URL}/chat/messages/{self.test_message_id}/web-search-content",
                json={"content": test_content}
            )
            
            self.log(f"📤 请求状态码: {response.status_code}")
            self.log(f"📤 响应内容: {response.text}")
            
            if response.status_code == 200:
                result = response.json()
                if result.get("code") == 200:
                    self.log("✅ 测试1通过: 正常更新成功")
                    return True
                else:
                    self.log(f"⚠️ 测试1部分通过: 业务错误 - {result.get('message')}")
            else:
                self.log(f"❌ 测试1失败: HTTP错误 {response.status_code}")
                
        except Exception as e:
            self.log(f"❌ 测试1异常: {str(e)}")
        
        return False
    
    def test_parameter_validation(self):
        """测试参数验证"""
        self.log("🧪 测试2: 参数验证")
        
        # 测试空内容
        self.log("🔍 测试2.1: 空内容验证")
        try:
            response = self.session.put(
                f"{BASE_URL}/chat/messages/{self.test_message_id}/web-search-content",
                json={"content": []}
            )
            self.log(f"📤 空内容测试 - 状态码: {response.status_code}, 响应: {response.text}")
        except Exception as e:
            self.log(f"❌ 空内容测试异常: {str(e)}")
        
        # 测试内容过多
        self.log("🔍 测试2.2: 内容数量限制验证")
        large_content = []
        for i in range(55):  # 超过50个限制
            large_content.append({
                "url": f"https://example.com/test{i}",
                "title": f"测试标题{i}",
                "content": f"测试内容{i}",
                "favicon": f"https://example.com/favicon{i}.ico"
            })
        
        try:
            response = self.session.put(
                f"{BASE_URL}/chat/messages/{self.test_message_id}/web-search-content",
                json={"content": large_content}
            )
            self.log(f"📤 内容过多测试 - 状态码: {response.status_code}, 响应: {response.text}")
        except Exception as e:
            self.log(f"❌ 内容过多测试异常: {str(e)}")
        
        # 测试格式错误
        self.log("🔍 测试2.3: 内容格式验证")
        invalid_content = [
            {
                "url": "https://example.com/test",
                "title": "测试标题",
                # 缺少content和favicon字段
            }
        ]
        
        try:
            response = self.session.put(
                f"{BASE_URL}/chat/messages/{self.test_message_id}/web-search-content",
                json={"content": invalid_content}
            )
            self.log(f"📤 格式错误测试 - 状态码: {response.status_code}, 响应: {response.text}")
        except Exception as e:
            self.log(f"❌ 格式错误测试异常: {str(e)}")
    
    def test_rate_limiting(self):
        """测试频率限制"""
        self.log("🧪 测试3: 频率限制 (10次/分钟)")
        
        test_content = [{
            "url": "https://example.com/rate-test",
            "title": "频率限制测试",
            "content": "用于测试频率限制的内容",
            "favicon": "https://example.com/favicon.ico"
        }]
        
        success_count = 0
        rate_limited_count = 0
        
        # 快速发送15个请求
        for i in range(15):
            try:
                response = self.session.put(
                    f"{BASE_URL}/chat/messages/{self.test_message_id}/web-search-content",
                    json={"content": test_content}
                )
                
                if response.status_code == 200:
                    result = response.json()
                    if result.get("code") == 200:
                        success_count += 1
                    elif result.get("code") == 40901:  # RATE_LIMIT_EXCEEDED
                        rate_limited_count += 1
                        self.log(f"🚫 第{i+1}次请求被频率限制")
                
                time.sleep(0.1)  # 短暂间隔
                
            except Exception as e:
                self.log(f"❌ 频率限制测试第{i+1}次请求异常: {str(e)}")
        
        self.log(f"📊 频率限制测试结果: 成功{success_count}次, 被限制{rate_limited_count}次")
        
        if rate_limited_count > 0:
            self.log("✅ 测试3通过: 频率限制正常工作")
        else:
            self.log("⚠️ 测试3警告: 频率限制可能未生效")
    
    def test_concurrent_requests(self):
        """测试并发请求"""
        self.log("🧪 测试4: 并发请求")
        
        def make_request(thread_id):
            test_content = [{
                "url": f"https://example.com/concurrent-test-{thread_id}",
                "title": f"并发测试{thread_id}",
                "content": f"并发测试内容{thread_id}",
                "favicon": f"https://example.com/favicon{thread_id}.ico"
            }]
            
            try:
                response = self.session.put(
                    f"{BASE_URL}/chat/messages/{self.test_message_id}/web-search-content",
                    json={"content": test_content}
                )
                self.log(f"🔄 线程{thread_id} - 状态码: {response.status_code}")
            except Exception as e:
                self.log(f"❌ 线程{thread_id}异常: {str(e)}")
        
        # 创建5个并发线程
        threads = []
        for i in range(5):
            thread = threading.Thread(target=make_request, args=(i,))
            threads.append(thread)
        
        # 启动所有线程
        for thread in threads:
            thread.start()
        
        # 等待所有线程完成
        for thread in threads:
            thread.join()
        
        self.log("✅ 测试4完成: 并发请求测试")
    
    def check_monitoring_metrics(self):
        """检查监控指标"""
        self.log("📊 检查监控指标...")
        
        try:
            # 检查健康状态
            health_response = requests.get(f"{ACTUATOR_URL}/health")
            if health_response.status_code == 200:
                health_data = health_response.json()
                self.log(f"💚 应用健康状态: {health_data.get('status', 'UNKNOWN')}")
            
            # 检查指标
            metrics_response = requests.get(f"{ACTUATOR_URL}/metrics")
            if metrics_response.status_code == 200:
                metrics_data = metrics_response.json()
                self.log(f"📈 可用指标数量: {len(metrics_data.get('names', []))}")
                
                # 查找我们的自定义指标
                web_search_metrics = [name for name in metrics_data.get('names', []) 
                                    if 'web_search' in name.lower()]
                if web_search_metrics:
                    self.log(f"🎯 网络搜索相关指标: {web_search_metrics}")
                else:
                    self.log("⚠️ 未找到网络搜索相关指标")
            
        except Exception as e:
            self.log(f"❌ 监控指标检查异常: {str(e)}")
    
    def run_all_tests(self):
        """运行所有测试"""
        self.log("🚀 开始网络搜索内容修改功能API测试")
        self.log("=" * 60)
        
        # 登录
        if not self.login():
            self.log("❌ 登录失败，终止测试")
            return
        
        # 创建测试数据
        if not self.create_test_message():
            self.log("❌ 创建测试数据失败，终止测试")
            return
        
        # 运行测试
        self.test_update_web_search_content_success()
        time.sleep(1)
        
        self.test_parameter_validation()
        time.sleep(1)
        
        self.test_rate_limiting()
        time.sleep(1)
        
        self.test_concurrent_requests()
        time.sleep(1)
        
        # 检查监控指标
        self.check_monitoring_metrics()
        
        self.log("=" * 60)
        self.log("🎉 所有测试完成")

if __name__ == "__main__":
    tester = WebSearchAPITester()
    tester.run_all_tests()
