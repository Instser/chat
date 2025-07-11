#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import requests
import json
import time

def test_network_chat():
    """测试联网聊天功能"""
    
    # 使用session来保持cookies
    session = requests.Session()
    
    # 登录获取认证
    login_url = "http://localhost:8080/api/auth/login"
    login_data = {
        "usernameOrEmail": "admin",
        "password": "admin123"
    }
    
    print("=== 开始登录 ===")
    login_response = session.post(login_url, json=login_data)
    print(f"登录状态码: {login_response.status_code}")
    
    if login_response.status_code != 200:
        print(f"登录失败: {login_response.text}")
        return
    
    print("认证成功")
    
    # 创建会话
    session_url = "http://localhost:8080/api/chats"
    session_data = {"title": "联网功能测试", "modelId": 1}
    
    print("\n=== 创建会话 ===")
    session_response = session.post(session_url, json=session_data)
    
    if session_response.status_code != 200:
        print(f"创建会话失败: {session_response.text}")
        return
    
    session_info = session_response.json()
    session_id = session_info['data']['id']
    print(f"会话创建成功，ID: {session_id}")
    
    # 发送联网聊天请求
    chat_url = "http://localhost:8080/api/chat"
    chat_data = {
        "sessionId": session_id,
        "messages": [
            {
                "role": "user",
                "content": "今天北京的天气怎么样？"
            }
        ],
        "modelId": 1,
        "network": True  # 启用联网搜索
    }
    
    print("\n=== 发送联网聊天请求 ===")
    print(f"请求数据: {json.dumps(chat_data, ensure_ascii=False, indent=2)}")
    
    chat_response = session.post(chat_url, json=chat_data, stream=True)
    
    print(f"响应状态码: {chat_response.status_code}")
    print("响应头:")
    for key, value in chat_response.headers.items():
        print(f"  {key}: {value}")
    
    if chat_response.status_code != 200:
        print(f"聊天请求失败: {chat_response.text}")
        return
    
    print("\n=== 流式响应内容 ===")
    ai_content = ""
    network_results = []
    
    for line in chat_response.iter_lines(decode_unicode=True):
        if line:
            print(f"收到: {line}")
            
            # 解析AI内容
            if line.startswith('0:'):
                try:
                    content = json.loads(line[2:])
                    ai_content += content
                    print(f"AI内容: {content}")
                except:
                    pass
            
            # 解析联网搜索结果
            elif line.startswith('u:'):
                try:
                    result = json.loads(line[2:])
                    network_results.append(result)
                    print(f"联网结果: {result}")
                except:
                    pass
            
            # 解析完成标记
            elif line.startswith('d:'):
                try:
                    completion = json.loads(line[2:])
                    print(f"完成标记: {completion}")
                except:
                    pass
    
    print(f"\n=== 测试结果汇总 ===")
    print(f"AI回复长度: {len(ai_content)}")
    print(f"联网结果数量: {len(network_results)}")
    print(f"AI回复内容: {ai_content[:100]}...")
    
    if network_results:
        print("联网搜索结果:")
        for i, result in enumerate(network_results[:3]):  # 只显示前3个
            print(f"  {i+1}. {result.get('title', 'N/A')}")
            print(f"     URL: {result.get('url', 'N/A')}")
            print(f"     内容: {result.get('content', 'N/A')[:50]}...")
    
    # 检查数据库中的联网标记
    print("\n=== 检查数据库联网标记 ===")
    messages_url = f"http://localhost:8080/api/chat/messages/{session_id}"
    messages_response = session.get(messages_url)
    
    if messages_response.status_code == 200:
        messages_data = messages_response.json()
        messages = messages_data.get('data', {}).get('messages', [])
        
        for msg in messages:
            if msg.get('role') == 'user' and '天气' in msg.get('content', ''):
                print(f"用户消息ID: {msg.get('id')}")
                print(f"联网标记: {msg.get('hasWebSearch', 'N/A')}")
                break
    
    return {
        'ai_content_length': len(ai_content),
        'network_results_count': len(network_results),
        'session_id': session_id
    }

if __name__ == "__main__":
    result = test_network_chat()
    print(f"\n最终结果: {result}")
