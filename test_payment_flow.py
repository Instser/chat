#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
支付模块完整流程测试脚本
测试支付成功后是否正确更新支付状态并更新用户套餐

作者: Augment Agent
创建时间: 2025-07-17 00:45
"""

import requests
import json
import time
import hashlib
import hmac
import urllib.parse
import random
from datetime import datetime
from typing import Dict, Any, Optional
import logging

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('payment_test.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class PaymentFlowTester:
    """支付流程测试器"""

    def __init__(self):
        self.base_url = "http://localhost:8080/api"
        self.session = requests.Session()
        self.session.headers.update({
            'Content-Type': 'application/json',
            'User-Agent': 'PaymentFlowTester/1.0'
        })

        # 测试配置
        self.test_config = {
            'username': 'admin',
            'password': 'admin123',
            'test_package_id': 3,  # 测试套餐ID - 使用Pro套餐
            'payment_method': 'alipay',
            'currency': 'USD',  # 使用USD货币
            'test_suffix': str(random.randint(1000, 9999))  # 随机后缀避免重复
        }

        # 易支付配置（用于模拟回调）
        self.yipay_config = {
            'pid': '1674',
            'key': 'your-yipay-merchant-key',
            'notify_url': f'{self.base_url}/payment/yipay/notify'
        }

        self.user_id = None
        self.auth_token = None
        
    def login(self) -> bool:
        """用户登录"""
        try:
            logger.info("=== 开始用户登录 ===")
            
            login_data = {
                'usernameOrEmail': self.test_config['username'],
                'password': self.test_config['password']
            }
            
            response = self.session.post(f"{self.base_url}/auth/login", json=login_data)
            
            if response.status_code == 200:
                result = response.json()
                logger.info(f"登录响应: {json.dumps(result, ensure_ascii=False, indent=2)}")

                if result.get('code') == 200:
                    data = result.get('data', {})
                    # 尝试不同的数据结构
                    if 'user' in data:
                        self.user_id = data.get('user', {}).get('id')
                    else:
                        self.user_id = data.get('id')

                    self.auth_token = data.get('token')

                    # 设置认证头
                    if self.auth_token:
                        self.session.headers.update({
                            'Authorization': f'Bearer {self.auth_token}'
                        })

                    logger.info(f"登录成功，用户ID: {self.user_id}, Token: {self.auth_token[:20] if self.auth_token else None}...")
                    return True
                else:
                    logger.error(f"登录失败: {result.get('message')}")
                    return False
            else:
                logger.error(f"登录请求失败: {response.status_code}")
                return False
                
        except Exception as e:
            logger.error(f"登录异常: {e}")
            return False
    
    def get_user_account_before(self) -> Optional[Dict]:
        """获取支付前的用户账户信息"""
        try:
            logger.info("=== 获取支付前用户账户信息 ===")
            
            response = self.session.get(f"{self.base_url}/auth/me")
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200:
                    data = result.get('data', {})
                    user_package = data.get('userPackage', {})
                    
                    account_info = {
                        'package_id': user_package.get('packageId'),
                        'package_name': user_package.get('packageName'),
                        'expire_time': user_package.get('packageExpireTime'),
                        'chat_remaining': user_package.get('chatCountRemaining'),
                        'total_amount': user_package.get('totalAmountSpent', 0)
                    }
                    
                    logger.info(f"支付前账户信息: {json.dumps(account_info, ensure_ascii=False, indent=2)}")
                    return account_info
                else:
                    logger.error(f"获取账户信息失败: {result.get('message')}")
                    return None
            else:
                logger.error(f"获取账户信息请求失败: {response.status_code}")
                return None
                
        except Exception as e:
            logger.error(f"获取账户信息异常: {e}")
            return None
    
    def get_package_info(self, package_id: int) -> Optional[Dict]:
        """获取套餐信息"""
        try:
            logger.info(f"=== 获取套餐信息 (ID: {package_id}) ===")
            
            response = self.session.get(f"{self.base_url}/payment/packages")
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200:
                    packages = result.get('data', [])
                    
                    logger.info(f"可用套餐列表: {json.dumps(packages, ensure_ascii=False, indent=2)}")

                    for package in packages:
                        if package.get('id') == package_id:  # 直接比较数字
                            logger.info(f"套餐信息: {json.dumps(package, ensure_ascii=False, indent=2)}")
                            return package

                    logger.error(f"未找到套餐ID: {package_id}")
                    logger.info("尝试使用第一个付费套餐...")
                    # 寻找第一个付费套餐
                    for package in packages:
                        if package.get('price', 0) > 0:
                            logger.info(f"使用付费套餐: {json.dumps(package, ensure_ascii=False, indent=2)}")
                            # 更新测试配置中的套餐ID
                            self.test_config['test_package_id'] = package.get('id')
                            return package
                    return None
                else:
                    logger.error(f"获取套餐列表失败: {result.get('message')}")
                    return None
            else:
                logger.error(f"获取套餐列表请求失败: {response.status_code}")
                return None
                
        except Exception as e:
            logger.error(f"获取套餐信息异常: {e}")
            return None
    
    def create_payment_order(self) -> Optional[str]:
        """创建支付订单"""
        try:
            logger.info("=== 创建支付订单 ===")
            
            payment_data = {
                'packageId': self.test_config['test_package_id'],
                'paymentMethod': self.test_config['payment_method'],
                'currency': self.test_config['currency']
            }
            
            response = self.session.post(f"{self.base_url}/payment/create", json=payment_data)
            
            if response.status_code == 200:
                result = response.json()
                logger.info(f"创建订单响应: {json.dumps(result, ensure_ascii=False, indent=2)}")

                if result.get('code') == 200:
                    data = result.get('data', {})
                    order_no = data.get('orderNo')
                    payment_url = data.get('paymentUrl') or data.get('qrCodeUrl')

                    logger.info(f"订单创建成功:")
                    logger.info(f"  订单号: {order_no}")
                    logger.info(f"  支付URL: {payment_url}")

                    if order_no:
                        return order_no
                    else:
                        logger.error("订单号为空")
                        return None
                else:
                    logger.error(f"创建订单失败: {result.get('message')}")
                    return None
            else:
                logger.error(f"创建订单请求失败: {response.status_code}")
                return None
                
        except Exception as e:
            logger.error(f"创建订单异常: {e}")
            return None
    
    def check_order_status(self, order_no: str) -> Optional[Dict]:
        """检查订单状态"""
        try:
            response = self.session.get(f"{self.base_url}/payment/status/{order_no}")
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200:
                    data = result.get('data', {})
                    status_info = {
                        'order_no': data.get('orderNo'),
                        'payment_status': data.get('paymentStatus'),
                        'payment_time': data.get('paymentTime'),
                        'amount': data.get('amount'),
                        'currency': data.get('currency')
                    }
                    return status_info
                else:
                    logger.error(f"查询订单状态失败: {result.get('message')}")
                    return None
            else:
                logger.error(f"查询订单状态请求失败: {response.status_code}")
                return None
                
        except Exception as e:
            logger.error(f"查询订单状态异常: {e}")
            return None
    
    def simulate_yipay_callback(self, order_no: str, amount: str) -> bool:
        """模拟易支付回调"""
        try:
            logger.info("=== 模拟易支付成功回调 ===")
            
            # 构造回调参数
            callback_params = {
                'pid': self.yipay_config['pid'],
                'trade_no': f'yipay_{int(time.time())}',  # 模拟易支付交易号
                'out_trade_no': order_no,
                'type': 'alipay',
                'name': '套餐购买',
                'money': amount,
                'trade_status': 'TRADE_SUCCESS'
            }
            
            # 生成签名
            sign_string = self._generate_yipay_sign(callback_params)
            callback_params['sign'] = sign_string
            callback_params['sign_type'] = 'MD5'
            
            logger.info(f"回调参数: {json.dumps(callback_params, ensure_ascii=False, indent=2)}")
            
            # 发送回调请求 - 使用localhost来绕过IP白名单验证
            local_notify_url = self.yipay_config['notify_url'].replace('api.example.com', 'localhost:8080')
            response = requests.post(
                local_notify_url,
                data=callback_params,
                headers={
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Forwarded-For': '127.0.0.1',  # 设置转发IP
                    'X-Real-IP': '127.0.0.1'  # 设置真实IP
                }
            )
            
            if response.status_code == 200:
                response_text = response.text.strip()
                logger.info(f"回调响应: {response_text}")
                
                if response_text == 'success':
                    logger.info("易支付回调处理成功")
                    return True
                else:
                    logger.error(f"易支付回调处理失败: {response_text}")
                    return False
            else:
                logger.error(f"易支付回调请求失败: {response.status_code}")
                return False
                
        except Exception as e:
            logger.error(f"模拟易支付回调异常: {e}")
            return False
    
    def _generate_yipay_sign(self, params: Dict[str, str]) -> str:
        """生成易支付签名"""
        # 排除sign和sign_type参数
        sign_params = {k: v for k, v in params.items() if k not in ['sign', 'sign_type']}
        
        # 按键名排序
        sorted_params = sorted(sign_params.items())
        
        # 构造签名字符串
        sign_string = '&'.join([f'{k}={v}' for k, v in sorted_params])
        sign_string += self.yipay_config['key']
        
        # MD5签名
        return hashlib.md5(sign_string.encode('utf-8')).hexdigest()
    
    def get_user_account_after(self) -> Optional[Dict]:
        """获取支付后的用户账户信息"""
        try:
            logger.info("=== 获取支付后用户账户信息 ===")
            
            # 等待一下，确保数据库更新完成
            time.sleep(2)
            
            response = self.session.get(f"{self.base_url}/auth/me")
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200:
                    data = result.get('data', {})
                    user_package = data.get('userPackage', {})
                    
                    account_info = {
                        'package_id': user_package.get('packageId'),
                        'package_name': user_package.get('packageName'),
                        'expire_time': user_package.get('packageExpireTime'),
                        'chat_remaining': user_package.get('chatCountRemaining'),
                        'total_amount': user_package.get('totalAmountSpent', 0)
                    }
                    
                    logger.info(f"支付后账户信息: {json.dumps(account_info, ensure_ascii=False, indent=2)}")
                    return account_info
                else:
                    logger.error(f"获取账户信息失败: {result.get('message')}")
                    return None
            else:
                logger.error(f"获取账户信息请求失败: {response.status_code}")
                return None
                
        except Exception as e:
            logger.error(f"获取账户信息异常: {e}")
            return None
    
    def run_complete_test(self) -> bool:
        """运行完整的支付流程测试"""
        try:
            logger.info("=" * 60)
            logger.info("支付模块完整流程测试开始")
            logger.info("=" * 60)
            logger.info(f"测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
            
            # 1. 用户登录
            if not self.login():
                logger.error("❌ 登录失败，测试终止")
                return False
            
            # 2. 获取支付前账户信息
            account_before = self.get_user_account_before()
            if account_before is None:
                logger.error("❌ 获取支付前账户信息失败")
                return False
            
            # 3. 获取套餐信息
            package_info = self.get_package_info(self.test_config['test_package_id'])
            if package_info is None:
                logger.error("❌ 获取套餐信息失败")
                return False
            
            # 4. 创建支付订单
            order_no = self.create_payment_order()
            if order_no is None:
                logger.error("❌ 创建支付订单失败")
                return False
            
            # 5. 检查订单初始状态
            logger.info("=== 检查订单初始状态 ===")
            order_status_before = self.check_order_status(order_no)
            if order_status_before:
                logger.info(f"订单初始状态: {json.dumps(order_status_before, ensure_ascii=False, indent=2)}")
            
            # 6. 模拟支付成功回调
            package_price = package_info.get('price', 0.00)
            if not self.simulate_yipay_callback(order_no, str(package_price)):
                logger.error("❌ 模拟支付回调失败")
                return False
            
            # 7. 检查订单支付后状态
            logger.info("=== 检查订单支付后状态 ===")
            order_status_after = self.check_order_status(order_no)
            if order_status_after:
                logger.info(f"订单支付后状态: {json.dumps(order_status_after, ensure_ascii=False, indent=2)}")
            
            # 8. 获取支付后账户信息
            account_after = self.get_user_account_after()
            if account_after is None:
                logger.error("❌ 获取支付后账户信息失败")
                return False
            
            # 9. 验证测试结果
            return self.verify_test_results(
                account_before, account_after, package_info, 
                order_status_before, order_status_after
            )
            
        except Exception as e:
            logger.error(f"测试执行异常: {e}")
            return False
    
    def verify_test_results(self, account_before: Dict, account_after: Dict, 
                          package_info: Dict, order_before: Dict, order_after: Dict) -> bool:
        """验证测试结果"""
        try:
            logger.info("=" * 60)
            logger.info("测试结果验证")
            logger.info("=" * 60)
            
            success = True
            
            # 验证订单状态更新
            logger.info("🔍 验证订单状态更新:")
            if order_before and order_after:
                status_before = order_before.get('payment_status', 0)
                status_after = order_after.get('payment_status', 0)
                
                if status_before == 0 and status_after == 1:
                    logger.info("✅ 订单状态正确更新: 未支付(0) -> 已支付(1)")
                else:
                    logger.error(f"❌ 订单状态更新异常: {status_before} -> {status_after}")
                    success = False
            
            # 验证用户套餐更新
            logger.info("🔍 验证用户套餐更新:")
            package_id_before = account_before.get('package_id')
            package_id_after = account_after.get('package_id')
            expected_package_id = str(self.test_config['test_package_id'])
            
            if str(package_id_after) == expected_package_id:
                logger.info(f"✅ 用户套餐正确更新: {package_id_before} -> {package_id_after}")
            else:
                logger.error(f"❌ 用户套餐更新异常: 期望{expected_package_id}, 实际{package_id_after}")
                success = False
            
            # 验证套餐过期时间更新
            expire_before = account_before.get('expire_time')
            expire_after = account_after.get('expire_time')
            
            if expire_after and expire_after != expire_before:
                logger.info(f"✅ 套餐过期时间正确更新: {expire_before} -> {expire_after}")
            else:
                logger.error(f"❌ 套餐过期时间更新异常: {expire_before} -> {expire_after}")
                success = False
            
            # 验证聊天次数更新
            chat_before = account_before.get('chat_remaining', 0)
            chat_after = account_after.get('chat_remaining', 0)
            expected_chat_count = package_info.get('chatCount', 0)
            
            if chat_after == expected_chat_count:
                logger.info(f"✅ 聊天次数正确更新: {chat_before} -> {chat_after}")
            else:
                logger.error(f"❌ 聊天次数更新异常: 期望{expected_chat_count}, 实际{chat_after}")
                success = False
            
            # 总结
            logger.info("=" * 60)
            if success:
                logger.info("🎉 支付模块测试全部通过！")
                logger.info("✅ 支付状态更新正常")
                logger.info("✅ 用户套餐激活正常")
                logger.info("✅ 数据一致性验证通过")
            else:
                logger.error("❌ 支付模块测试存在问题，请检查日志")
            
            logger.info("=" * 60)
            return success
            
        except Exception as e:
            logger.error(f"验证测试结果异常: {e}")
            return False

def main():
    """主函数"""
    tester = PaymentFlowTester()
    success = tester.run_complete_test()
    
    if success:
        print("\n🎉 支付模块测试成功完成！")
        exit(0)
    else:
        print("\n❌ 支付模块测试失败！")
        exit(1)

if __name__ == "__main__":
    main()
