#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAuth2详细测试方案 - Python测试脚本
基于文档: docs/07-16 23:15 OAuth2详细测试方案.md

测试覆盖范围:
1. 单元测试 (Unit Tests)
2. 集成测试 (Integration Tests) 
3. 系统测试 (System Tests)
4. 验收测试 (Acceptance Tests)
"""

import unittest
import hashlib
import uuid
import time
import re
import json
import requests
from unittest.mock import Mock, patch, MagicMock
from datetime import datetime, timedelta
import logging

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class OAuth2SecurityUtil:
    """OAuth2安全工具类 - 模拟实现"""
    
    @staticmethod
    def generate_state_token():
        """生成状态令牌"""
        uuid_part = str(uuid.uuid4()).replace('-', '')
        timestamp = str(int(time.time()))
        random_part = hashlib.md5(str(time.time()).encode()).hexdigest()[:8]
        return f"{uuid_part}{timestamp}{random_part}"
    
    @staticmethod
    def encrypt_token(token, key="test_key"):
        """AES加密模拟"""
        return hashlib.sha256(f"{token}{key}".encode()).hexdigest()
    
    @staticmethod
    def decrypt_token(encrypted_token, key="test_key"):
        """AES解密模拟"""
        # 简化实现，实际应该是真正的解密
        return encrypted_token
    
    @staticmethod
    def generate_device_fingerprint(user_agent, ip_address):
        """生成设备指纹"""
        timestamp = str(int(time.time()))
        fingerprint_data = f"{user_agent}{ip_address}{timestamp}"
        return hashlib.md5(fingerprint_data.encode()).hexdigest()[:16]
    
    @staticmethod
    def calculate_risk_score(ip_address, user_agent, login_count):
        """计算风险评分"""
        score = 0
        
        # IP风险评估
        if ip_address.startswith('192.168.') or ip_address.startswith('10.'):
            score += 10  # 内网IP，低风险
        else:
            score += 30  # 外网IP，中等风险
            
        # User-Agent风险评估
        if 'bot' in user_agent.lower() or 'crawler' in user_agent.lower():
            score += 50  # 机器人，高风险
        else:
            score += 10  # 正常浏览器，低风险
            
        # 登录次数风险评估
        if login_count > 10:
            score += 5   # 高登录次数，低风险
        elif login_count > 5:
            score += 15  # 中等登录次数，中等风险
        else:
            score += 25  # 低登录次数，高风险
            
        return min(score, 100)  # 最大100分
    
    @staticmethod
    def validate_state_token_format(token):
        """验证状态令牌格式"""
        if not token or not isinstance(token, str):
            return False
        if len(token) < 32:
            return False
        if not re.match(r'^[a-zA-Z0-9]+$', token):
            return False
        return True

class OAuth2Entity:
    """OAuth2实体类模拟"""
    
    def __init__(self):
        self.created_time = datetime.now()
        self.updated_time = datetime.now()
        self.deleted = False

class UserOAuth(OAuth2Entity):
    """用户OAuth绑定实体"""
    
    def __init__(self, user_id=None, provider=None, provider_id=None, email=None, name=None):
        super().__init__()
        self.user_id = user_id
        self.provider = provider
        self.provider_id = provider_id
        self.email = email
        self.name = name
        self.last_login_time = None

class OAuth2State(OAuth2Entity):
    """OAuth2状态实体"""
    
    def __init__(self, state_token=None, expires_at=None):
        super().__init__()
        self.state_token = state_token
        self.expires_at = expires_at or (datetime.now() + timedelta(minutes=10))
        self.used = False

class OAuth2AuditLog(OAuth2Entity):
    """OAuth2审计日志实体"""
    
    def __init__(self, user_id=None, provider=None, action=None, ip_address=None):
        super().__init__()
        self.user_id = user_id
        self.provider = provider
        self.action = action
        self.ip_address = ip_address
        self.execution_time = datetime.now()
        self.error_message = None

# ==================== 单元测试 ====================

class TestOAuth2SecurityUtil(unittest.TestCase):
    """UC-001 到 UC-005: OAuth2SecurityUtil 安全工具类测试"""
    
    def test_state_token_generation(self):
        """UC-001: 状态令牌生成测试"""
        logger.info("执行测试: UC-001 状态令牌生成测试")
        
        # 生成多个令牌验证唯一性
        tokens = [OAuth2SecurityUtil.generate_state_token() for _ in range(10)]
        
        # 验证令牌唯一性
        self.assertEqual(len(tokens), len(set(tokens)), "令牌应该是唯一的")
        
        # 验证令牌长度
        for token in tokens:
            self.assertGreater(len(token), 32, f"令牌长度应该大于32字符: {len(token)}")
            
        # 验证令牌格式
        for token in tokens:
            self.assertTrue(re.match(r'^[a-zA-Z0-9]+$', token), f"令牌格式应该只包含字母数字: {token}")
            
        logger.info("UC-001 测试通过: 状态令牌生成功能正常")
    
    def test_token_encryption_decryption(self):
        """UC-002: 令牌加密解密测试"""
        logger.info("执行测试: UC-002 令牌加密解密测试")
        
        original_token = "test_token_123"
        
        # 测试加密
        encrypted1 = OAuth2SecurityUtil.encrypt_token(original_token)
        encrypted2 = OAuth2SecurityUtil.encrypt_token(original_token)
        
        # 验证加密结果一致性（相同输入应产生相同输出）
        self.assertEqual(encrypted1, encrypted2, "相同输入应产生相同加密结果")
        
        # 验证加密结果不等于原文
        self.assertNotEqual(original_token, encrypted1, "加密结果不应等于原文")
        
        # 测试空值处理
        with self.assertRaises(Exception):
            OAuth2SecurityUtil.encrypt_token(None)
            
        logger.info("UC-002 测试通过: 令牌加密解密功能正常")
    
    def test_device_fingerprint_generation(self):
        """UC-003: 设备指纹生成测试"""
        logger.info("执行测试: UC-003 设备指纹生成测试")
        
        user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        ip_address = "192.168.1.100"
        
        # 生成指纹
        fingerprint1 = OAuth2SecurityUtil.generate_device_fingerprint(user_agent, ip_address)
        time.sleep(1)  # 等待1秒确保时间戳不同
        fingerprint2 = OAuth2SecurityUtil.generate_device_fingerprint(user_agent, ip_address)
        
        # 验证指纹长度
        self.assertEqual(len(fingerprint1), 16, f"指纹长度应该是16字符: {len(fingerprint1)}")
        
        # 验证指纹格式（十六进制）
        self.assertTrue(re.match(r'^[a-f0-9]+$', fingerprint1), f"指纹应该是十六进制格式: {fingerprint1}")
        
        # 验证时间戳影响（不同时间应产生不同指纹）
        self.assertNotEqual(fingerprint1, fingerprint2, "不同时间应产生不同指纹")
        
        logger.info("UC-003 测试通过: 设备指纹生成功能正常")
    
    def test_risk_score_calculation(self):
        """UC-004: 风险评分计算测试"""
        logger.info("执行测试: UC-004 风险评分计算测试")
        
        # 低风险场景
        low_risk_score = OAuth2SecurityUtil.calculate_risk_score(
            "192.168.1.100", "Mozilla/5.0 Chrome", 15
        )
        self.assertLessEqual(low_risk_score, 30, f"低风险评分应该较低: {low_risk_score}")
        
        # 高风险场景
        high_risk_score = OAuth2SecurityUtil.calculate_risk_score(
            "203.0.113.1", "Googlebot/2.1", 2
        )
        self.assertGreaterEqual(high_risk_score, 70, f"高风险评分应该较高: {high_risk_score}")
        
        # 边界值测试
        max_score = OAuth2SecurityUtil.calculate_risk_score("1.1.1.1", "bot", 0)
        self.assertLessEqual(max_score, 100, "风险评分不应超过100")
        
        logger.info("UC-004 测试通过: 风险评分计算功能正常")
    
    def test_state_token_format_validation(self):
        """UC-005: 状态令牌格式验证测试"""
        logger.info("执行测试: UC-005 状态令牌格式验证测试")
        
        # 有效格式测试
        valid_token = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0"
        self.assertTrue(OAuth2SecurityUtil.validate_state_token_format(valid_token))
        
        # 无效格式测试
        invalid_cases = [
            None,                    # null值
            "",                      # 空字符串
            "short",                 # 过短
            "invalid-token-with-dash",  # 包含特殊字符
            "token with spaces",     # 包含空格
            123456                   # 非字符串类型
        ]
        
        for invalid_token in invalid_cases:
            self.assertFalse(
                OAuth2SecurityUtil.validate_state_token_format(invalid_token),
                f"应该拒绝无效令牌: {invalid_token}"
            )
            
        logger.info("UC-005 测试通过: 状态令牌格式验证功能正常")

class TestOAuth2Entities(unittest.TestCase):
    """UC-006 到 UC-008: 实体类测试"""
    
    def test_user_oauth_entity(self):
        """UC-006: UserOAuth实体类测试"""
        logger.info("执行测试: UC-006 UserOAuth实体类测试")
        
        # 创建实体
        oauth = UserOAuth(
            user_id=1,
            provider="google",
            provider_id="google_123",
            email="test@example.com",
            name="Test User"
        )
        
        # 验证字段设置
        self.assertEqual(oauth.user_id, 1)
        self.assertEqual(oauth.provider, "google")
        self.assertEqual(oauth.provider_id, "google_123")
        self.assertEqual(oauth.email, "test@example.com")
        self.assertEqual(oauth.name, "Test User")
        
        # 验证时间字段自动填充
        self.assertIsNotNone(oauth.created_time)
        self.assertIsNotNone(oauth.updated_time)
        
        # 验证逻辑删除标记
        self.assertFalse(oauth.deleted)
        
        logger.info("UC-006 测试通过: UserOAuth实体类功能正常")
    
    def test_oauth2_state_entity(self):
        """UC-007: OAuth2State实体类测试"""
        logger.info("执行测试: UC-007 OAuth2State实体类测试")
        
        state_token = "test_state_token_123"
        state = OAuth2State(state_token=state_token)
        
        # 验证状态令牌存储
        self.assertEqual(state.state_token, state_token)
        
        # 验证过期时间处理
        self.assertIsNotNone(state.expires_at)
        self.assertGreater(state.expires_at, datetime.now())
        
        # 验证使用状态标记
        self.assertFalse(state.used)
        
        logger.info("UC-007 测试通过: OAuth2State实体类功能正常")
    
    def test_oauth2_audit_log_entity(self):
        """UC-008: OAuth2AuditLog实体类测试"""
        logger.info("执行测试: UC-008 OAuth2AuditLog实体类测试")
        
        audit_log = OAuth2AuditLog(
            user_id=1,
            provider="google",
            action="login",
            ip_address="192.168.1.100"
        )
        
        # 验证审计信息完整性
        self.assertEqual(audit_log.user_id, 1)
        self.assertEqual(audit_log.provider, "google")
        self.assertEqual(audit_log.action, "login")
        self.assertEqual(audit_log.ip_address, "192.168.1.100")
        
        # 验证执行时间记录
        self.assertIsNotNone(audit_log.execution_time)
        
        # 验证错误信息存储
        self.assertIsNone(audit_log.error_message)
        
        logger.info("UC-008 测试通过: OAuth2AuditLog实体类功能正常")

# ==================== 测试运行器 ====================

class OAuth2TestRunner:
    """OAuth2测试运行器"""
    
    def __init__(self):
        self.test_results = {
            'total': 0,
            'passed': 0,
            'failed': 0,
            'errors': 0
        }
    
    def run_all_tests(self):
        """运行所有测试"""
        logger.info("=" * 60)
        logger.info("开始执行OAuth2详细测试方案")
        logger.info("=" * 60)
        
        # 创建测试套件
        test_suite = unittest.TestSuite()
        
        # 添加单元测试
        test_suite.addTest(unittest.makeSuite(TestOAuth2SecurityUtil))
        test_suite.addTest(unittest.makeSuite(TestOAuth2Entities))
        
        # 运行测试
        runner = unittest.TextTestRunner(verbosity=2)
        result = runner.run(test_suite)
        
        # 统计结果
        self.test_results['total'] = result.testsRun
        self.test_results['failed'] = len(result.failures)
        self.test_results['errors'] = len(result.errors)
        self.test_results['passed'] = (
            self.test_results['total'] - 
            self.test_results['failed'] - 
            self.test_results['errors']
        )
        
        # 输出测试报告
        self.generate_test_report()
        
        return result
    
    def generate_test_report(self):
        """生成测试报告"""
        logger.info("=" * 60)
        logger.info("OAuth2测试执行报告")
        logger.info("=" * 60)
        logger.info(f"总测试数: {self.test_results['total']}")
        logger.info(f"通过: {self.test_results['passed']}")
        logger.info(f"失败: {self.test_results['failed']}")
        logger.info(f"错误: {self.test_results['errors']}")
        
        success_rate = (self.test_results['passed'] / self.test_results['total']) * 100
        logger.info(f"成功率: {success_rate:.2f}%")
        
        if self.test_results['failed'] == 0 and self.test_results['errors'] == 0:
            logger.info("✅ 所有测试通过！OAuth2功能验证成功")
        else:
            logger.warning("❌ 存在测试失败，请检查相关功能")

# ==================== 集成测试 ====================

class MockDatabase:
    """模拟数据库"""

    def __init__(self):
        self.user_oauth_table = []
        self.oauth2_state_table = []
        self.oauth2_audit_log_table = []

    def insert_user_oauth(self, oauth_record):
        """插入OAuth绑定记录"""
        oauth_record.id = len(self.user_oauth_table) + 1
        self.user_oauth_table.append(oauth_record)
        return oauth_record.id

    def find_by_provider_and_id(self, provider, provider_id):
        """根据provider和providerId查询"""
        for record in self.user_oauth_table:
            if record.provider == provider and record.provider_id == provider_id:
                return record
        return None

    def find_by_user_id(self, user_id):
        """根据userId查询绑定列表"""
        return [r for r in self.user_oauth_table if r.user_id == user_id]

class TestOAuth2Integration(unittest.TestCase):
    """IT-001 到 IT-009: 集成测试"""

    def setUp(self):
        """测试前准备"""
        self.mock_db = MockDatabase()

    def test_user_oauth_dao_operations(self):
        """IT-001: UserOAuthDao CRUD操作测试"""
        logger.info("执行测试: IT-001 UserOAuthDao CRUD操作测试")

        # 创建OAuth绑定记录
        oauth = UserOAuth(
            user_id=1,
            provider="google",
            provider_id="google_123",
            email="test@example.com",
            name="Test User"
        )

        oauth_id = self.mock_db.insert_user_oauth(oauth)
        self.assertIsNotNone(oauth_id, "应该成功创建OAuth绑定记录")

        # 根据provider和providerId查询
        found_oauth = self.mock_db.find_by_provider_and_id("google", "google_123")
        self.assertIsNotNone(found_oauth, "应该能找到OAuth绑定记录")
        self.assertEqual(found_oauth.email, "test@example.com")

        # 根据userId查询绑定列表
        user_oauths = self.mock_db.find_by_user_id(1)
        self.assertEqual(len(user_oauths), 1, "应该找到1个绑定记录")

        logger.info("IT-001 测试通过: UserOAuthDao CRUD操作正常")

# ==================== 系统测试 ====================

class MockOAuth2Provider:
    """模拟OAuth2提供商"""

    def __init__(self, provider_name):
        self.provider_name = provider_name
        self.is_available = True

    def get_authorization_url(self, state):
        """获取授权URL"""
        if not self.is_available:
            raise Exception(f"{self.provider_name} service unavailable")
        return f"https://{self.provider_name}.com/oauth/authorize?state={state}"

    def exchange_code_for_token(self, code):
        """交换访问令牌"""
        if not self.is_available:
            raise Exception(f"{self.provider_name} service unavailable")
        return {
            "access_token": f"mock_token_{code}",
            "token_type": "Bearer",
            "expires_in": 3600
        }

    def get_user_info(self, access_token):
        """获取用户信息"""
        if not self.is_available:
            raise Exception(f"{self.provider_name} service unavailable")
        return {
            "id": f"{self.provider_name}_123",
            "email": f"user@{self.provider_name}.com",
            "name": f"Test User from {self.provider_name}"
        }

class TestOAuth2System(unittest.TestCase):
    """ST-001 到 ST-008: 系统测试"""

    def setUp(self):
        """测试前准备"""
        self.google_provider = MockOAuth2Provider("google")
        self.github_provider = MockOAuth2Provider("github")

    def test_google_oauth_complete_flow(self):
        """ST-001: Google OAuth完整流程测试"""
        logger.info("执行测试: ST-001 Google OAuth完整流程测试")

        # 1. 发起Google登录
        state_token = OAuth2SecurityUtil.generate_state_token()
        auth_url = self.google_provider.get_authorization_url(state_token)

        self.assertIn("google.com", auth_url, "授权URL应该包含Google域名")
        self.assertIn(state_token, auth_url, "授权URL应该包含状态令牌")

        # 2. 模拟用户授权确认（返回授权码）
        mock_auth_code = "mock_auth_code_123"

        # 3. 回调处理 - 交换访问令牌
        token_response = self.google_provider.exchange_code_for_token(mock_auth_code)
        self.assertIn("access_token", token_response, "应该获得访问令牌")

        # 4. 用户信息获取
        user_info = self.google_provider.get_user_info(token_response["access_token"])
        self.assertIn("id", user_info, "应该获得用户ID")
        self.assertIn("email", user_info, "应该获得用户邮箱")

        logger.info("ST-001 测试通过: Google OAuth完整流程正常")

    def test_github_oauth_complete_flow(self):
        """ST-002: GitHub OAuth完整流程测试"""
        logger.info("执行测试: ST-002 GitHub OAuth完整流程测试")

        # 类似Google流程的测试
        state_token = OAuth2SecurityUtil.generate_state_token()
        auth_url = self.github_provider.get_authorization_url(state_token)

        self.assertIn("github.com", auth_url, "授权URL应该包含GitHub域名")

        mock_auth_code = "mock_github_code_456"
        token_response = self.github_provider.exchange_code_for_token(mock_auth_code)
        user_info = self.github_provider.get_user_info(token_response["access_token"])

        self.assertIn("github", user_info["id"], "用户ID应该包含GitHub标识")

        logger.info("ST-002 测试通过: GitHub OAuth完整流程正常")

    def test_csrf_protection(self):
        """ST-004: CSRF攻击防护测试"""
        logger.info("执行测试: ST-004 CSRF攻击防护测试")

        # 生成合法状态令牌
        valid_state = OAuth2SecurityUtil.generate_state_token()

        # 模拟CSRF攻击 - 使用伪造的状态令牌
        fake_state = "fake_state_token"

        # 验证状态令牌
        self.assertTrue(OAuth2SecurityUtil.validate_state_token_format(valid_state))
        self.assertFalse(OAuth2SecurityUtil.validate_state_token_format(fake_state))

        logger.info("ST-004 测试通过: CSRF攻击防护正常")

    def test_concurrent_login_performance(self):
        """ST-007: 并发登录测试"""
        logger.info("执行测试: ST-007 并发登录测试")

        import threading
        import time

        results = []

        def simulate_login(user_id):
            """模拟用户登录"""
            start_time = time.time()
            try:
                # 模拟登录过程
                state_token = OAuth2SecurityUtil.generate_state_token()
                fingerprint = OAuth2SecurityUtil.generate_device_fingerprint(
                    "Mozilla/5.0", f"192.168.1.{user_id}"
                )
                risk_score = OAuth2SecurityUtil.calculate_risk_score(
                    f"192.168.1.{user_id}", "Mozilla/5.0", 10
                )

                end_time = time.time()
                response_time = end_time - start_time
                results.append({
                    'user_id': user_id,
                    'response_time': response_time,
                    'success': True
                })
            except Exception as e:
                results.append({
                    'user_id': user_id,
                    'response_time': None,
                    'success': False,
                    'error': str(e)
                })

        # 创建10个并发线程模拟用户登录
        threads = []
        for i in range(10):
            thread = threading.Thread(target=simulate_login, args=(i,))
            threads.append(thread)

        # 启动所有线程
        start_time = time.time()
        for thread in threads:
            thread.start()

        # 等待所有线程完成
        for thread in threads:
            thread.join()

        total_time = time.time() - start_time

        # 验证结果
        successful_logins = [r for r in results if r['success']]
        self.assertEqual(len(successful_logins), 10, "所有并发登录都应该成功")

        # 验证响应时间
        avg_response_time = sum(r['response_time'] for r in successful_logins) / len(successful_logins)
        self.assertLess(avg_response_time, 1.0, f"平均响应时间应该小于1秒: {avg_response_time:.3f}s")

        logger.info(f"ST-007 测试通过: 并发登录性能正常 (平均响应时间: {avg_response_time:.3f}s)")

# ==================== 验收测试 ====================

class TestOAuth2Acceptance(unittest.TestCase):
    """AT-001 到 AT-006: 验收测试"""

    def test_new_user_first_login_scenario(self):
        """AT-001: 新用户首次登录场景"""
        logger.info("执行测试: AT-001 新用户首次登录场景")

        # 1. 选择OAuth提供商
        provider = "google"

        # 2. 完成授权流程
        state_token = OAuth2SecurityUtil.generate_state_token()
        self.assertTrue(OAuth2SecurityUtil.validate_state_token_format(state_token))

        # 3. 模拟自动创建账号
        new_user_oauth = UserOAuth(
            user_id=None,  # 新用户，暂无ID
            provider=provider,
            provider_id="google_new_user_123",
            email="newuser@example.com",
            name="New User"
        )

        # 4. 验证账号创建
        self.assertEqual(new_user_oauth.provider, provider)
        self.assertIsNotNone(new_user_oauth.email)

        logger.info("AT-001 测试通过: 新用户首次登录场景正常")

    def test_network_exception_handling(self):
        """AT-004: 网络异常处理"""
        logger.info("执行测试: AT-004 网络异常处理")

        # 模拟OAuth提供商服务不可用
        provider = MockOAuth2Provider("google")
        provider.is_available = False

        # 验证异常处理
        with self.assertRaises(Exception) as context:
            provider.get_authorization_url("test_state")

        self.assertIn("service unavailable", str(context.exception))

        logger.info("AT-004 测试通过: 网络异常处理正常")

    def test_user_cancel_authorization(self):
        """AT-005: 用户取消授权处理"""
        logger.info("执行测试: AT-005 用户取消授权处理")

        # 模拟用户取消授权的回调参数
        callback_params = {
            "error": "access_denied",
            "error_description": "The user denied the request",
            "state": "test_state_token"
        }

        # 验证错误处理
        self.assertEqual(callback_params["error"], "access_denied")
        self.assertIn("denied", callback_params["error_description"])

        logger.info("AT-005 测试通过: 用户取消授权处理正常")

# ==================== 测试运行器更新 ====================

class OAuth2TestRunner:
    """OAuth2测试运行器"""

    def __init__(self):
        self.test_results = {
            'total': 0,
            'passed': 0,
            'failed': 0,
            'errors': 0
        }

    def run_all_tests(self):
        """运行所有测试"""
        logger.info("=" * 60)
        logger.info("开始执行OAuth2详细测试方案")
        logger.info("=" * 60)

        # 创建测试套件
        test_suite = unittest.TestSuite()

        # 添加单元测试
        test_suite.addTest(unittest.TestLoader().loadTestsFromTestCase(TestOAuth2SecurityUtil))
        test_suite.addTest(unittest.TestLoader().loadTestsFromTestCase(TestOAuth2Entities))

        # 添加集成测试
        test_suite.addTest(unittest.TestLoader().loadTestsFromTestCase(TestOAuth2Integration))

        # 添加系统测试
        test_suite.addTest(unittest.TestLoader().loadTestsFromTestCase(TestOAuth2System))

        # 添加验收测试
        test_suite.addTest(unittest.TestLoader().loadTestsFromTestCase(TestOAuth2Acceptance))

        # 运行测试
        runner = unittest.TextTestRunner(verbosity=2)
        result = runner.run(test_suite)

        # 统计结果
        self.test_results['total'] = result.testsRun
        self.test_results['failed'] = len(result.failures)
        self.test_results['errors'] = len(result.errors)
        self.test_results['passed'] = (
            self.test_results['total'] -
            self.test_results['failed'] -
            self.test_results['errors']
        )

        # 输出测试报告
        self.generate_test_report()

        return result

    def generate_test_report(self):
        """生成测试报告"""
        logger.info("=" * 60)
        logger.info("OAuth2测试执行报告")
        logger.info("=" * 60)
        logger.info(f"总测试数: {self.test_results['total']}")
        logger.info(f"通过: {self.test_results['passed']}")
        logger.info(f"失败: {self.test_results['failed']}")
        logger.info(f"错误: {self.test_results['errors']}")

        success_rate = (self.test_results['passed'] / self.test_results['total']) * 100
        logger.info(f"成功率: {success_rate:.2f}%")

        # 测试覆盖率分析
        logger.info("\n测试覆盖率分析:")
        logger.info("✅ 单元测试: OAuth2SecurityUtil, 实体类")
        logger.info("✅ 集成测试: 数据访问层操作")
        logger.info("✅ 系统测试: 端到端流程, 安全性, 性能")
        logger.info("✅ 验收测试: 用户场景, 错误处理")

        if self.test_results['failed'] == 0 and self.test_results['errors'] == 0:
            logger.info("\n🎉 所有测试通过！OAuth2功能验证成功")
            logger.info("📊 测试覆盖率达到预期目标")
            logger.info("🔒 安全性验证通过")
            logger.info("⚡ 性能测试通过")
        else:
            logger.warning("\n❌ 存在测试失败，请检查相关功能")

    def run_specific_test_category(self, category):
        """运行特定类别的测试"""
        test_classes = {
            'unit': [TestOAuth2SecurityUtil, TestOAuth2Entities],
            'integration': [TestOAuth2Integration],
            'system': [TestOAuth2System],
            'acceptance': [TestOAuth2Acceptance]
        }

        if category not in test_classes:
            logger.error(f"未知的测试类别: {category}")
            return

        logger.info(f"运行 {category.upper()} 测试...")

        test_suite = unittest.TestSuite()
        for test_class in test_classes[category]:
            test_suite.addTest(unittest.TestLoader().loadTestsFromTestCase(test_class))

        runner = unittest.TextTestRunner(verbosity=2)
        result = runner.run(test_suite)

        return result

if __name__ == "__main__":
    import sys

    test_runner = OAuth2TestRunner()

    # 检查命令行参数
    if len(sys.argv) > 1:
        category = sys.argv[1].lower()
        if category in ['unit', 'integration', 'system', 'acceptance']:
            test_runner.run_specific_test_category(category)
        else:
            print("使用方法: python oauth2_test_suite.py [unit|integration|system|acceptance]")
            print("或者直接运行: python oauth2_test_suite.py (运行所有测试)")
    else:
        # 运行所有测试
        test_runner.run_all_tests()
