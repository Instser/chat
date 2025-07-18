#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
UserPaymentStatusController 测试配置文件
测试时间: 2025-07-17 00:31
版本: v1.0
"""

# 测试环境配置
TEST_CONFIG = {
    # API配置
    'api': {
        'base_url': 'http://localhost:8080/api',
        'timeout': 30,
        'retry_count': 3
    },
    
    # 认证配置
    'auth': {
        'username': 'admin',
        'password': 'admin123'
    },
    
    # 测试用户配置
    'test_users': {
        'admin': {
            'username': 'admin',
            'password': 'admin123',
            'expected_role': 'admin'
        },
        'test_user': {
            'username': 'testuser',
            'password': '123456',
            'expected_role': 'user'
        }
    },
    
    # 测试端点配置
    'endpoints': {
        'overview': '/user/status/overview',
        'never_paid': '/user/status/new',
        'free_only': '/user/status/free-only',
        'active_paid': '/user/status/active-paid',
        'total_amount': '/user/status/total-amount',
        'first_payment_time': '/user/status/first-payment-time',
        'admin_query': '/user/status/admin/{user_id}'
    },
    
    # 预期响应结构
    'expected_responses': {
        'overview': {
            'required_fields': [
                'userId', 'neverPaid', 'onlyFreeUser', 'activePaidUser',
                'statusCategory', 'totalPaidAmount', 'firstPaymentTime'
            ],
            'status_categories': ['NEVER_PAID', 'FREE_ONLY', 'ACTIVE_PAID', 'INACTIVE_PAID']
        },
        'boolean_endpoints': ['never_paid', 'free_only', 'active_paid'],
        'numeric_endpoints': ['total_amount'],
        'datetime_endpoints': ['first_payment_time']
    },
    
    # 测试数据
    'test_data': {
        'admin_query_user_ids': [1, 2, 999999],  # 包含存在和不存在的用户ID
        'invalid_user_ids': [-1, 0, 'invalid']
    }
}

# 错误码映射
ERROR_CODES = {
    200: 'SUCCESS',
    400: 'BAD_REQUEST',
    401: 'UNAUTHORIZED',
    403: 'FORBIDDEN',
    404: 'NOT_FOUND',
    500: 'INTERNAL_SERVER_ERROR'
}

# 测试场景配置
TEST_SCENARIOS = {
    'normal_flow': {
        'description': '正常流程测试',
        'tests': [
            'login',
            'overview',
            'never_paid',
            'free_only',
            'active_paid',
            'total_amount',
            'first_payment_time',
            'admin_query'
        ]
    },
    'security_test': {
        'description': '安全性测试',
        'tests': [
            'unauthorized_access',
            'invalid_token',
            'admin_permission_check'
        ]
    },
    'edge_cases': {
        'description': '边界情况测试',
        'tests': [
            'invalid_user_id',
            'non_existent_user',
            'malformed_request'
        ]
    }
}

def get_test_config():
    """获取测试配置"""
    return TEST_CONFIG

def get_error_codes():
    """获取错误码映射"""
    return ERROR_CODES

def get_test_scenarios():
    """获取测试场景"""
    return TEST_SCENARIOS
