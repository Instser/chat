#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试 Stripe Webhook 连接性
"""

import requests
import json

def test_backend_connectivity():
    """测试后端服务连接性"""
    print("🔍 测试后端服务连接性...")
    
    # 测试健康检查端点
    try:
        response = requests.get("http://localhost:8080/actuator/health", timeout=5)
        print(f"✅ 健康检查响应: {response.status_code}")
        print(f"📋 响应内容: {response.text}")
        return True
    except requests.exceptions.ConnectionError:
        print("❌ 后端服务未启动或无法连接 (http://localhost:8080)")
        return False
    except Exception as e:
        print(f"❌ 健康检查异常: {e}")
        return False

def test_webhook_endpoint():
    """测试 Webhook 端点可访问性"""
    print("\n🎯 测试 Webhook 端点...")
    
    webhook_url = "http://localhost:8080/api/payment/stripe/webhook"
    
    # 发送简单 POST 请求测试端点是否可访问
    try:
        response = requests.post(
            webhook_url, 
            data='{"test": "connectivity"}',
            headers={"Content-Type": "application/json"},
            timeout=5
        )
        print(f"📊 Webhook 端点响应: {response.status_code}")
        print(f"📋 响应内容: {response.text}")
        
        if response.status_code == 200:
            print("✅ Webhook 端点可访问")
            return True
        else:
            print(f"⚠️  Webhook 端点返回非200状态码: {response.status_code}")
            return False
            
    except requests.exceptions.ConnectionError:
        print("❌ Webhook 端点无法连接")
        return False
    except Exception as e:
        print(f"❌ Webhook 端点测试异常: {e}")
        return False

def main():
    print("=" * 60)
    print("Stripe Webhook 连接性诊断工具")
    print("=" * 60)
    
    # 测试后端服务
    backend_ok = test_backend_connectivity()
    
    if not backend_ok:
        print("\n❌ 诊断结果: 后端服务未运行或无法访问")
        print("💡 解决方案:")
        print("   1. 启动后端服务: mvn spring-boot:run")
        print("   2. 确认服务运行在端口 8080")
        return
    
    # 测试 Webhook 端点
    webhook_ok = test_webhook_endpoint()
    
    print("\n" + "=" * 60)
    print("📊 诊断结果:")
    print(f"   后端服务: {'✅ 正常' if backend_ok else '❌ 异常'}")
    print(f"   Webhook端点: {'✅ 可访问' if webhook_ok else '❌ 无法访问'}")
    
    if backend_ok and webhook_ok:
        print("\n✅ 服务正常，Stripe CLI 应该能正常转发 Webhook")
        print("💡 检查项目:")
        print("   1. Stripe CLI 转发地址是否正确: http://localhost:8080/api/payment/stripe/webhook")
        print("   2. 查看 Stripe CLI 是否有错误消息")
        print("   3. 查看后端日志是否有 Webhook 处理记录")
    else:
        print("\n❌ 发现问题，需要解决后才能正常接收 Webhook")
    
    print("=" * 60)

if __name__ == "__main__":
    main()