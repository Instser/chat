#!/usr/bin/env python3
"""
联网功能全面测试脚本
测试所有联网功能的逻辑正确性
"""

import requests
import json
import time
import sys
from typing import Dict, Any, Optional

class NetworkFunctionalityTester:
    def __init__(self, base_url: str = "http://localhost:8080/api"):
        self.base_url = base_url
        self.session = requests.Session()
        self.token = None
        self.user_id = None
        self.session_id = None
        
    def login(self, username: str = "admin", password: str = "admin123") -> bool:
        """登录获取token"""
        try:
            response = self.session.post(f"{self.base_url}/auth/login", json={
                "usernameOrEmail": username,
                "password": password
            })
            
            if response.status_code == 200:
                data = response.json()
                if data.get("code") == 200:
                    # 从Cookie中获取token
                    cookies = response.cookies
                    if 'auth-token' in cookies:
                        self.token = cookies['auth-token']
                        print(f"✅ 登录成功，获取到token")

                    self.user_id = data["data"]["id"]
                    print(f"✅ 登录成功，用户ID: {self.user_id}")
                    return True
                else:
                    print(f"❌ 登录失败: {data.get('message', '未知错误')}")
                    return False
            else:
                print(f"❌ 登录请求失败: {response.status_code}")
                return False
        except Exception as e:
            print(f"❌ 登录异常: {e}")
            return False
    
    def create_session(self) -> bool:
        """创建聊天会话"""
        try:
            response = self.session.post(f"{self.base_url}/chats", json={
                "title": "联网功能测试会话",
                "modelId": 1
            })
            
            if response.status_code == 200:
                data = response.json()
                if data.get("code") == 200:
                    self.session_id = data["data"]["id"]
                    print(f"✅ 会话创建成功，会话ID: {self.session_id}")
                    return True
                else:
                    print(f"❌ 会话创建失败: {data.get('message', '未知错误')}")
                    return False
            else:
                print(f"❌ 会话创建请求失败: {response.status_code}")
                return False
        except Exception as e:
            print(f"❌ 会话创建异常: {e}")
            return False
    
    def send_chat_message(self, content: str, network: bool = False, model_id: int = 1) -> Optional[Dict]:
        """发送聊天消息"""
        try:
            payload = {
                "sessionId": self.session_id,
                "messages": [{"role": "user", "content": content}],
                "modelId": model_id
            }
            
            if network:
                payload["network"] = True
            
            response = self.session.post(f"{self.base_url}/chat", json=payload)

            print(f"🔍 聊天请求状态码: {response.status_code}")
            if response.status_code != 200:
                print(f"🔍 聊天请求失败，响应: {response.text[:200]}")
                return None

            if response.status_code == 200:
                # 这是SSE响应，需要解析
                lines = response.text.strip().split('\n')
                result = {"messages": [], "ai_response": ""}

                for line in lines:
                    # 解析SSE格式：0:"内容" 表示AI回复内容
                    if line.startswith('0:"') and line.endswith('"'):
                        content = line[2:-1]  # 去掉 0:" 和 "
                        result["ai_response"] += content
                    # 解析其他格式的数据
                    elif line.startswith('data: '):
                        try:
                            data = json.loads(line[6:])
                            if data.get("type") == "message":
                                result["messages"].append(data["data"])
                        except json.JSONDecodeError:
                            continue

                return result
            else:
                print(f"❌ 聊天请求失败: {response.status_code}")
                return None
        except Exception as e:
            print(f"❌ 聊天异常: {e}")
            return None
    
    def retry_message(self, message_id: str, network: bool = False, model_id: int = 1) -> Optional[Dict]:
        """重试消息"""
        try:
            payload = {
                "sessionId": self.session_id,
                "messageId": message_id,
                "modelId": model_id
            }
            
            if network:
                payload["network"] = True
            
            response = self.session.post(f"{self.base_url}/chat/retry", json=payload)
            
            if response.status_code == 200:
                # 解析SSE响应
                lines = response.text.strip().split('\n')
                result = {"ai_response": ""}

                for line in lines:
                    # 解析SSE格式：0:"内容" 表示AI回复内容
                    if line.startswith('0:"') and line.endswith('"'):
                        content = line[2:-1]  # 去掉 0:" 和 "
                        result["ai_response"] += content

                return result
            else:
                print(f"❌ 重试请求失败: {response.status_code}")
                return None
        except Exception as e:
            print(f"❌ 重试异常: {e}")
            return None
    
    def edit_resend_message(self, message_id: str, new_content: str, network: bool = False, model_id: int = 1) -> Optional[Dict]:
        """编辑重发消息"""
        try:
            payload = {
                "sessionId": self.session_id,
                "messageId": message_id,
                "newContent": new_content,
                "modelId": model_id
            }
            
            if network:
                payload["network"] = True
            
            response = self.session.post(f"{self.base_url}/chat/edit-resend", json=payload)
            
            if response.status_code == 200:
                # 解析SSE响应
                lines = response.text.strip().split('\n')
                result = {"ai_response": ""}

                for line in lines:
                    # 解析SSE格式：0:"内容" 表示AI回复内容
                    if line.startswith('0:"') and line.endswith('"'):
                        content = line[2:-1]  # 去掉 0:" 和 "
                        result["ai_response"] += content

                return result
            else:
                print(f"❌ 编辑重发请求失败: {response.status_code}")
                return None
        except Exception as e:
            print(f"❌ 编辑重发异常: {e}")
            return None
    
    def get_session_messages(self) -> Optional[list]:
        """获取会话消息"""
        try:
            response = self.session.get(f"{self.base_url}/chats/{self.session_id}/messages")
            
            if response.status_code == 200:
                data = response.json()
                if data.get("code") == 200:
                    return data["data"]["messages"]  # 根据API文档，消息在data.messages中
                else:
                    print(f"❌ 获取消息失败: {data.get('message', '未知错误')}")
                    return None
            else:
                print(f"❌ 获取消息请求失败: {response.status_code}")
                return None
        except Exception as e:
            print(f"❌ 获取消息异常: {e}")
            return None
    
    def check_database_consistency(self) -> bool:
        """检查数据库一致性（需要数据库访问权限）"""
        # 这里可以添加数据库查询逻辑
        # 由于没有直接的数据库访问接口，我们通过API间接验证
        messages = self.get_session_messages()
        if not messages:
            return False
        
        print("📊 数据库一致性检查:")
        for msg in messages:
            has_web_search = msg.get("hasWebSearch", 0)
            msg_id = msg.get("id", "unknown")
            print(f"  消息ID: {msg_id[:8]}..., 联网标记: {has_web_search}, 角色: {msg['role']}")
        
        return True
    
    def run_comprehensive_test(self):
        """运行全面测试"""
        print("🚀 开始联网功能全面测试")
        print("=" * 60)
        
        # 1. 登录
        if not self.login():
            return False
        
        # 2. 创建会话
        if not self.create_session():
            return False
        
        print("\n📝 测试场景1: 正常聊天 - 启用联网")
        print("-" * 40)
        result1 = self.send_chat_message("今天的天气怎么样？", network=True)
        if result1:
            print(f"✅ 联网聊天成功，AI回复长度: {len(result1['ai_response'])}")

            # 获取消息列表来获取消息ID
            messages = self.get_session_messages()
            user_message_id = None
            ai_message_id = None
            if messages:
                # 获取最后两条消息（用户消息和AI回复）
                for msg in reversed(messages[-2:]):
                    if msg["role"] == "user" and user_message_id is None:
                        user_message_id = msg["id"]
                    elif msg["role"] == "assistant" and ai_message_id is None:
                        ai_message_id = msg["id"]

            print(f"📝 用户消息ID: {user_message_id}")
            print(f"🤖 AI消息ID: {ai_message_id}")
        else:
            print("❌ 联网聊天失败")
            return False
        
        time.sleep(2)
        
        print("\n📝 测试场景2: 正常聊天 - 不启用联网")
        print("-" * 40)
        result2 = self.send_chat_message("你好，请介绍一下自己", network=False)
        if result2:
            print(f"✅ 普通聊天成功，AI回复长度: {len(result2['ai_response'])}")
        else:
            print("❌ 普通聊天失败")
            return False
        
        time.sleep(2)
        
        print("\n📝 测试场景3: Retry操作 - 启用联网")
        print("-" * 40)
        if ai_message_id:
            result3 = self.retry_message(ai_message_id, network=True)
            if result3:
                print(f"✅ 联网重试成功，AI回复长度: {len(result3['ai_response'])}")
            else:
                print("❌ 联网重试失败")
        
        time.sleep(2)
        
        print("\n📝 测试场景4: Retry操作 - 不启用联网")
        print("-" * 40)
        if ai_message_id:
            result4 = self.retry_message(ai_message_id, network=False)
            if result4:
                print(f"✅ 普通重试成功，AI回复长度: {len(result4['ai_response'])}")
            else:
                print("❌ 普通重试失败")
        
        time.sleep(2)
        
        print("\n📝 测试场景5: Edit-resend操作 - 启用联网")
        print("-" * 40)
        if user_message_id:
            result5 = self.edit_resend_message(user_message_id, "请告诉我明天的天气预报", network=True)
            if result5:
                print(f"✅ 联网编辑重发成功，AI回复长度: {len(result5['ai_response'])}")
            else:
                print("❌ 联网编辑重发失败")
        
        time.sleep(2)
        
        print("\n📝 测试场景6: Edit-resend操作 - 不启用联网")
        print("-" * 40)
        if user_message_id:
            result6 = self.edit_resend_message(user_message_id, "请简单介绍一下人工智能", network=False)
            if result6:
                print(f"✅ 普通编辑重发成功，AI回复长度: {len(result6['ai_response'])}")
            else:
                print("❌ 普通编辑重发失败")
        
        time.sleep(2)
        
        print("\n📊 数据一致性检查")
        print("-" * 40)
        self.check_database_consistency()
        
        print("\n🎉 测试完成")
        print("=" * 60)
        return True

def main():
    """主函数"""
    if len(sys.argv) > 1:
        base_url = sys.argv[1]
    else:
        base_url = "http://localhost:8080/api"
    
    tester = NetworkFunctionalityTester(base_url)
    
    # 等待服务启动
    print("⏳ 等待服务启动...")
    for i in range(30):
        try:
            response = requests.get(f"{base_url}/actuator/health", timeout=5)
            if response.status_code == 200:
                print("✅ 服务已启动")
                break
        except:
            pass
        time.sleep(2)
        print(f"   等待中... ({i+1}/30)")
    else:
        print("❌ 服务启动超时")
        return False
    
    # 运行测试
    return tester.run_comprehensive_test()

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
