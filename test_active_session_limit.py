#!/usr/bin/env python3
"""
测试用户活跃对话限制功能
模拟HTTP请求来测试聊天API的活跃会话限制
"""

import requests
import json
import time
import threading
from datetime import datetime

# 配置
BASE_URL = "http://localhost:8080"
CHAT_API_URL = f"{BASE_URL}/api/chat"
TEST_USER_ID = 1
TEST_MODEL_ID = 1

# 测试用的JWT Token（需要根据实际情况调整）
TEST_TOKEN = "Bearer your-jwt-token"

def get_headers():
    """获取请求头"""
    return {
        "Content-Type": "application/json",
        "Authorization": TEST_TOKEN,
        "Accept": "text/event-stream"
    }

def send_chat_request(user_id, message, model_id=1):
    """发送聊天请求"""
    payload = {
        "userId": user_id,
        "message": message,
        "modelId": model_id
    }
    
    try:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] 发送聊天请求: {message[:30]}...")
        response = requests.post(
            CHAT_API_URL,
            json=payload,
            headers=get_headers(),
            stream=True,
            timeout=30
        )
        
        print(f"[{datetime.now().strftime('%H:%M:%S')}] 响应状态码: {response.status_code}")
        
        if response.status_code == 200:
            # 读取流式响应
            for line in response.iter_lines():
                if line:
                    decoded_line = line.decode('utf-8')
                    if decoded_line.startswith('data: '):
                        data = decoded_line[6:]  # 移除 'data: ' 前缀
                        if data.strip() == '[DONE]':
                            print(f"[{datetime.now().strftime('%H:%M:%S')}] 聊天完成")
                            break
                        elif data.strip():
                            try:
                                json_data = json.loads(data)
                                if 'content' in json_data:
                                    print(f"[{datetime.now().strftime('%H:%M:%S')}] 收到内容: {json_data['content'][:50]}...")
                            except json.JSONDecodeError:
                                pass
        else:
            # 读取错误响应
            error_text = response.text
            print(f"[{datetime.now().strftime('%H:%M:%S')}] 错误响应: {error_text}")
            return False, error_text
            
        return True, "成功"
        
    except requests.exceptions.RequestException as e:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] 请求异常: {str(e)}")
        return False, str(e)

def test_normal_chat():
    """测试正常聊天流程"""
    print("\n=== 测试1: 正常聊天流程 ===")
    success, message = send_chat_request(TEST_USER_ID, "你好，这是一个测试消息")
    if success:
        print("✅ 正常聊天测试通过")
    else:
        print(f"❌ 正常聊天测试失败: {message}")
    return success

def test_concurrent_chat_limit():
    """测试并发聊天限制"""
    print("\n=== 测试2: 并发聊天限制 ===")
    
    results = []
    
    def chat_thread(thread_id):
        success, message = send_chat_request(TEST_USER_ID, f"并发测试消息 {thread_id}")
        results.append((thread_id, success, message))
    
    # 创建两个并发线程
    thread1 = threading.Thread(target=chat_thread, args=(1,))
    thread2 = threading.Thread(target=chat_thread, args=(2,))
    
    # 几乎同时启动两个请求
    thread1.start()
    time.sleep(0.1)  # 稍微延迟第二个请求
    thread2.start()
    
    # 等待两个线程完成
    thread1.join()
    thread2.join()
    
    # 分析结果
    success_count = sum(1 for _, success, _ in results if success)
    failed_count = len(results) - success_count
    
    print(f"并发请求结果: 成功 {success_count} 个, 失败 {failed_count} 个")
    
    for thread_id, success, message in results:
        status = "✅" if success else "❌"
        print(f"  线程{thread_id}: {status} {message}")
    
    # 检查是否有一个成功，一个因为活跃会话限制而失败
    has_active_session_error = any("Currently chatting" in message for _, success, message in results if not success)
    
    if success_count == 1 and failed_count == 1 and has_active_session_error:
        print("✅ 并发聊天限制测试通过")
        return True
    else:
        print("❌ 并发聊天限制测试失败")
        return False

def test_different_users():
    """测试不同用户的隔离"""
    print("\n=== 测试3: 不同用户隔离 ===")
    
    results = []
    
    def chat_thread(user_id):
        success, message = send_chat_request(user_id, f"用户{user_id}的测试消息")
        results.append((user_id, success, message))
    
    # 创建两个不同用户的并发请求
    thread1 = threading.Thread(target=chat_thread, args=(1,))
    thread2 = threading.Thread(target=chat_thread, args=(2,))
    
    thread1.start()
    thread2.start()
    
    thread1.join()
    thread2.join()
    
    # 分析结果
    success_count = sum(1 for _, success, _ in results if success)
    
    print(f"不同用户请求结果: 成功 {success_count} 个")
    
    for user_id, success, message in results:
        status = "✅" if success else "❌"
        print(f"  用户{user_id}: {status} {message}")
    
    if success_count == 2:
        print("✅ 不同用户隔离测试通过")
        return True
    else:
        print("❌ 不同用户隔离测试失败")
        return False

def check_server_status():
    """检查服务器状态"""
    try:
        response = requests.get(f"{BASE_URL}/actuator/health", timeout=5)
        if response.status_code == 200:
            print("✅ 服务器运行正常")
            return True
    except:
        pass
    
    print("❌ 服务器未运行或无法访问")
    return False

def main():
    """主测试函数"""
    print("开始测试用户活跃对话限制功能")
    print(f"测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"服务器地址: {BASE_URL}")
    print(f"测试用户ID: {TEST_USER_ID}")
    
    # 检查服务器状态
    if not check_server_status():
        print("\n请先启动服务器，然后重新运行测试")
        return
    
    # 运行测试
    test_results = []
    
    # 测试1: 正常聊天
    test_results.append(("正常聊天流程", test_normal_chat()))
    
    # 等待一段时间确保第一个会话完成
    print("\n等待5秒确保会话完成...")
    time.sleep(5)
    
    # 测试2: 并发聊天限制
    test_results.append(("并发聊天限制", test_concurrent_chat_limit()))
    
    # 等待一段时间
    print("\n等待5秒...")
    time.sleep(5)
    
    # 测试3: 不同用户隔离
    test_results.append(("不同用户隔离", test_different_users()))
    
    # 输出测试总结
    print("\n" + "="*50)
    print("测试总结:")
    passed = 0
    total = len(test_results)
    
    for test_name, result in test_results:
        status = "✅ 通过" if result else "❌ 失败"
        print(f"  {test_name}: {status}")
        if result:
            passed += 1
    
    print(f"\n总体结果: {passed}/{total} 个测试通过")
    
    if passed == total:
        print("🎉 所有测试都通过了！用户活跃对话限制功能工作正常。")
    else:
        print("⚠️  部分测试失败，请检查实现。")

if __name__ == "__main__":
    main()
