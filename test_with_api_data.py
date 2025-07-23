#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
网络搜索内容修改功能 - 通过API获取真实数据测试脚本
测试时间: 2025-07-20 11:55
作者: Augment Agent
"""

import requests
import json
import time
from datetime import datetime
import uuid

# API配置
BASE_URL = "http://localhost:8080/api"
TEST_USERNAME = "admin"
TEST_PASSWORD = "admin123"

class APIDataTester:
    def __init__(self):
        self.session = requests.Session()
        self.jwt_token = None
        self.user_id = None
        self.test_message_id = None
        
    def log(self, message):
        """打印带时间戳的日志"""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] {message}")
        
    def login(self):
        """用户登录"""
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
    
    def get_user_info(self):
        """获取用户信息"""
        self.log("👤 获取用户信息...")
        try:
            response = self.session.get(f"{BASE_URL}/auth/me")
            if response.status_code == 200:
                result = response.json()
                if result.get("code") == 200:
                    user_data = result["data"]
                    self.log(f"✅ 用户信息: ID={user_data['id']}, 用户名={user_data['username']}")
                    self.log(f"   邮箱: {user_data.get('email', 'N/A')}")
                    self.log(f"   角色: {user_data.get('role', 'N/A')}")
                    if 'userPackage' in user_data:
                        package_info = user_data['userPackage']
                        self.log(f"   套餐: {package_info.get('packageName', 'N/A')}")
                    return user_data
                else:
                    self.log(f"❌ 获取用户信息失败: {result.get('message')}")
            else:
                self.log(f"❌ 获取用户信息请求失败，状态码: {response.status_code}")
        except Exception as e:
            self.log(f"❌ 获取用户信息异常: {str(e)}")
        return None
    
    def create_test_chat_session(self):
        """创建测试聊天会话"""
        self.log("💬 创建测试聊天会话...")
        try:
            # 创建新的聊天会话
            response = self.session.post(f"{BASE_URL}/chat/sessions", json={})
            if response.status_code == 200:
                result = response.json()
                if result.get("code") == 200:
                    session_id = result["data"]["sessionId"]
                    self.log(f"✅ 创建聊天会话成功: {session_id}")
                    return session_id
                else:
                    self.log(f"❌ 创建聊天会话失败: {result.get('message')}")
            else:
                self.log(f"❌ 创建聊天会话请求失败，状态码: {response.status_code}")
        except Exception as e:
            self.log(f"❌ 创建聊天会话异常: {str(e)}")
        return None
    
    def get_existing_messages(self, session_id):
        """获取现有消息"""
        self.log(f"📋 获取会话 {session_id} 的现有消息...")
        try:
            response = self.session.get(f"{BASE_URL}/chat/sessions/{session_id}/messages")
            if response.status_code == 200:
                result = response.json()
                if result.get("code") == 200:
                    messages = result.get("data", [])
                    self.log(f"✅ 找到 {len(messages)} 条现有消息")
                    if messages:
                        # 使用第一条消息进行测试
                        message = messages[0]
                        message_id = message.get("id")
                        content_preview = message.get("content", "")[:50] + "..." if len(message.get("content", "")) > 50 else message.get("content", "")
                        self.log(f"   使用消息ID: {message_id}")
                        self.log(f"   消息内容: {content_preview}")
                        return message_id
                    else:
                        self.log("⚠️ 会话中暂无消息，创建测试消息ID")
                        return str(uuid.uuid4())
                else:
                    self.log(f"❌ 获取消息失败: {result.get('message')}")
            else:
                self.log(f"❌ 获取消息请求失败，状态码: {response.status_code}")
        except Exception as e:
            self.log(f"❌ 获取消息异常: {str(e)}")

        # 如果获取失败，生成一个测试用的UUID
        test_message_id = str(uuid.uuid4())
        self.log(f"⚠️ 使用生成的测试消息ID: {test_message_id}")
        return test_message_id
    
    def test_update_with_real_message(self, message_id):
        """使用真实消息ID测试更新功能"""
        self.log(f"🧪 测试更新真实消息 {message_id} 的网络搜索内容")
        
        test_content = [
            {
                "url": "https://example.com/real-test-1",
                "title": "真实数据测试标题1",
                "content": "这是使用真实消息ID进行的网络搜索内容修改测试，包含详细的测试信息。测试内容验证了API的完整功能，包括参数验证、权限检查和数据存储。",
                "favicon": "https://example.com/favicon1.ico"
            },
            {
                "url": "https://example.com/real-test-2",
                "title": "真实数据测试标题2", 
                "content": "第二条测试内容，用于验证多项内容的处理能力。这个测试确保系统能够正确处理多个网络搜索结果项目。",
                "favicon": "https://example.com/favicon2.ico"
            },
            {
                "url": "https://github.com/example/project",
                "title": "GitHub项目示例",
                "content": "这是一个GitHub项目的示例，用于测试不同类型的网络内容处理。",
                "favicon": "https://github.com/favicon.ico"
            }
        ]
        
        try:
            response = self.session.put(
                f"{BASE_URL}/chat/messages/{message_id}/web-search-content",
                json={"content": test_content}
            )
            
            self.log(f"📤 请求状态码: {response.status_code}")
            self.log(f"📤 响应内容: {response.text}")
            
            if response.status_code == 200:
                result = response.json()
                if result.get("code") == 200:
                    self.log("✅ 真实数据测试成功: 网络搜索内容更新成功")
                    self.log(f"   更新了 {len(test_content)} 项网络搜索内容")
                    return True
                else:
                    self.log(f"⚠️ 真实数据测试部分成功: 业务错误 - {result.get('message')}")
                    self.log(f"   错误码: {result.get('code')}")
            else:
                self.log(f"❌ 真实数据测试失败: HTTP错误 {response.status_code}")
                
        except Exception as e:
            self.log(f"❌ 真实数据测试异常: {str(e)}")
        
        return False
    
    def test_parameter_validation(self, message_id):
        """测试参数验证"""
        self.log("🧪 测试参数验证功能...")
        
        # 测试1: 空内容
        self.log("🔍 测试1: 空内容验证")
        try:
            response = self.session.put(
                f"{BASE_URL}/chat/messages/{message_id}/web-search-content",
                json={"content": []}
            )
            result = response.json()
            self.log(f"   空内容测试 - 状态码: {response.status_code}, 错误码: {result.get('code')}, 消息: {result.get('message')}")
        except Exception as e:
            self.log(f"   空内容测试异常: {str(e)}")
        
        # 测试2: 内容格式错误
        self.log("🔍 测试2: 内容格式验证")
        invalid_content = [
            {
                "url": "https://example.com/test",
                "title": "测试标题",
                # 缺少content和favicon字段
            }
        ]
        
        try:
            response = self.session.put(
                f"{BASE_URL}/chat/messages/{message_id}/web-search-content",
                json={"content": invalid_content}
            )
            result = response.json()
            self.log(f"   格式错误测试 - 状态码: {response.status_code}, 错误码: {result.get('code')}, 消息: {result.get('message')}")
        except Exception as e:
            self.log(f"   格式错误测试异常: {str(e)}")
        
        # 测试3: 内容过多
        self.log("🔍 测试3: 内容数量限制验证")
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
                f"{BASE_URL}/chat/messages/{message_id}/web-search-content",
                json={"content": large_content}
            )
            result = response.json()
            self.log(f"   内容过多测试 - 状态码: {response.status_code}, 错误码: {result.get('code')}, 消息: {result.get('message')}")
        except Exception as e:
            self.log(f"   内容过多测试异常: {str(e)}")
    
    def test_rate_limiting(self, message_id):
        """测试频率限制"""
        self.log("🧪 测试频率限制 (10次/分钟)")
        
        test_content = [{
            "url": "https://example.com/rate-test",
            "title": "频率限制测试",
            "content": "用于测试频率限制的内容",
            "favicon": "https://example.com/favicon.ico"
        }]
        
        success_count = 0
        rate_limited_count = 0
        
        # 快速发送12个请求
        for i in range(12):
            try:
                response = self.session.put(
                    f"{BASE_URL}/chat/messages/{message_id}/web-search-content",
                    json={"content": test_content}
                )
                
                if response.status_code == 200:
                    result = response.json()
                    if result.get("code") == 200:
                        success_count += 1
                    elif result.get("code") == 40901:  # RATE_LIMIT_EXCEEDED
                        rate_limited_count += 1
                        self.log(f"🚫 第{i+1}次请求被频率限制")
                
                time.sleep(0.2)  # 短暂间隔
                
            except Exception as e:
                self.log(f"❌ 频率限制测试第{i+1}次请求异常: {str(e)}")
        
        self.log(f"📊 频率限制测试结果: 成功{success_count}次, 被限制{rate_limited_count}次")
        
        if rate_limited_count > 0:
            self.log("✅ 频率限制功能正常工作")
        else:
            self.log("⚠️ 频率限制可能未生效或限制较宽松")
    
    def check_monitoring_metrics(self):
        """检查监控指标"""
        self.log("📊 检查监控指标...")
        
        try:
            # 检查健康状态
            health_response = requests.get("http://localhost:8081/actuator/health")
            if health_response.status_code == 200:
                health_data = health_response.json()
                self.log(f"💚 应用健康状态: {health_data.get('status', 'UNKNOWN')}")
            
            # 检查指标
            metrics_response = requests.get("http://localhost:8081/actuator/metrics")
            if metrics_response.status_code == 200:
                metrics_data = metrics_response.json()
                self.log(f"📈 可用指标数量: {len(metrics_data.get('names', []))}")
                
                # 查找我们的自定义指标
                web_search_metrics = [name for name in metrics_data.get('names', []) 
                                    if 'web_search' in name.lower()]
                if web_search_metrics:
                    self.log(f"🎯 网络搜索相关指标: {web_search_metrics}")
                    
                    # 获取具体指标值
                    for metric_name in web_search_metrics[:3]:  # 只显示前3个
                        try:
                            metric_response = requests.get(f"http://localhost:8081/actuator/metrics/{metric_name}")
                            if metric_response.status_code == 200:
                                metric_data = metric_response.json()
                                measurements = metric_data.get('measurements', [])
                                for measurement in measurements:
                                    value = measurement.get('value', 0)
                                    statistic = measurement.get('statistic', 'VALUE')
                                    self.log(f"   {metric_name} ({statistic}): {value}")
                        except:
                            pass
                else:
                    self.log("⚠️ 未找到网络搜索相关指标")
            
        except Exception as e:
            self.log(f"❌ 监控指标检查异常: {str(e)}")
    
    def run_comprehensive_test(self):
        """运行综合测试"""
        self.log("🚀 开始网络搜索内容修改功能 - API真实数据综合测试")
        self.log("=" * 70)
        
        # 1. 用户登录
        if not self.login():
            self.log("❌ 用户登录失败，终止测试")
            return
        
        # 2. 获取用户信息
        user_info = self.get_user_info()
        if not user_info:
            self.log("❌ 获取用户信息失败，终止测试")
            return
        
        # 3. 创建测试聊天会话
        session_id = self.create_test_chat_session()
        if not session_id:
            self.log("❌ 创建聊天会话失败，终止测试")
            return
        
        # 4. 获取现有消息或创建测试消息ID
        message_id = self.get_existing_messages(session_id)
        
        self.test_message_id = message_id
        self.log(f"📋 使用消息ID进行测试: {self.test_message_id}")
        
        # 5. 测试更新功能
        if self.test_update_with_real_message(self.test_message_id):
            self.log("✅ 主要功能测试通过")
        
        # 6. 测试参数验证
        self.test_parameter_validation(self.test_message_id)
        
        # 7. 测试频率限制
        self.test_rate_limiting(self.test_message_id)
        
        # 8. 检查监控指标
        self.check_monitoring_metrics()
        
        self.log("=" * 70)
        self.log("🎉 API真实数据综合测试完成")

if __name__ == "__main__":
    tester = APIDataTester()
    tester.run_comprehensive_test()
