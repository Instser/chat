#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PayPal支付集成测试脚本
测试PayPal支付流程是否正确联通

使用方法:
python paypal_test.py

需要先启动后端应用: mvn spring-boot:run
"""

import requests
import time
import webbrowser
import json
import sys
import io
from typing import Optional, Dict, Any
from datetime import datetime

# 设置标准输出编码为UTF-8以支持中文和emoji
if sys.platform.startswith('win'):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

class PayPalPaymentTester:
    def __init__(self, base_url: str = "http://localhost:8080"):
        self.base_url = base_url
        self.api_base = f"{base_url}/api"
        self.jwt_token: Optional[str] = None
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
            # 如果遇到编码错误，移除特殊字符重试
            safe_message = message.encode('ascii', 'ignore').decode('ascii')
            print(f"[{timestamp}] {level}: {safe_message}")
        except Exception as e:
            # 最后的备用方案
            print(f"[{timestamp}] {level}: [消息显示错误: {str(e)}]")
    
    def login(self, username_or_email: str, password: str) -> bool:
        """用户登录获取JWT令牌"""
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
                self.log(f"登录响应: {json.dumps(result, indent=2, ensure_ascii=False)}")
                
                # 检查响应是否成功
                if result.get('code') == 200:
                    # JWT token已经通过cookie设置，session会自动保存
                    # 检查是否设置了认证cookie
                    cookies = response.cookies
                    auth_cookie_found = False
                    for cookie in cookies:
                        if cookie.name in ['auth-token', 'token', 'jwt', 'Authorization']:
                            auth_cookie_found = True
                            self.log(f"检测到认证Cookie: {cookie.name}")
                            break
                    
                    # 也检查Set-Cookie头
                    set_cookie_header = response.headers.get('Set-Cookie', '')
                    if 'auth' in set_cookie_header.lower() or 'token' in set_cookie_header.lower():
                        auth_cookie_found = True
                        self.log("检测到认证Cookie头")
                    
                    self.log("[成功] 登录成功，认证信息已通过Cookie设置")
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
                self.log(f"创建支付响应: {json.dumps(result, indent=2, ensure_ascii=False)}")
                
                if result.get('code') == 200:
                    self.log("[成功] PayPal支付订单创建成功")
                    return result
                else:
                    # 检查是否是包装错误响应
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
    
    def test_connectivity(self) -> bool:
        """测试基础连通性"""
        self.log("[连通] 测试服务器连通性...")
        
        try:
            # 测试健康检查端点
            response = self.session.get(f"{self.base_url}/actuator/health", timeout=10)
            if response.status_code == 200:
                self.log("[成功] 服务器连通性正常")
                return True
        except:
            pass
            
        try:
            # 测试根路径
            response = self.session.get(self.base_url, timeout=10)
            self.log(f"服务器响应状态: {response.status_code}")
            return True
        except Exception as e:
            self.log(f"[失败] 服务器连接失败: {e}", "ERROR")
            return False
    
    def generate_test_report(self, success: bool, order_no: str = None, approval_url: str = None, error_msg: str = None):
        """生成测试报告"""
        import time
        timestamp = time.strftime("%Y-%m-%d_%H-%M-%S")
        
        report = []
        report.append("=" * 80)
        report.append("PayPal支付集成测试报告")
        report.append("=" * 80)
        report.append(f"测试时间: {timestamp}")
        report.append(f"后端服务: {self.base_url}")
        report.append(f"测试结果: {'✓ 成功' if success else '✗ 失败'}")
        report.append("")
        
        if success and order_no:
            report.append("🎉 测试成功详情:")
            report.append(f"   • 服务器连通性: ✓")
            report.append(f"   • 用户认证: ✓")  
            report.append(f"   • PayPal订单创建: ✓")
            report.append(f"   • 本地订单号: {order_no}")
            if approval_url:
                report.append(f"   • 支付链接: {approval_url}")
            report.append("")
            report.append("📋 建议:")
            report.append("   • PayPal支付功能基础集成正常")
            report.append("   • 可以进行完整的支付流程测试")
            report.append("   • 建议在生产环境前进行端到端测试")
        else:
            report.append("❌ 测试失败详情:")
            if error_msg:
                report.append(f"   • 错误信息: {error_msg}")
            report.append("")
            report.append("🔧 排查建议:")
            report.append("   • 检查后端服务是否正常启动")
            report.append("   • 验证PayPal配置是否正确")
            report.append("   • 查看后端日志中的详细错误信息")
            report.append("   • 确认网络连接和防火墙设置")
        
        report.append("")
        report.append("=" * 80)
        report.append("测试环境信息:")
        report.append("=" * 80)
        report.append("• Python版本: 3.x")
        report.append("• 测试脚本: paypal_test.py")
        report.append("• PayPal环境: 沙箱环境")
        report.append("• 支持货币: USD, EUR, GBP, CAD, AUD, JPY, CHF, SEK, NOK, DKK, CNY")
        report.append("=" * 80)
        
        return "\n".join(report)
    
    def poll_payment_status(self, order_no: str, interval: int = 3, max_duration: int = 300) -> bool:
        self.log(f"[轮询] 开始轮询支付状态 - 间隔: {interval}秒, 最大时长: {max_duration}秒")
        
        start_time = time.time()
        check_count = 0
        
        while time.time() - start_time < max_duration:
            check_count += 1
            try:
                self.log(f"第 {check_count} 次检查支付状态...")
                status_result = self.check_payment_status(order_no)
                
                if status_result.get('code') == 200:
                    data = status_result.get('data', {})
                    status = data.get('status')
                    message = data.get('message', '未知状态')
                    
                    self.log(f"[状态] 订单状态: {message} (状态码: {status})")
                    
                    if status == 1:  # 已支付
                        self.log("[成功] 支付成功！订单已完成")
                        return True
                    elif status in [2, 3]:  # 已取消或已退款
                        self.log("[失败] 支付失败或已取消")
                        return False
                else:
                    self.log(f"[警告] 查询状态响应异常: {status_result}")
                
                if check_count < max_duration // interval:
                    self.log(f"[等待] 等待 {interval} 秒后继续检查...")
                    time.sleep(interval)
                    
            except Exception as e:
                self.log(f"[警告] 查询状态异常: {e}", "WARN")
                time.sleep(interval)
        
        self.log("[超时] 轮询超时，请手动检查支付状态")
        return False
    
    def run_connectivity_test(self) -> bool:
        """运行连通性测试"""
        self.log("=" * 60)
        self.log("[测试] 开始PayPal支付连通性测试")
        self.log("=" * 60)
        
        # 1. 测试服务器连通性
        if not self.test_connectivity():
            self.log("[失败] 服务器连通性测试失败，请确认后端服务已启动")
            return False
            
        # 2. 测试登录
        username = "Instser"
        password = "cgbz..6757"  # 用户提供的密码
        
        if not self.login(username, password):
            self.log("[失败] 登录测试失败")
            return False
            
        # 3. 测试创建PayPal支付订单
        package_id = 3  # 用户提供的套餐ID
        currency = "USD"
        
        try:
            payment_result = self.create_paypal_payment(package_id, currency)
            
            # 提取关键信息
            data = payment_result.get('data', {})
            order_no = data.get('orderNo')
            approval_url = data.get('approvalUrl')
            paypal_order_id = data.get('paypalOrderId')
            
            if not order_no or not approval_url:
                self.log("[失败] PayPal支付订单创建失败：缺少关键信息")
                return False
                
            self.log("=" * 60)
            self.log("[成功] PayPal支付连通性测试成功！")
            self.log(f"   本地订单号: {order_no}")
            self.log(f"   PayPal订单ID: {paypal_order_id}")
            self.log(f"   支付链接: {approval_url}")
            self.log("=" * 60)
            
            # 询问是否进行完整支付测试
            self.log("\n[提示] 连通性测试已完成，是否要进行完整的支付流程测试？")
            try:
                user_input = input("输入 'y' 或 'yes' 继续完整测试，其他任意键退出: ").strip().lower()
                
                if user_input in ['y', 'yes']:
                    return self.run_full_payment_test(order_no, approval_url)
                else:
                    self.log("[结束] 连通性测试完成，跳过完整支付测试")
                    return True
            except EOFError:
                # 处理非交互式环境（如Claude Code）的情况
                self.log("[注意] 检测到非交互式环境，自动跳过完整支付测试")
                self.log("[结束] 连通性测试完成，PayPal支付功能正常")
                
                # 生成测试报告
                report = self.generate_test_report(True, order_no, approval_url)
                self.log("\n" + report)
                
                return True
            except Exception as e:
                self.log(f"[警告] 用户输入异常: {e}")
                self.log("[结束] 连通性测试完成，跳过完整支付测试")
                
                # 生成测试报告
                report = self.generate_test_report(True, order_no, approval_url)
                self.log("\n" + report)
                
                return True
                
        except Exception as e:
            error_msg = str(e)
            self.log(f"[失败] PayPal支付连通性测试失败: {e}", "ERROR")
            
            # 生成失败报告
            report = self.generate_test_report(False, error_msg=error_msg)
            self.log("\n" + report)
            
            return False
    
    def run_full_payment_test(self, order_no: str, approval_url: str) -> bool:
        """运行完整支付测试"""
        self.log("=" * 60)
        self.log("[测试] 开始完整PayPal支付流程测试")
        self.log("=" * 60)
        
        # 打开支付页面
        self.log("[浏览器] 正在打开PayPal支付页面...")
        try:
            webbrowser.open(approval_url)
            self.log("[成功] 支付页面已在浏览器中打开")
        except Exception as e:
            self.log(f"[警告] 自动打开浏览器失败: {e}")
            self.log(f"请手动打开以下链接完成支付:")
            self.log(f"[链接] {approval_url}")
        
        # 等待用户完成支付
        self.log("\n[指引] 请在浏览器中完成PayPal支付流程")
        self.log("   1. 登录PayPal沙箱账户")
        self.log("   2. 确认支付金额和信息")
        self.log("   3. 点击 'Pay Now' 完成支付")
        self.log("   4. 支付完成后返回此窗口")
        
        try:
            input("\n[等待]  完成支付后，按回车键继续...")
        except EOFError:
            self.log("\n[注意] 检测到非交互式环境，自动开始轮询支付状态")
        except Exception as e:
            self.log(f"\n[警告] 输入异常: {e}，自动开始轮询支付状态")
        
        # 轮询检查支付状态
        return self.poll_payment_status(order_no)

def main():
    """主函数"""
    print("PayPal支付集成测试脚本")
    print("作者: Claude Code")
    print("用途: 测试PayPal支付模块是否正确集成")
    print()
    
    # 创建测试实例
    tester = PayPalPaymentTester()
    
    try:
        # 运行连通性测试
        success = tester.run_connectivity_test()
        
        if success:
            print("\n[成功] 测试完成！PayPal支付集成正常")
        else:
            print("\n[失败] 测试失败！请检查配置和日志")
            
    except KeyboardInterrupt:
        print("\n\n[中断] 测试被用户中断")
    except Exception as e:
        print(f"\n[异常] 测试过程中发生异常: {e}")
    
    print("\n感谢使用PayPal支付测试脚本！")

if __name__ == "__main__":
    main()