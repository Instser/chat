#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
UserPaymentStatusController 端点测试脚本
测试时间: 2025-07-17 00:31
版本: v1.0
作者: Augment Agent
"""

import requests
import json
import time
import sys
from typing import Dict, Any, Optional
from datetime import datetime

class UserPaymentStatusTester:
    """用户支付状态控制器测试类"""
    
    def __init__(self, base_url: str = "http://localhost:8080/api"):
        """
        初始化测试器
        
        Args:
            base_url: API基础URL
        """
        self.base_url = base_url
        self.session = requests.Session()
        self.auth_token = None
        self.test_results = []
        
        # 设置请求头
        self.session.headers.update({
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'User-Agent': 'UserPaymentStatusTester/1.0'
        })
    
    def log_test_result(self, test_name: str, success: bool, message: str, response_data: Any = None):
        """记录测试结果"""
        result = {
            'test_name': test_name,
            'success': success,
            'message': message,
            'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'response_data': response_data
        }
        self.test_results.append(result)
        
        status = "✅ PASS" if success else "❌ FAIL"
        print(f"{status} {test_name}: {message}")
        if response_data and not success:
            print(f"   响应数据: {json.dumps(response_data, ensure_ascii=False, indent=2)}")
    
    def login(self, username: str = "admin", password: str = "admin123") -> bool:
        """
        用户登录获取认证token
        
        Args:
            username: 用户名
            password: 密码
            
        Returns:
            bool: 登录是否成功
        """
        login_url = f"{self.base_url}/auth/login"
        login_data = {
            "usernameOrEmail": username,
            "password": password
        }
        
        try:
            response = self.session.post(login_url, json=login_data)
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200:
                    # 从Cookie中获取认证token
                    cookies = response.cookies
                    auth_cookie = cookies.get('auth-token')
                    if auth_cookie:
                        self.auth_token = auth_cookie
                        self.session.cookies.set('auth-token', auth_cookie)
                        self.log_test_result("用户登录", True, f"登录成功，用户: {username}")
                        return True
                    else:
                        self.log_test_result("用户登录", False, "未获取到认证Cookie", result)
                        return False
                else:
                    self.log_test_result("用户登录", False, f"登录失败: {result.get('message', '未知错误')}", result)
                    return False
            else:
                self.log_test_result("用户登录", False, f"HTTP错误: {response.status_code}", response.text)
                return False
                
        except Exception as e:
            self.log_test_result("用户登录", False, f"请求异常: {str(e)}")
            return False
    
    def test_payment_status_overview(self) -> bool:
        """测试获取用户支付状态概览"""
        url = f"{self.base_url}/user/status/overview"
        
        try:
            response = self.session.get(url)
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200:
                    data = result.get('data', {})
                    required_fields = ['userId', 'neverPaid', 'onlyFreeUser', 'activePaidUser', 
                                     'statusCategory', 'totalPaidAmount', 'firstPaymentTime']
                    
                    missing_fields = [field for field in required_fields if field not in data]
                    if not missing_fields:
                        self.log_test_result("支付状态概览", True, 
                                           f"获取成功，状态分类: {data.get('statusCategory')}", data)
                        return True
                    else:
                        self.log_test_result("支付状态概览", False, 
                                           f"响应缺少字段: {missing_fields}", result)
                        return False
                else:
                    self.log_test_result("支付状态概览", False, 
                                       f"业务错误: {result.get('message', '未知错误')}", result)
                    return False
            else:
                self.log_test_result("支付状态概览", False, f"HTTP错误: {response.status_code}", response.text)
                return False
                
        except Exception as e:
            self.log_test_result("支付状态概览", False, f"请求异常: {str(e)}")
            return False
    
    def test_has_never_paid(self) -> bool:
        """测试检查用户是否从未充值"""
        url = f"{self.base_url}/user/status/new"
        
        try:
            response = self.session.get(url)
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200:
                    data = result.get('data')
                    if isinstance(data, bool):
                        self.log_test_result("从未充值检查", True, 
                                           f"检查成功，从未充值: {data}", result)
                        return True
                    else:
                        self.log_test_result("从未充值检查", False, 
                                           f"返回数据类型错误，期望bool，实际: {type(data)}", result)
                        return False
                else:
                    self.log_test_result("从未充值检查", False, 
                                       f"业务错误: {result.get('message', '未知错误')}", result)
                    return False
            else:
                self.log_test_result("从未充值检查", False, f"HTTP错误: {response.status_code}", response.text)
                return False
                
        except Exception as e:
            self.log_test_result("从未充值检查", False, f"请求异常: {str(e)}")
            return False
    
    def test_is_only_free_user(self) -> bool:
        """测试检查用户是否只使用免费套餐"""
        url = f"{self.base_url}/user/status/free-only"
        
        try:
            response = self.session.get(url)
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200:
                    data = result.get('data')
                    if isinstance(data, bool):
                        self.log_test_result("免费套餐检查", True, 
                                           f"检查成功，仅免费套餐: {data}", result)
                        return True
                    else:
                        self.log_test_result("免费套餐检查", False, 
                                           f"返回数据类型错误，期望bool，实际: {type(data)}", result)
                        return False
                else:
                    self.log_test_result("免费套餐检查", False, 
                                       f"业务错误: {result.get('message', '未知错误')}", result)
                    return False
            else:
                self.log_test_result("免费套餐检查", False, f"HTTP错误: {response.status_code}", response.text)
                return False
                
        except Exception as e:
            self.log_test_result("免费套餐检查", False, f"请求异常: {str(e)}")
            return False
    
    def test_is_active_paid_user(self) -> bool:
        """测试检查用户是否为活跃付费用户"""
        url = f"{self.base_url}/user/status/active-paid"
        
        try:
            response = self.session.get(url)
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200:
                    data = result.get('data')
                    if isinstance(data, bool):
                        self.log_test_result("活跃付费用户检查", True, 
                                           f"检查成功，活跃付费用户: {data}", result)
                        return True
                    else:
                        self.log_test_result("活跃付费用户检查", False, 
                                           f"返回数据类型错误，期望bool，实际: {type(data)}", result)
                        return False
                else:
                    self.log_test_result("活跃付费用户检查", False, 
                                       f"业务错误: {result.get('message', '未知错误')}", result)
                    return False
            else:
                self.log_test_result("活跃付费用户检查", False, f"HTTP错误: {response.status_code}", response.text)
                return False
                
        except Exception as e:
            self.log_test_result("活跃付费用户检查", False, f"请求异常: {str(e)}")
            return False
    
    def test_get_total_paid_amount(self) -> bool:
        """测试获取用户总充值金额"""
        url = f"{self.base_url}/user/status/total-amount"
        
        try:
            response = self.session.get(url)
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200:
                    data = result.get('data')
                    # 金额可能为null或数字
                    if data is None or isinstance(data, (int, float)):
                        self.log_test_result("总充值金额查询", True, 
                                           f"查询成功，总金额: {data}", result)
                        return True
                    else:
                        self.log_test_result("总充值金额查询", False, 
                                           f"返回数据类型错误，期望数字或null，实际: {type(data)}", result)
                        return False
                else:
                    self.log_test_result("总充值金额查询", False, 
                                       f"业务错误: {result.get('message', '未知错误')}", result)
                    return False
            else:
                self.log_test_result("总充值金额查询", False, f"HTTP错误: {response.status_code}", response.text)
                return False
                
        except Exception as e:
            self.log_test_result("总充值金额查询", False, f"请求异常: {str(e)}")
            return False
    
    def test_get_first_payment_time(self) -> bool:
        """测试获取用户首次充值时间"""
        url = f"{self.base_url}/user/status/first-payment-time"
        
        try:
            response = self.session.get(url)
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200:
                    data = result.get('data')
                    # 时间可能为null或时间字符串
                    self.log_test_result("首次充值时间查询", True, 
                                       f"查询成功，首次充值时间: {data}", result)
                    return True
                else:
                    self.log_test_result("首次充值时间查询", False, 
                                       f"业务错误: {result.get('message', '未知错误')}", result)
                    return False
            else:
                self.log_test_result("首次充值时间查询", False, f"HTTP错误: {response.status_code}", response.text)
                return False
                
        except Exception as e:
            self.log_test_result("首次充值时间查询", False, f"请求异常: {str(e)}")
            return False

    def test_admin_get_payment_status(self, target_user_id: int = 1) -> bool:
        """测试管理员查询指定用户支付状态"""
        url = f"{self.base_url}/user/status/admin/{target_user_id}"

        try:
            response = self.session.get(url)

            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200:
                    data = result.get('data', {})
                    required_fields = ['userId', 'neverPaid', 'onlyFreeUser', 'activePaidUser',
                                     'totalPaidAmount', 'firstPaymentTime']

                    missing_fields = [field for field in required_fields if field not in data]
                    if not missing_fields:
                        self.log_test_result("管理员查询用户状态", True,
                                           f"查询成功，目标用户ID: {target_user_id}", data)
                        return True
                    else:
                        self.log_test_result("管理员查询用户状态", False,
                                           f"响应缺少字段: {missing_fields}", result)
                        return False
                else:
                    self.log_test_result("管理员查询用户状态", False,
                                       f"业务错误: {result.get('message', '未知错误')}", result)
                    return False
            else:
                self.log_test_result("管理员查询用户状态", False, f"HTTP错误: {response.status_code}", response.text)
                return False

        except Exception as e:
            self.log_test_result("管理员查询用户状态", False, f"请求异常: {str(e)}")
            return False

    def test_unauthorized_access(self) -> bool:
        """测试未认证访问"""
        # 临时清除认证信息
        original_cookies = self.session.cookies.copy()
        self.session.cookies.clear()

        url = f"{self.base_url}/user/status/overview"

        try:
            response = self.session.get(url)

            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 401:
                    self.log_test_result("未认证访问测试", True,
                                       "正确返回401未认证错误", result)
                    success = True
                else:
                    self.log_test_result("未认证访问测试", False,
                                       f"期望401错误，实际返回: {result.get('code')}", result)
                    success = False
            elif response.status_code == 401:
                # 直接返回HTTP 401也是正确的
                self.log_test_result("未认证访问测试", True,
                                   "正确返回HTTP 401未认证错误")
                success = True
            else:
                self.log_test_result("未认证访问测试", False, f"HTTP错误: {response.status_code}", response.text)
                success = False

        except Exception as e:
            self.log_test_result("未认证访问测试", False, f"请求异常: {str(e)}")
            success = False
        finally:
            # 恢复认证信息
            self.session.cookies.update(original_cookies)

        return success

    def run_all_tests(self) -> Dict[str, Any]:
        """运行所有测试"""
        print("=" * 60)
        print("UserPaymentStatusController 端点测试")
        print("=" * 60)
        print(f"测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"测试目标: {self.base_url}")
        print("=" * 60)

        # 1. 登录测试
        if not self.login():
            print("\n❌ 登录失败，无法继续测试")
            return self.generate_test_report()

        print("\n🔍 开始API端点测试...")

        # 2. 测试所有端点
        test_methods = [
            self.test_payment_status_overview,
            self.test_has_never_paid,
            self.test_is_only_free_user,
            self.test_is_active_paid_user,
            self.test_get_total_paid_amount,
            self.test_get_first_payment_time,
            self.test_admin_get_payment_status,
            self.test_unauthorized_access
        ]

        for test_method in test_methods:
            try:
                test_method()
                time.sleep(0.5)  # 避免请求过于频繁
            except Exception as e:
                self.log_test_result(test_method.__name__, False, f"测试执行异常: {str(e)}")

        return self.generate_test_report()

    def generate_test_report(self) -> Dict[str, Any]:
        """生成测试报告"""
        total_tests = len(self.test_results)
        passed_tests = sum(1 for result in self.test_results if result['success'])
        failed_tests = total_tests - passed_tests

        report = {
            'summary': {
                'total_tests': total_tests,
                'passed_tests': passed_tests,
                'failed_tests': failed_tests,
                'success_rate': f"{(passed_tests / total_tests * 100):.1f}%" if total_tests > 0 else "0%"
            },
            'test_results': self.test_results,
            'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        }

        print("\n" + "=" * 60)
        print("测试报告")
        print("=" * 60)
        print(f"总测试数: {total_tests}")
        print(f"通过测试: {passed_tests}")
        print(f"失败测试: {failed_tests}")
        print(f"成功率: {report['summary']['success_rate']}")

        if failed_tests > 0:
            print("\n❌ 失败的测试:")
            for result in self.test_results:
                if not result['success']:
                    print(f"  - {result['test_name']}: {result['message']}")

        print("=" * 60)

        return report


def main():
    """主函数"""
    import argparse

    parser = argparse.ArgumentParser(description='UserPaymentStatusController API测试工具')
    parser.add_argument('--url', default='http://localhost:8080/api',
                       help='API基础URL (默认: http://localhost:8080/api)')
    parser.add_argument('--username', default='admin',
                       help='登录用户名 (默认: admin)')
    parser.add_argument('--password', default='admin123',
                       help='登录密码 (默认: admin123)')
    parser.add_argument('--output', help='测试报告输出文件路径 (JSON格式)')

    args = parser.parse_args()

    # 创建测试器并运行测试
    tester = UserPaymentStatusTester(args.url)

    # 设置登录凭据
    if not tester.login(args.username, args.password):
        print("❌ 登录失败，请检查用户名和密码")
        sys.exit(1)

    # 运行所有测试
    report = tester.run_all_tests()

    # 保存测试报告
    if args.output:
        try:
            with open(args.output, 'w', encoding='utf-8') as f:
                json.dump(report, f, ensure_ascii=False, indent=2)
            print(f"\n📄 测试报告已保存到: {args.output}")
        except Exception as e:
            print(f"\n❌ 保存测试报告失败: {str(e)}")

    # 根据测试结果设置退出码
    if report['summary']['failed_tests'] > 0:
        sys.exit(1)
    else:
        sys.exit(0)


if __name__ == "__main__":
    main()
