#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PayPal支付成功后续逻辑测试脚本
模拟PayPal Webhook回调，测试支付成功后的订单状态更新和套餐激活

使用方法:
python paypal_success_test.py

需要先启动后端应用: mvn spring-boot:run
"""

import requests
import time
import json
import sys
import io
from typing import Optional, Dict, Any
from datetime import datetime

# 设置标准输出编码为UTF-8以支持中文和emoji
if sys.platform.startswith('win'):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

class PayPalSuccessFlowTester:
    def __init__(self, base_url: str = "http://localhost:8080"):
        self.base_url = base_url
        self.api_base = f"{base_url}/api"
        self.session = requests.Session()
        self.session.headers.update({
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        })
        
    def log(self, message: str, level: str = "INFO"):
        """输出日志，兼容Windows中文环境"""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        try:
            print(f"[{timestamp}] {level}: {message}")
        except UnicodeEncodeError:
            safe_message = message.encode('ascii', 'ignore').decode('ascii')
            print(f"[{timestamp}] {level}: {safe_message}")
        except Exception as e:
            print(f"[{timestamp}] {level}: [消息显示错误: {str(e)}]")
    
    def login(self, username_or_email: str, password: str) -> bool:
        """用户登录获取认证"""
        self.log(f"[登录] 开始登录，用户: {username_or_email}")
        
        url = f"{self.api_base}/auth/login"
        data = {
            "usernameOrEmail": username_or_email,
            "password": password
        }
        
        try:
            response = self.session.post(url, json=data)
            self.log(f"登录响应状态: {response.status_code}")
            
            if response.status_code == 200:
                result = response.json()
                
                if result.get('code') == 200:
                    # 检查是否设置了认证cookie
                    cookies = response.cookies
                    auth_cookie_found = False
                    for cookie in cookies:
                        if cookie.name in ['auth-token', 'token', 'jwt', 'Authorization']:
                            auth_cookie_found = True
                            self.log(f"检测到认证Cookie: {cookie.name}")
                            break
                    
                    if auth_cookie_found:
                        self.log("[成功] 登录成功，认证信息已通过Cookie设置")
                        return True
                    else:
                        self.log("[警告] 登录成功但未发现认证Cookie")
                        return True
                else:
                    self.log(f"[失败] 登录失败: {result.get('message', '未知错误')}")
                    return False
            else:
                self.log(f"[失败] 登录请求失败: {response.status_code} - {response.text}")
                return False
                
        except Exception as e:
            self.log(f"[异常] 登录异常: {e}", "ERROR")
            return False
    
    def create_paypal_payment(self, package_id: int, currency: str = "USD") -> Dict[str, Any]:
        """创建PayPal支付订单"""
        self.log(f"[支付] 创建PayPal支付订单 - 套餐ID: {package_id}, 货币: {currency}")
        
        url = f"{self.api_base}/payment/create"
        data = {
            "packageId": package_id,
            "currency": currency,
            "paymentMethod": "paypal"
        }
        
        try:
            response = self.session.post(url, json=data)
            self.log(f"创建支付响应状态: {response.status_code}")
            
            if response.status_code == 200:
                result = response.json()
                
                if result.get('code') == 200:
                    self.log("[成功] PayPal支付订单创建成功")
                    return result
                else:
                    data = result.get('data', {})
                    if isinstance(data, dict) and data.get('code') == 7001:
                        raise Exception(f"PayPal服务内部错误: {data.get('message', '未知错误')}")
                    else:
                        raise Exception(f"创建支付失败: {result.get('message', '未知错误')}")
            else:
                raise Exception(f"创建支付请求失败: {response.status_code} - {response.text}")
                
        except Exception as e:
            self.log(f"[异常] 创建支付异常: {e}", "ERROR")
            raise
    
    def check_payment_status(self, order_no: str) -> Dict[str, Any]:
        """查询订单支付状态"""
        self.log(f"[查询] 查询订单支付状态 - 订单号: {order_no}")
        
        url = f"{self.api_base}/payment/status/{order_no}"
        
        try:
            response = self.session.get(url)
            self.log(f"查询状态响应状态: {response.status_code}")
            
            if response.status_code == 200:
                result = response.json()
                self.log(f"查询状态响应: {json.dumps(result, indent=2, ensure_ascii=False)}")
                return result
            else:
                raise Exception(f"查询状态请求失败: {response.status_code} - {response.text}")
                
        except Exception as e:
            self.log(f"[异常] 查询状态异常: {e}", "ERROR")
            raise
    
    def simulate_paypal_webhook(self, paypal_order_id: str, order_no: str, status: str = "COMPLETED") -> bool:
        """模拟PayPal Webhook回调"""
        self.log(f"[Webhook] 模拟PayPal Webhook回调 - PayPal订单ID: {paypal_order_id}, 状态: {status}")
        
        # 构建PayPal Webhook事件数据
        webhook_data = {
            "id": f"WH-{int(time.time())}-{paypal_order_id}",
            "event_version": "1.0",
            "create_time": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S.%fZ"),
            "resource_type": "checkout-order",
            "event_type": "CHECKOUT.ORDER.APPROVED" if status == "COMPLETED" else "CHECKOUT.ORDER.CANCELLED",
            "summary": f"An order has been {status.lower()}.",
            "resource": {
                "id": paypal_order_id,
                "intent": "CAPTURE",
                "status": status,
                "purchase_units": [
                    {
                        "reference_id": order_no,
                        "amount": {
                            "currency_code": "USD",
                            "value": "5.00"
                        }
                    }
                ],
                "payer": {
                    "name": {
                        "given_name": "Test",
                        "surname": "User"
                    },
                    "email_address": "test@example.com"
                },
                "create_time": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S.%fZ"),
                "update_time": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S.%fZ"),
                "links": []
            },
            "links": []
        }
        
        url = f"{self.api_base}/payment/paypal/webhook"
        
        try:
            # 模拟PayPal的请求头
            headers = {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'paypal-transmission-id': f"test-transmission-{int(time.time())}",
                'paypal-cert-id': 'test-cert-id',
                'paypal-transmission-time': datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S.%fZ"),
                'paypal-transmission-sig': 'test-signature',
                'paypal-auth-algo': 'SHA256withRSA'
            }
            
            response = requests.post(url, json=webhook_data, headers=headers)
            self.log(f"Webhook响应状态: {response.status_code}")
            self.log(f"Webhook响应内容: {response.text}")
            
            if response.status_code == 200 and response.text == "success":
                self.log("[成功] PayPal Webhook回调处理成功")
                return True
            else:
                self.log(f"[失败] PayPal Webhook回调处理失败: {response.status_code} - {response.text}")
                return False
                
        except Exception as e:
            self.log(f"[异常] 模拟Webhook回调异常: {e}", "ERROR")
            return False
    
    def check_user_package_activation(self) -> Dict[str, Any]:
        """检查用户套餐激活状态"""
        self.log("[检查] 查询用户信息和套餐激活状态")
        
        url = f"{self.api_base}/auth/me"
        
        try:
            response = self.session.get(url)
            self.log(f"用户信息查询状态: {response.status_code}")
            
            if response.status_code == 200:
                result = response.json()
                self.log(f"用户信息: {json.dumps(result, indent=2, ensure_ascii=False)}")
                return result
            else:
                self.log(f"[失败] 查询用户信息失败: {response.status_code} - {response.text}")
                return {}
                
        except Exception as e:
            self.log(f"[异常] 查询用户信息异常: {e}", "ERROR")
            return {}
    
    def run_complete_success_flow_test(self) -> bool:
        """运行完整的支付成功流程测试"""
        self.log("=" * 60)
        self.log("[测试] 开始PayPal支付成功完整流程测试")
        self.log("=" * 60)
        
        try:
            # 1. 用户登录
            username = "Instser"
            password = "cgbz..6757"
            
            if not self.login(username, password):
                self.log("[失败] 用户登录失败，无法继续测试")
                return False
            
            # 2. 创建PayPal支付订单
            package_id = 3
            currency = "USD"
            
            payment_result = self.create_paypal_payment(package_id, currency)
            data = payment_result.get('data', {})
            order_no = data.get('orderNo')
            paypal_order_id = data.get('paypalOrderId')
            
            if not order_no or not paypal_order_id:
                self.log("[失败] 创建支付订单失败，缺少关键信息")
                return False
            
            self.log(f"[信息] 订单创建成功 - 订单号: {order_no}, PayPal订单ID: {paypal_order_id}")
            
            # 3. 查询初始订单状态
            initial_status = self.check_payment_status(order_no)
            initial_payment_status = initial_status.get('data', {}).get('status', -1)
            self.log(f"[状态] 初始订单状态: {initial_payment_status} (0=待支付, 1=已支付, 2=已取消, 3=已退款)")
            
            # 4. 查询用户初始套餐状态
            initial_user_info = self.check_user_package_activation()
            initial_package_id = initial_user_info.get('data', {}).get('packageId')
            initial_package_name = initial_user_info.get('data', {}).get('packageName')
            self.log(f"[状态] 初始用户套餐: ID={initial_package_id}, 名称={initial_package_name}")
            
            # 5. 模拟PayPal支付成功Webhook
            self.log("=" * 40)
            self.log("[关键] 模拟PayPal支付成功，发送Webhook回调")
            self.log("=" * 40)
            
            webhook_success = self.simulate_paypal_webhook(paypal_order_id, order_no, "COMPLETED")
            if not webhook_success:
                self.log("[失败] PayPal Webhook回调模拟失败")
                return False
            
            # 6. 等待异步处理完成
            self.log("[等待] 等待后端异步处理完成...")
            time.sleep(3)
            
            # 7. 查询更新后的订单状态
            updated_status = self.check_payment_status(order_no)
            updated_payment_status = updated_status.get('data', {}).get('status', -1)
            status_message = updated_status.get('data', {}).get('message', '未知')
            self.log(f"[结果] 更新后订单状态: {updated_payment_status} ({status_message})")
            
            # 8. 查询用户套餐激活状态
            updated_user_info = self.check_user_package_activation()
            updated_package_id = updated_user_info.get('data', {}).get('packageId')
            updated_package_name = updated_user_info.get('data', {}).get('packageName')
            self.log(f"[结果] 更新后用户套餐: ID={updated_package_id}, 名称={updated_package_name}")
            
            # 9. 分析测试结果
            self.log("=" * 60)
            self.log("[分析] 支付成功流程测试结果分析")
            self.log("=" * 60)
            
            success = True
            
            # 检查订单状态是否更新
            if updated_payment_status == 1:
                self.log("✅ 订单状态更新成功: 待支付 -> 已支付")
            else:
                self.log(f"❌ 订单状态更新失败: 期望=1(已支付), 实际={updated_payment_status}")
                success = False
            
            # 检查套餐是否激活
            if updated_package_id == package_id:
                self.log(f"✅ 套餐激活成功: 用户已绑定套餐ID={package_id}")
            else:
                self.log(f"❌ 套餐激活失败: 期望套餐ID={package_id}, 实际={updated_package_id}")
                success = False
            
            if updated_package_name:
                self.log(f"✅ 套餐信息完整: 套餐名称={updated_package_name}")
            else:
                self.log("❌ 套餐信息缺失: 套餐名称为空")
                success = False
            
            # 最终结果
            if success:
                self.log("=" * 60)
                self.log("🎉 [成功] PayPal支付成功完整流程测试通过!")
                self.log("   • 订单状态正确更新为已支付")
                self.log(f"   • 用户套餐成功激活 (ID={package_id}, 名称={updated_package_name})")
                self.log("   • 支付成功后续逻辑工作正常")
                self.log("=" * 60)
            else:
                self.log("=" * 60)
                self.log("❌ [失败] PayPal支付成功完整流程测试失败!")
                self.log("   请检查后端Webhook处理逻辑和套餐激活服务")
                self.log("=" * 60)
            
            return success
            
        except Exception as e:
            self.log(f"[异常] 支付成功流程测试异常: {e}", "ERROR")
            return False
    
    def run_quick_status_test(self, order_no: str) -> bool:
        """快速测试现有订单的状态"""
        self.log("=" * 60)
        self.log(f"[测试] 快速测试订单状态 - 订单号: {order_no}")
        self.log("=" * 60)
        
        try:
            # 登录
            if not self.login("Instser", "cgbz..6757"):
                return False
            
            # 查询订单状态
            status_result = self.check_payment_status(order_no)
            if status_result.get('code') == 200:
                data = status_result.get('data', {})
                status = data.get('status')
                message = data.get('message', '未知状态')
                self.log(f"[结果] 订单状态: {status} ({message})")
                return True
            else:
                self.log("[失败] 查询订单状态失败")
                return False
                
        except Exception as e:
            self.log(f"[异常] 快速状态测试异常: {e}", "ERROR")
            return False

def main():
    """主函数"""
    print("PayPal支付成功后续逻辑测试脚本")
    print("作者: Claude Code")
    print("用途: 测试PayPal支付成功后的订单状态更新和套餐激活逻辑")
    print()
    
    # 创建测试实例
    tester = PayPalSuccessFlowTester()
    
    try:
        # 运行完整的支付成功流程测试
        success = tester.run_complete_success_flow_test()
        
        if success:
            print("\n[总结] 测试完成！PayPal支付成功后续逻辑正常工作")
        else:
            print("\n[总结] 测试失败！PayPal支付成功后续逻辑存在问题")
            
    except KeyboardInterrupt:
        print("\n\n[中断] 测试被用户中断")
    except Exception as e:
        print(f"\n[异常] 测试过程中发生异常: {e}")
    
    print("\n感谢使用PayPal支付成功逻辑测试脚本！")

if __name__ == "__main__":
    main()