#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAuth2测试配置文件
基于文档: docs/07-16 23:15 OAuth2详细测试方案.md
"""

import os
from datetime import datetime, timedelta

class OAuth2TestConfig:
    """OAuth2测试配置类"""
    
    # 测试环境配置 - 使用项目实际数据库
    TEST_ENV = {
        'database': {
            'host': 'your-db-host.example.com',
            'port': 32232,
            'database': 'chat',
            'username': 'root',
            'password': 'wsq54wg4',
            'url': 'jdbc:mysql://your-db-host.example.com:32232/chat?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true',
            'driver': 'com.mysql.cj.jdbc.Driver'
        },
        'redis': {
            'host': 'your-db-host.example.com',
            'port': 31122,
            'username': 'default',
            'password': 'pt7tv2k4',
            'database': 0
        }
    }
    
    # OAuth2提供商测试配置
    OAUTH2_PROVIDERS = {
        'google': {
            'client_id': 'test-google-client-id',
            'client_secret': 'test-google-client-secret',
            'authorization_url': 'https://accounts.google.com/o/oauth2/auth',
            'token_url': 'https://oauth2.googleapis.com/token',
            'user_info_url': 'https://www.googleapis.com/oauth2/v2/userinfo',
            'scope': 'openid email profile'
        },
        'github': {
            'client_id': 'test-github-client-id',
            'client_secret': 'test-github-client-secret',
            'authorization_url': 'https://github.com/login/oauth/authorize',
            'token_url': 'https://github.com/login/oauth/access_token',
            'user_info_url': 'https://api.github.com/user',
            'scope': 'user:email'
        }
    }
    
    # 安全配置
    SECURITY_CONFIG = {
        'state_token_length': 64,
        'state_token_expiry_minutes': 10,
        'device_fingerprint_length': 16,
        'encryption_key': 'test_encryption_key_123',
        'max_risk_score': 100,
        'csrf_protection_enabled': True
    }
    
    # 测试数据配置
    TEST_DATA = {
        'users': [
            {
                'id': 1,
                'username': 'testuser1',
                'email': 'test1@example.com',
                'password': 'encrypted_password_1'
            },
            {
                'id': 2,
                'username': 'testuser2',
                'email': 'test2@example.com',
                'password': 'encrypted_password_2'
            }
        ],
        'oauth_bindings': [
            {
                'user_id': 1,
                'provider': 'google',
                'provider_id': 'google_123',
                'email': 'test1@example.com',
                'name': 'Test User 1',
                'access_token': 'mock_google_token_1',
                'refresh_token': 'mock_google_refresh_1'
            },
            {
                'user_id': 2,
                'provider': 'github',
                'provider_id': 'github_456',
                'email': 'test2@example.com',
                'name': 'Test User 2',
                'access_token': 'mock_github_token_2',
                'refresh_token': 'mock_github_refresh_2'
            }
        ]
    }
    
    # 测试场景配置
    TEST_SCENARIOS = {
        'low_risk': {
            'ip_address': '192.168.1.100',
            'user_agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'login_count': 15,
            'expected_risk_score_max': 30
        },
        'medium_risk': {
            'ip_address': '203.0.113.1',
            'user_agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'login_count': 7,
            'expected_risk_score_range': (30, 70)
        },
        'high_risk': {
            'ip_address': '203.0.113.1',
            'user_agent': 'Googlebot/2.1 (+http://www.google.com/bot.html)',
            'login_count': 2,
            'expected_risk_score_min': 70
        }
    }
    
    # 性能测试配置
    PERFORMANCE_CONFIG = {
        'concurrent_users': 100,
        'max_response_time_seconds': 2.0,
        'test_duration_seconds': 60,
        'ramp_up_time_seconds': 10
    }
    
    # 测试覆盖率要求
    COVERAGE_REQUIREMENTS = {
        'line_coverage': 90,
        'branch_coverage': 85,
        'method_coverage': 95,
        'class_coverage': 100
    }
    
    # 错误模拟配置
    ERROR_SIMULATION = {
        'network_timeout': {
            'enabled': True,
            'timeout_seconds': 5,
            'retry_attempts': 3
        },
        'service_unavailable': {
            'enabled': True,
            'error_rate': 0.1  # 10%错误率
        },
        'database_connection_failure': {
            'enabled': True,
            'failure_rate': 0.05  # 5%失败率
        }
    }
    
    @classmethod
    def get_test_database_url(cls):
        """获取测试数据库URL"""
        return cls.TEST_ENV['database']['url']
    
    @classmethod
    def get_oauth_provider_config(cls, provider_name):
        """获取OAuth提供商配置"""
        return cls.OAUTH2_PROVIDERS.get(provider_name)
    
    @classmethod
    def get_test_user(cls, user_id):
        """获取测试用户"""
        for user in cls.TEST_DATA['users']:
            if user['id'] == user_id:
                return user
        return None
    
    @classmethod
    def get_test_oauth_binding(cls, user_id, provider):
        """获取测试OAuth绑定"""
        for binding in cls.TEST_DATA['oauth_bindings']:
            if binding['user_id'] == user_id and binding['provider'] == provider:
                return binding
        return None
    
    @classmethod
    def get_risk_scenario(cls, scenario_name):
        """获取风险测试场景"""
        return cls.TEST_SCENARIOS.get(scenario_name)
    
    @classmethod
    def is_performance_test_enabled(cls):
        """检查是否启用性能测试"""
        return os.getenv('ENABLE_PERFORMANCE_TESTS', 'false').lower() == 'true'
    
    @classmethod
    def is_integration_test_enabled(cls):
        """检查是否启用集成测试"""
        return os.getenv('ENABLE_INTEGRATION_TESTS', 'true').lower() == 'true'
    
    @classmethod
    def get_mock_responses(cls):
        """获取模拟响应配置"""
        return {
            'google_user_info': {
                'id': 'google_mock_123',
                'email': 'mockuser@gmail.com',
                'verified_email': True,
                'name': 'Mock Google User',
                'given_name': 'Mock',
                'family_name': 'User',
                'picture': 'https://lh3.googleusercontent.com/mock'
            },
            'github_user_info': {
                'id': 'github_mock_456',
                'login': 'mockuser',
                'email': 'mockuser@github.com',
                'name': 'Mock GitHub User',
                'avatar_url': 'https://avatars.githubusercontent.com/mock'
            },
            'google_token_response': {
                'access_token': 'mock_google_access_token',
                'token_type': 'Bearer',
                'expires_in': 3600,
                'refresh_token': 'mock_google_refresh_token',
                'scope': 'openid email profile'
            },
            'github_token_response': {
                'access_token': 'mock_github_access_token',
                'token_type': 'bearer',
                'scope': 'user:email'
            }
        }
    
    @classmethod
    def get_test_sql_scripts(cls):
        """获取测试SQL脚本"""
        return {
            'setup': """
                -- 创建测试用户表
                CREATE TABLE IF NOT EXISTS t_user (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(50) NOT NULL UNIQUE,
                    email VARCHAR(100) NOT NULL UNIQUE,
                    password VARCHAR(255) NOT NULL,
                    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    deleted BOOLEAN DEFAULT FALSE
                );
                
                -- 创建OAuth绑定表
                CREATE TABLE IF NOT EXISTS t_user_oauth (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    provider VARCHAR(20) NOT NULL,
                    provider_id VARCHAR(100) NOT NULL,
                    email VARCHAR(100),
                    name VARCHAR(100),
                    access_token TEXT,
                    refresh_token TEXT,
                    expires_at TIMESTAMP,
                    last_login_time TIMESTAMP,
                    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    deleted BOOLEAN DEFAULT FALSE,
                    UNIQUE KEY uk_provider_id (provider, provider_id),
                    KEY idx_user_id (user_id)
                );
                
                -- 创建OAuth状态表
                CREATE TABLE IF NOT EXISTS t_oauth2_state (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    state_token VARCHAR(255) NOT NULL UNIQUE,
                    expires_at TIMESTAMP NOT NULL,
                    used BOOLEAN DEFAULT FALSE,
                    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                
                -- 创建OAuth审计日志表
                CREATE TABLE IF NOT EXISTS t_oauth2_audit_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT,
                    provider VARCHAR(20),
                    action VARCHAR(50) NOT NULL,
                    ip_address VARCHAR(45),
                    user_agent TEXT,
                    device_fingerprint VARCHAR(32),
                    risk_score INT,
                    status VARCHAR(20) NOT NULL,
                    error_message TEXT,
                    execution_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """,
            'cleanup': """
                DROP TABLE IF EXISTS t_oauth2_audit_log;
                DROP TABLE IF EXISTS t_oauth2_state;
                DROP TABLE IF EXISTS t_user_oauth;
                DROP TABLE IF EXISTS t_user;
            """,
            'test_data': """
                -- 插入测试用户
                INSERT INTO t_user (id, username, email, password) VALUES 
                (1, 'testuser1', 'test1@example.com', 'encrypted_password_1'),
                (2, 'testuser2', 'test2@example.com', 'encrypted_password_2');
                
                -- 插入测试OAuth绑定
                INSERT INTO t_user_oauth (user_id, provider, provider_id, email, name) VALUES
                (1, 'google', 'google_123', 'test1@example.com', 'Test User 1'),
                (2, 'github', 'github_456', 'test2@example.com', 'Test User 2');
            """
        }

# 测试环境检查
class TestEnvironmentChecker:
    """测试环境检查器"""
    
    @staticmethod
    def check_dependencies():
        """检查测试依赖"""
        required_modules = [
            'unittest', 'hashlib', 'uuid', 'time', 're', 'json',
            'logging', 'threading', 'datetime'
        ]
        
        missing_modules = []
        for module in required_modules:
            try:
                __import__(module)
            except ImportError:
                missing_modules.append(module)
        
        if missing_modules:
            raise ImportError(f"缺少必要的模块: {', '.join(missing_modules)}")
        
        return True
    
    @staticmethod
    def check_test_environment():
        """检查测试环境"""
        checks = {
            'python_version': TestEnvironmentChecker._check_python_version(),
            'dependencies': TestEnvironmentChecker.check_dependencies(),
            'config_valid': TestEnvironmentChecker._check_config_validity()
        }
        
        return all(checks.values()), checks
    
    @staticmethod
    def _check_python_version():
        """检查Python版本"""
        import sys
        return sys.version_info >= (3, 6)
    
    @staticmethod
    def _check_config_validity():
        """检查配置有效性"""
        try:
            config = OAuth2TestConfig()
            return (
                config.OAUTH2_PROVIDERS and
                config.SECURITY_CONFIG and
                config.TEST_DATA
            )
        except Exception:
            return False

if __name__ == "__main__":
    # 运行环境检查
    checker = TestEnvironmentChecker()
    is_valid, checks = checker.check_test_environment()
    
    print("OAuth2测试环境检查结果:")
    print("=" * 40)
    for check_name, result in checks.items():
        status = "✅ 通过" if result else "❌ 失败"
        print(f"{check_name}: {status}")
    
    if is_valid:
        print("\n🎉 测试环境检查通过，可以开始运行测试！")
    else:
        print("\n❌ 测试环境检查失败，请修复相关问题后重试。")
