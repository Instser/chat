#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
网络搜索内容修改功能 - 真实数据测试脚本
测试时间: 2025-07-20 11:50
作者: Augment Agent
"""

import requests
import json
import time
import mysql.connector
from datetime import datetime
import uuid

# 数据库配置 (根据memory bank中的信息)
DB_CONFIG = {
    'host': 'your-db-host.example.com',
    'port': 45266,
    'user': 'root',
    'password': 'hgjnjzpz',
    'database': 'chat_test',
    'charset': 'utf8mb4',
    'connect_timeout': 10,
    'autocommit': True,
    'use_unicode': True
}

# API配置
BASE_URL = "http://localhost:8080/api"
TEST_USERNAME = "admin"
TEST_PASSWORD = "admin123"

class RealDataTester:
    def __init__(self):
        self.session = requests.Session()
        self.jwt_token = None
        self.user_id = None
        self.db_connection = None
        self.test_message_id = None
        
    def log(self, message):
        """打印带时间戳的日志"""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] {message}")
        
    def connect_database(self):
        """连接数据库"""
        self.log("🔌 连接数据库...")
        try:
            self.db_connection = mysql.connector.connect(**DB_CONFIG)
            if self.db_connection.is_connected():
                self.log("✅ 数据库连接成功")
                return True
        except Exception as e:
            self.log(f"❌ 数据库连接失败: {str(e)}")
        return False
    
    def get_real_user_data(self):
        """获取真实用户数据"""
        self.log("👤 获取真实用户数据...")
        try:
            cursor = self.db_connection.cursor(dictionary=True)
            cursor.execute("SELECT id, username, email FROM t_user WHERE username = %s", (TEST_USERNAME,))
            user = cursor.fetchone()
            cursor.close()
            
            if user:
                self.log(f"✅ 找到用户: ID={user['id']}, 用户名={user['username']}, 邮箱={user['email']}")
                return user
            else:
                self.log("❌ 未找到测试用户")
        except Exception as e:
            self.log(f"❌ 查询用户数据失败: {str(e)}")
        return None
    
    def get_real_message_data(self):
        """获取真实消息数据"""
        self.log("💬 获取真实消息数据...")
        try:
            cursor = self.db_connection.cursor(dictionary=True)
            # 查找属于测试用户的消息
            cursor.execute("""
                SELECT m.id, m.content, m.user_id, m.created_time 
                FROM t_message m 
                WHERE m.user_id = %s 
                ORDER BY m.created_time DESC 
                LIMIT 5
            """, (self.user_id,))
            messages = cursor.fetchall()
            cursor.close()
            
            if messages:
                self.log(f"✅ 找到 {len(messages)} 条消息")
                for msg in messages:
                    content_preview = msg['content'][:50] + "..." if len(msg['content']) > 50 else msg['content']
                    self.log(f"   消息ID: {msg['id']}, 内容: {content_preview}")
                return messages
            else:
                self.log("⚠️ 未找到用户消息，将创建测试消息")
        except Exception as e:
            self.log(f"❌ 查询消息数据失败: {str(e)}")
        return []
    
    def create_test_message(self):
        """创建测试消息"""
        self.log("📝 创建测试消息...")
        try:
            cursor = self.db_connection.cursor()
            message_id = str(uuid.uuid4())
            test_content = "这是一条用于测试网络搜索内容修改功能的消息"
            
            cursor.execute("""
                INSERT INTO t_message (id, content, user_id, role, created_time, updated_time)
                VALUES (%s, %s, %s, 'user', NOW(), NOW())
            """, (message_id, test_content, self.user_id))
            
            self.db_connection.commit()
            cursor.close()
            
            self.log(f"✅ 创建测试消息成功: {message_id}")
            return message_id
        except Exception as e:
            self.log(f"❌ 创建测试消息失败: {str(e)}")
        return None
    
    def check_existing_network_content(self, message_id):
        """检查现有的网络搜索内容"""
        self.log(f"🔍 检查消息 {message_id} 的现有网络搜索内容...")
        try:
            cursor = self.db_connection.cursor(dictionary=True)
            cursor.execute("""
                SELECT id, message_id, content 
                FROM t_message_network_content 
                WHERE message_id = %s
            """, (message_id,))
            contents = cursor.fetchall()
            cursor.close()
            
            if contents:
                self.log(f"✅ 找到 {len(contents)} 条现有网络搜索内容")
                for content in contents:
                    try:
                        content_data = json.loads(content['content'])
                        self.log(f"   内容ID: {content['id']}, 项目数: {len(content_data)}")
                    except:
                        self.log(f"   内容ID: {content['id']}, 内容格式异常")
                return contents
            else:
                self.log("📋 该消息暂无网络搜索内容")
        except Exception as e:
            self.log(f"❌ 查询网络搜索内容失败: {str(e)}")
        return []
    
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
    
    def test_update_with_real_message(self, message_id):
        """使用真实消息ID测试更新功能"""
        self.log(f"🧪 测试更新真实消息 {message_id} 的网络搜索内容")
        
        test_content = [
            {
                "url": "https://example.com/real-test-1",
                "title": "真实数据测试标题1",
                "content": "这是使用真实消息ID进行的网络搜索内容修改测试，包含详细的测试信息。",
                "favicon": "https://example.com/favicon1.ico"
            },
            {
                "url": "https://example.com/real-test-2",
                "title": "真实数据测试标题2", 
                "content": "第二条测试内容，用于验证多项内容的处理能力。",
                "favicon": "https://example.com/favicon2.ico"
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
                    return True
                else:
                    self.log(f"⚠️ 真实数据测试部分成功: 业务错误 - {result.get('message')}")
            else:
                self.log(f"❌ 真实数据测试失败: HTTP错误 {response.status_code}")
                
        except Exception as e:
            self.log(f"❌ 真实数据测试异常: {str(e)}")
        
        return False
    
    def verify_database_changes(self, message_id):
        """验证数据库中的变更"""
        self.log(f"🔍 验证数据库中消息 {message_id} 的变更...")
        try:
            cursor = self.db_connection.cursor(dictionary=True)
            cursor.execute("""
                SELECT id, message_id, content, created_time, updated_time 
                FROM t_message_network_content 
                WHERE message_id = %s 
                ORDER BY updated_time DESC
            """, (message_id,))
            contents = cursor.fetchall()
            cursor.close()
            
            if contents:
                self.log(f"✅ 数据库验证成功: 找到 {len(contents)} 条网络搜索内容记录")
                for content in contents:
                    try:
                        content_data = json.loads(content['content'])
                        self.log(f"   记录ID: {content['id']}")
                        self.log(f"   创建时间: {content['created_time']}")
                        self.log(f"   更新时间: {content['updated_time']}")
                        self.log(f"   内容项数: {len(content_data)}")
                        
                        # 显示内容详情
                        for i, item in enumerate(content_data):
                            self.log(f"     项目{i+1}: {item.get('title', 'N/A')} - {item.get('url', 'N/A')}")
                    except Exception as e:
                        self.log(f"   记录ID: {content['id']}, 内容解析失败: {str(e)}")
                return True
            else:
                self.log("❌ 数据库验证失败: 未找到网络搜索内容记录")
        except Exception as e:
            self.log(f"❌ 数据库验证异常: {str(e)}")
        return False
    
    def test_permission_scenarios(self):
        """测试权限场景"""
        self.log("🔒 测试权限场景...")
        
        # 测试其他用户的消息
        try:
            cursor = self.db_connection.cursor(dictionary=True)
            cursor.execute("""
                SELECT id FROM t_message 
                WHERE user_id != %s 
                LIMIT 1
            """, (self.user_id,))
            other_message = cursor.fetchone()
            cursor.close()
            
            if other_message:
                self.log(f"🧪 测试访问其他用户消息: {other_message['id']}")
                
                test_content = [{
                    "url": "https://example.com/unauthorized",
                    "title": "未授权测试",
                    "content": "尝试修改其他用户的消息",
                    "favicon": "https://example.com/favicon.ico"
                }]
                
                response = self.session.put(
                    f"{BASE_URL}/chat/messages/{other_message['id']}/web-search-content",
                    json={"content": test_content}
                )
                
                if response.status_code == 200:
                    result = response.json()
                    if result.get("code") == 40301:  # NO_MESSAGE_PERMISSION
                        self.log("✅ 权限测试通过: 正确拒绝了未授权访问")
                    else:
                        self.log(f"⚠️ 权限测试异常: 未预期的响应 - {result}")
                else:
                    self.log(f"❌ 权限测试失败: HTTP错误 {response.status_code}")
            else:
                self.log("⚠️ 未找到其他用户的消息，跳过权限测试")
                
        except Exception as e:
            self.log(f"❌ 权限测试异常: {str(e)}")
    
    def run_comprehensive_test(self):
        """运行综合测试"""
        self.log("🚀 开始网络搜索内容修改功能 - 真实数据综合测试")
        self.log("=" * 70)
        
        # 1. 连接数据库
        if not self.connect_database():
            self.log("❌ 数据库连接失败，终止测试")
            return
        
        # 2. 获取真实用户数据
        user_data = self.get_real_user_data()
        if not user_data:
            self.log("❌ 获取用户数据失败，终止测试")
            return
        
        # 3. 用户登录
        if not self.login():
            self.log("❌ 用户登录失败，终止测试")
            return
        
        # 4. 获取真实消息数据
        messages = self.get_real_message_data()
        
        # 5. 如果没有消息，创建测试消息
        if not messages:
            test_message_id = self.create_test_message()
            if test_message_id:
                self.test_message_id = test_message_id
            else:
                self.log("❌ 创建测试消息失败，终止测试")
                return
        else:
            # 使用第一条真实消息
            self.test_message_id = messages[0]['id']
        
        self.log(f"📋 使用消息ID进行测试: {self.test_message_id}")
        
        # 6. 检查现有网络搜索内容
        existing_content = self.check_existing_network_content(self.test_message_id)
        
        # 7. 测试更新功能
        if self.test_update_with_real_message(self.test_message_id):
            # 8. 验证数据库变更
            time.sleep(1)  # 等待数据库写入
            self.verify_database_changes(self.test_message_id)
        
        # 9. 测试权限场景
        self.test_permission_scenarios()
        
        # 10. 关闭数据库连接
        if self.db_connection and self.db_connection.is_connected():
            self.db_connection.close()
            self.log("🔌 数据库连接已关闭")
        
        self.log("=" * 70)
        self.log("🎉 真实数据综合测试完成")

if __name__ == "__main__":
    tester = RealDataTester()
    tester.run_comprehensive_test()
