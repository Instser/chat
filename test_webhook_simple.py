#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
简单的Stripe Webhook测试
"""

import requests
import json
import time
import hashlib
import hmac

# 配置
WEBHOOK_URL = "http://localhost:8080/api/payment/stripe/webhook"
WEBHOOK_SECRET = "whsec_your_webhook_secret"

def test_webhook():
    # 模拟有效的Stripe支付成功事件
    payload = {
        "id": "evt_test_webhook_success",
        "object": "event",
        "api_version": "2024-06-20", 
        "created": int(time.time()),
        "data": {
            "object": {
                "id": "pi_test_payment_intent",
                "object": "payment_intent",
                "amount": 1000,
                "currency": "usd",
                "status": "succeeded",
                "metadata": {
                    "order_id": "JY202509030001"
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

    # 创建正确的签名
    sig_string = f"{timestamp}.{payload_str}"
    signature = hmac.new(
        WEBHOOK_SECRET.encode('utf-8'), 
        sig_string.encode('utf-8'), 
        hashlib.sha256
    ).hexdigest()
    stripe_signature = f"t={timestamp},v1={signature}"

    headers = {
        "Content-Type": "application/json",
        "Stripe-Signature": stripe_signature,
        "User-Agent": "Stripe/1.0 (+https://stripe.com/docs/webhooks)"
    }

    print("🧪 发送测试Webhook请求...")
    print(f"📦 载荷长度: {len(payload_str)} 字符")
    print(f"🔐 签名: {stripe_signature}")

    try:
        response = requests.post(WEBHOOK_URL, data=payload_str, headers=headers, timeout=10)
        print(f"📊 响应状态码: {response.status_code}")
        print(f"📋 响应内容: {response.text}")
        
        if response.status_code == 200:
            print("✅ Webhook请求成功")
            print("💡 请检查后端日志以确认处理情况")
            print("📝 需要查找的关键日志:")
            print("   - '收到Stripe Webhook事件'")
            print("   - '开始异步处理Stripe Webhook事件'")
            print("   - '异步处理Stripe Webhook成功'")
        else:
            print("❌ Webhook请求失败")
            
    except Exception as e:
        print(f"❌ 请求失败: {e}")

if __name__ == "__main__":
    test_webhook()