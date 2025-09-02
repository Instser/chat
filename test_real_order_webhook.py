#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
使用真实订单号测试Stripe Webhook
"""

import requests
import json
import time
import hashlib
import hmac

# 配置
WEBHOOK_URL = "http://localhost:8080/api/payment/stripe/webhook"
WEBHOOK_SECRET = "whsec_your_webhook_secret"

def test_real_order_webhook():
    # 使用真实订单号的支付成功事件
    payload = {
        "id": "evt_3S2yEIRWJJRQKPQG06YRtOEf",
        "object": "event",
        "api_version": "2024-06-20", 
        "created": int(time.time()),
        "data": {
            "object": {
                "id": "pi_3S2yEIRWJJRQKPQG0hUwZKwH",
                "object": "payment_intent",
                "amount": 1000,
                "currency": "usd",
                "status": "succeeded",
                "metadata": {
                    "order_id": "JY202509030142473977"
                }
            }
        },
        "livemode": False,
        "pending_webhooks": 1,
        "request": {
            "id": "req_real_request",
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

    print("🧪 测试真实订单Webhook处理...")
    print(f"📦 订单号: JY202509030142473977")
    print(f"🔐 签名: {stripe_signature}")

    try:
        response = requests.post(WEBHOOK_URL, data=payload_str, headers=headers, timeout=10)
        print(f"📊 响应状态码: {response.status_code}")
        print(f"📋 响应内容: {response.text}")
        
        if response.status_code == 200:
            print("✅ Webhook请求成功")
            print("💡 请检查后端日志和数据库订单状态")
        else:
            print("❌ Webhook请求失败")
            
    except Exception as e:
        print(f"❌ 请求失败: {e}")

if __name__ == "__main__":
    test_real_order_webhook()