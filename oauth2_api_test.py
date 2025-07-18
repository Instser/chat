#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAuth2 API实际测试脚本
通过HTTP请求测试项目的OAuth2功能

使用方法:
python oauth2_api_test.py
"""

import requests
import json
import time
import logging
from datetime import datetime
import urllib.parse

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class OAuth2ApiTester:
    """OAuth2 API测试器"""
    
    def __init__(self, base_url="http://localhost:8080/api"):
        self.base_url = base_url
        self.session = requests.Session()
        self.session.headers.update({
            'Content-Type': 'application/json',
            'User-Agent': 'OAuth2-API-Tester/1.0'
        })
        self.test_results = {
            'total': 0,
            'passed': 0,
            'failed': 0,
            'details': []
        }
    
    def log_test_result(self, test_name, success, message, response=None):
        """记录测试结果"""
        self.test_results['total'] += 1
        if success:
            self.test_results['passed'] += 1
            logger.info(f"✅ {test_name}: {message}")
        else:
            self.test_results['failed'] += 1
            logger.error(f"❌ {test_name}: {message}")
            if response:
                logger.error(f"   响应状态: {response.status_code}")
                logger.error(f"   响应内容: {response.text[:200]}...")
        
        self.test_results['details'].append({
            'test_name': test_name,
            'success': success,
            'message': message,
            'timestamp': datetime.now().isoformat()
        })
    
    def test_oauth2_login_endpoint(self, provider='google'):
        """测试OAuth2登录端点"""
        test_name = f"OAuth2登录端点测试 ({provider})"

        try:
            url = f"{self.base_url}/auth/oauth2/login/{provider}"
            response = self.session.get(url)
            
            if response.status_code == 200:
                data = response.json()
                # 检查是否是成功的Result格式
                if 'code' in data and data['code'] == 200 and 'data' in data:
                    result_data = data['data']
                    if 'authUrl' in result_data:
                        auth_url = result_data['authUrl']
                        # 验证授权URL格式
                        if provider == 'google' and 'accounts.google.com' in auth_url:
                            self.log_test_result(test_name, True, f"获取到有效的Google授权URL: {auth_url[:50]}...")
                        elif provider == 'github' and 'github.com' in auth_url:
                            self.log_test_result(test_name, True, f"获取到有效的GitHub授权URL: {auth_url[:50]}...")
                        else:
                            self.log_test_result(test_name, False, f"授权URL格式不正确: {auth_url}")
                        return auth_url
                    else:
                        self.log_test_result(test_name, False, "响应数据中缺少authUrl字段", response)
                else:
                    self.log_test_result(test_name, False, f"API返回错误: {data.get('message', '未知错误')}", response)
            else:
                # 尝试解析错误响应
                try:
                    error_data = response.json()
                    error_msg = error_data.get('message', f"HTTP状态码错误: {response.status_code}")
                    self.log_test_result(test_name, False, error_msg, response)
                except:
                    self.log_test_result(test_name, False, f"HTTP状态码错误: {response.status_code}", response)
                
        except Exception as e:
            self.log_test_result(test_name, False, f"请求异常: {str(e)}")
        
        return None
    
    def test_oauth2_callback_endpoint(self, provider='google'):
        """测试OAuth2回调端点"""
        test_name = f"OAuth2回调端点测试 ({provider})"
        
        try:
            # 模拟回调参数
            callback_params = {
                'code': 'mock_authorization_code_12345',
                'state': 'mock_state_token_67890'
            }
            
            url = f"{self.base_url}/auth/oauth2/callback/{provider}"
            response = self.session.get(url, params=callback_params)
            
            # 由于是模拟数据，期望返回错误，但应该是有意义的错误
            if response.status_code in [400, 401, 403]:
                try:
                    data = response.json()
                    if 'message' in data or 'error' in data:
                        self.log_test_result(test_name, True, f"回调端点正常响应错误: {response.status_code}")
                    else:
                        self.log_test_result(test_name, False, "错误响应格式不正确", response)
                except:
                    self.log_test_result(test_name, True, f"回调端点正常响应错误: {response.status_code}")
            elif response.status_code == 500:
                self.log_test_result(test_name, False, "服务器内部错误", response)
            else:
                self.log_test_result(test_name, False, f"意外的响应状态: {response.status_code}", response)
                
        except Exception as e:
            self.log_test_result(test_name, False, f"请求异常: {str(e)}")
    
    def test_oauth2_user_info_endpoint(self):
        """测试OAuth2用户信息端点"""
        test_name = "OAuth2用户信息端点测试"
        
        try:
            url = f"{self.base_url}/auth/oauth2/bindings"
            
            # 不带认证信息的请求
            response = self.session.get(url)
            
            if response.status_code in [401, 403]:
                self.log_test_result(test_name, True, f"未认证请求正确返回: {response.status_code}")
            else:
                self.log_test_result(test_name, False, f"未认证请求响应异常: {response.status_code}", response)
                
        except Exception as e:
            self.log_test_result(test_name, False, f"请求异常: {str(e)}")
    
    def test_oauth2_bind_endpoint(self):
        """测试OAuth2绑定端点"""
        test_name = "OAuth2绑定端点测试"
        
        try:
            url = f"{self.base_url}/auth/oauth2/bind/google"
            
            # 不带认证信息的请求
            response = self.session.post(url)
            
            if response.status_code in [401, 403]:
                self.log_test_result(test_name, True, f"未认证绑定请求正确返回: {response.status_code}")
            else:
                self.log_test_result(test_name, False, f"未认证绑定请求响应异常: {response.status_code}", response)
                
        except Exception as e:
            self.log_test_result(test_name, False, f"请求异常: {str(e)}")
    

    
    def test_server_connectivity(self):
        """测试服务器连通性"""
        test_name = "服务器连通性测试"
        
        try:
            # 测试健康检查端点
            health_url = f"{self.base_url}/actuator/health"
            response = self.session.get(health_url, timeout=5)
            
            if response.status_code == 200:
                self.log_test_result(test_name, True, "服务器健康检查通过")
                return True
            else:
                # 尝试基础端点
                base_response = self.session.get(self.base_url, timeout=5)
                if base_response.status_code in [200, 404]:
                    self.log_test_result(test_name, True, "服务器基础连接正常")
                    return True
                else:
                    self.log_test_result(test_name, False, f"服务器响应异常: {base_response.status_code}")
                    return False
                    
        except requests.exceptions.ConnectTimeout:
            self.log_test_result(test_name, False, "连接超时，请检查服务器是否启动")
            return False
        except requests.exceptions.ConnectionError:
            self.log_test_result(test_name, False, "连接失败，请检查服务器地址和端口")
            return False
        except Exception as e:
            self.log_test_result(test_name, False, f"连接异常: {str(e)}")
            return False
    
    def test_oauth2_state_validation(self):
        """测试OAuth2状态验证"""
        test_name = "OAuth2状态验证测试"

        try:
            # 先获取授权URL，其中应该包含state参数
            auth_url = self.test_oauth2_login_endpoint('google')
            if auth_url:
                # 解析state参数
                parsed_url = urllib.parse.urlparse(auth_url)
                query_params = urllib.parse.parse_qs(parsed_url.query)

                if 'state' in query_params:
                    state_value = query_params['state'][0]
                    if len(state_value) >= 32:  # 状态令牌应该足够长
                        self.log_test_result(test_name, True, f"状态令牌格式正确，长度: {len(state_value)}")
                    else:
                        self.log_test_result(test_name, False, f"状态令牌长度不足: {len(state_value)}")
                else:
                    self.log_test_result(test_name, False, "授权URL中缺少state参数")
            else:
                self.log_test_result(test_name, False, "无法获取授权URL进行状态验证")

        except Exception as e:
            self.log_test_result(test_name, False, f"状态验证异常: {str(e)}")

    def test_oauth2_configuration(self):
        """测试OAuth2配置"""
        test_name = "OAuth2配置测试"

        try:
            # 测试是否配置了OAuth2提供商
            url = f"{self.base_url}/auth/oauth2/providers"
            response = self.session.get(url)

            if response.status_code == 200:
                data = response.json()
                if 'code' in data and data['code'] == 200:
                    providers = data.get('data', [])
                    if isinstance(providers, list) and len(providers) > 0:
                        self.log_test_result(test_name, True, f"OAuth2提供商配置正常，支持: {providers}")
                    else:
                        self.log_test_result(test_name, False, "未配置OAuth2提供商")
                else:
                    self.log_test_result(test_name, False, f"API返回错误: {data.get('message', '未知错误')}")
            elif response.status_code == 404:
                self.log_test_result(test_name, False, "OAuth2提供商接口不存在")
            else:
                self.log_test_result(test_name, False, f"配置检查失败: {response.status_code}")

        except Exception as e:
            self.log_test_result(test_name, False, f"配置检查异常: {str(e)}")
    
    def run_all_tests(self):
        """运行所有测试"""
        logger.info("🚀 开始OAuth2 API实际测试")
        logger.info("=" * 60)
        
        start_time = time.time()
        
        # 1. 服务器连通性测试
        if not self.test_server_connectivity():
            logger.error("❌ 服务器连接失败，跳过后续测试")
            self.generate_test_report(time.time() - start_time)
            return False
        
        # 2. OAuth2登录端点测试
        self.test_oauth2_login_endpoint('google')
        self.test_oauth2_login_endpoint('github')
        
        # 3. OAuth2回调端点测试
        self.test_oauth2_callback_endpoint('google')
        self.test_oauth2_callback_endpoint('github')
        
        # 4. OAuth2用户信息端点测试
        self.test_oauth2_user_info_endpoint()
        
        # 5. OAuth2绑定端点测试
        self.test_oauth2_bind_endpoint()
        
        # 6. OAuth2配置测试
        self.test_oauth2_configuration()

        # 7. OAuth2状态验证测试
        self.test_oauth2_state_validation()
        
        end_time = time.time()
        self.generate_test_report(end_time - start_time)
        
        return self.test_results['failed'] == 0
    
    def generate_test_report(self, duration):
        """生成测试报告"""
        logger.info("=" * 60)
        logger.info("📊 OAuth2 API测试结果统计")
        logger.info("=" * 60)
        logger.info(f"执行时间: {duration:.2f} 秒")
        logger.info(f"总测试数: {self.test_results['total']}")
        logger.info(f"通过: {self.test_results['passed']}")
        logger.info(f"失败: {self.test_results['failed']}")
        
        if self.test_results['total'] > 0:
            success_rate = (self.test_results['passed'] / self.test_results['total']) * 100
            logger.info(f"成功率: {success_rate:.2f}%")
        
        # 保存详细报告
        report_file = f'oauth2_api_test_report_{datetime.now().strftime("%Y%m%d_%H%M%S")}.json'
        try:
            with open(report_file, 'w', encoding='utf-8') as f:
                json.dump({
                    'summary': self.test_results,
                    'duration': duration,
                    'timestamp': datetime.now().isoformat(),
                    'base_url': self.base_url
                }, f, indent=2, ensure_ascii=False)
            logger.info(f"📄 详细报告已保存到: {report_file}")
        except Exception as e:
            logger.warning(f"⚠️ 保存报告失败: {e}")
        
        if self.test_results['failed'] == 0:
            logger.info("🎉 所有测试通过！OAuth2 API功能正常")
        else:
            logger.warning("❌ 存在测试失败，请检查相关功能")

def main():
    """主函数"""
    import sys
    
    # 检查命令行参数
    base_url = "http://localhost:8080/api"
    if len(sys.argv) > 1:
        base_url = sys.argv[1]
    
    logger.info(f"测试目标: {base_url}")
    
    try:
        tester = OAuth2ApiTester(base_url)
        success = tester.run_all_tests()
        sys.exit(0 if success else 1)
    except KeyboardInterrupt:
        logger.info("⏹️ 测试被用户中断")
        sys.exit(1)
    except Exception as e:
        logger.error(f"❌ 测试执行过程中发生错误: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
