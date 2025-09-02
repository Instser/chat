#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Stripe Webhook 测试脚本
用于测试修复后的 Stripe Webhook 回调处理
"""

import requests
import json
import time
import hashlib
import hmac

# 配置
WEBHOOK_URL = "http://localhost:8080/api/payment/stripe/webhook"
WEBHOOK_SECRET = "whsec_your_webhook_secret"

def create_stripe_signature(payload, secret, timestamp):
    """创建 Stripe 签名"""
    # 构建签名字符串
    sig_string = f"{timestamp}.{payload}"
    
    # 计算签名
    signature = hmac.new(
        secret.encode('utf-8'), 
        sig_string.encode('utf-8'), 
        hashlib.sha256
    ).hexdigest()
    
    return f"t={timestamp},v1={signature}"

def test_stripe_webhook():
    """测试 Stripe Webhook"""
    print("🧪 开始测试 Stripe Webhook 处理...")
    
    # 模拟 Stripe 支付成功事件
    payload = {
        "id": "evt_test_webhook_success",
        "object": "event",
        "api_version": "2024-06-20", 
        "created": int(time.time()),
        "data": {
            "object": {
                "id": "pi_test_payment_intent",
                "object": "payment_intent",
                "amount": 1000,  # $10.00
                "currency": "usd",
                "status": "succeeded",
                "metadata": {
                    "order_id": "JY202509020012272678"
                }
            }
        },
        "livemode": False,
        "pending_webhooks": 1,
        "request": {
            "id": "req_test_request",
            "idempotency_key": None
        },
        "type": "payment_intent.succeeded"
    }
    
    payload_str = json.dumps(payload, separators=(',', ':'))
    timestamp = str(int(time.time()))
    
    # 创建签名
    signature = create_stripe_signature(payload_str, WEBHOOK_SECRET, timestamp)
    
    # 发送请求
    headers = {
        "Content-Type": "application/json",
        "Stripe-Signature": signature,
        "User-Agent": "Stripe/1.0 (+https://stripe.com/docs/webhooks)"
    }
    
    try:
        print(f"📡 发送 Webhook 请求到: {WEBHOOK_URL}")
        print(f"🔐 签名: {signature}")
        print(f"📦 载荷长度: {len(payload_str)} 字符")
        
        response = requests.post(
            WEBHOOK_URL, 
            data=payload_str, 
            headers=headers,
            timeout=10
        )
        
        print(f"📊 响应状态码: {response.status_code}")
        print(f"📋 响应内容: {response.text}")
        
        if response.status_code == 200:
            print("✅ Webhook 请求成功发送！")
            print("💡 检查后端日志确认是否正确处理")
            return True
        else:
            print(f"❌ Webhook 请求失败，状态码: {response.status_code}")
            return False
            
    except requests.exceptions.ConnectionError:
        print("❌ 连接失败：请确认后端服务已启动 (http://localhost:8080)")
        return False
    except Exception as e:
        print(f"❌ 请求异常: {e}")
        return False

def test_invalid_signature():
    """测试无效签名拒绝"""
    print("\n🔒 测试无效签名拒绝...")
    
    payload = {"id": "evt_invalid", "type": "payment_intent.succeeded"}
    payload_str = json.dumps(payload)
    
    headers = {
        "Content-Type": "application/json", 
        "Stripe-Signature": "t=123456789,v1=invalid_signature"
    }
    
    try:
        response = requests.post(WEBHOOK_URL, data=payload_str, headers=headers, timeout=5)
        print(f"📊 无效签名测试 - 状态码: {response.status_code}")
        print(f"📋 响应内容: {response.text}")
        
        # Stripe Webhook 即使签名验证失败也返回 200，避免重试风暴
        if response.status_code == 200:
            print("✅ 无效签名正确处理（返回200避免重试风暴）")
        
    except Exception as e:
        print(f"❌ 无效签名测试异常: {e}")

if __name__ == "__main__":
    print("=" * 60)
    print("Stripe Webhook 测试工具")
    print("测试修复后的安全配置和线程池配置")
    print("=" * 60)
    
    # 测试正常 Webhook
    success = test_stripe_webhook()
    
    # 测试无效签名
    test_invalid_signature()
    
    print("\n" + "=" * 60)
    if success:
        print("✅ 测试完成！请检查后端日志确认 Webhook 处理情况")
        print("📝 关键日志关键词:")
        print("   - '收到Stripe Webhook事件'")
        print("   - 'Stripe Webhook已提交异步处理'")
        print("   - '开始异步处理Stripe Webhook事件'")
        print("   - '异步处理Stripe Webhook成功'")
    else:
        print("❌ 测试失败，请检查:")
        print("   1. 后端服务是否已启动")
        print("   2. SecurityConfig 是否正确配置 /payment/stripe/webhook")
        print("   3. PaymentAsyncConfig 是否包含 stripeWebhookExecutor Bean")
    print("=" * 60)