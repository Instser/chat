#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAuth2数据库测试脚本
使用项目实际数据库进行测试，测试完成后自动清理测试数据

依赖安装:
pip install pymysql redis

使用方法:
python oauth2_database_test.py
"""

import sys
import subprocess
import pymysql
import redis
import hashlib
import uuid
import time
import json
import logging
from datetime import datetime, timedelta
from contextlib import contextmanager

# 自动安装依赖
def install_dependencies():
    """自动安装必要的依赖"""
    dependencies = ['pymysql', 'redis', 'cryptography']
    for dep in dependencies:
        try:
            __import__(dep)
        except ImportError:
            print(f"正在安装 {dep}...")
            subprocess.check_call([sys.executable, '-m', 'pip', 'install', dep])

try:
    install_dependencies()
except Exception as e:
    print(f"依赖安装失败: {e}")
    sys.exit(1)

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class DatabaseTestManager:
    """数据库测试管理器"""

    def __init__(self):
        # 直接使用项目数据库配置
        self.db_config = {
            'host': 'your-db-host.example.com',
            'port': 32232,
            'user': 'root',
            'password': 'wsq54wg4',
            'database': 'chat',
            'charset': 'utf8mb4'
        }
        self.redis_config = {
            'host': 'your-db-host.example.com',
            'port': 31122,
            'username': 'default',
            'password': 'pt7tv2k4',
            'db': 0
        }
        self.test_data_ids = {
            'users': [],
            'oauth_bindings': [],
            'oauth_states': [],
            'audit_logs': []
        }
    
    @contextmanager
    def get_db_connection(self):
        """获取数据库连接"""
        connection = None
        try:
            connection = pymysql.connect(**self.db_config, cursorclass=pymysql.cursors.DictCursor)
            logger.info("✅ 数据库连接成功")
            yield connection
        except Exception as e:
            logger.error(f"❌ 数据库连接失败: {e}")
            raise
        finally:
            if connection:
                connection.close()
                logger.info("🔒 数据库连接已关闭")
    
    def get_redis_connection(self):
        """获取Redis连接"""
        try:
            r = redis.Redis(**self.redis_config, decode_responses=True)
            # 测试连接
            r.ping()
            logger.info("✅ Redis连接成功")
            return r
        except Exception as e:
            logger.error(f"❌ Redis连接失败: {e}")
            raise
    
    def create_test_data(self):
        """创建测试数据"""
        logger.info("🔧 开始创建测试数据...")
        
        with self.get_db_connection() as conn:
            cursor = conn.cursor()
            
            try:
                # 1. 创建测试用户
                test_users = [
                    {
                        'username': f'oauth_test_user_{int(time.time())}',
                        'email': f'oauth_test_{int(time.time())}@example.com',
                        'password': hashlib.sha256('test_password_123'.encode()).hexdigest(),
                        'role': 'user',
                        'status': 1
                    },
                    {
                        'username': f'oauth_test_user2_{int(time.time())}',
                        'email': f'oauth_test2_{int(time.time())}@example.com',
                        'password': hashlib.sha256('test_password_456'.encode()).hexdigest(),
                        'role': 'user',
                        'status': 1
                    }
                ]

                for user in test_users:
                    cursor.execute("""
                        INSERT INTO t_user (username, email, password, role, status, create_time, update_time, deleted)
                        VALUES (%(username)s, %(email)s, %(password)s, %(role)s, %(status)s, NOW(), NOW(), 0)
                    """, user)
                    user_id = cursor.lastrowid
                    self.test_data_ids['users'].append(user_id)
                    logger.info(f"✅ 创建测试用户: {user['username']} (ID: {user_id})")
                
                # 2. 创建OAuth绑定测试数据
                oauth_bindings = [
                    {
                        'user_id': self.test_data_ids['users'][0],
                        'provider': 'google',
                        'provider_id': f'google_test_{int(time.time())}',
                        'email': test_users[0]['email'],
                        'name': 'Test Google User',
                        'access_token': f'mock_google_token_{int(time.time())}',
                        'refresh_token': f'mock_google_refresh_{int(time.time())}',
                        'login_count': 0,
                        'status': 1
                    },
                    {
                        'user_id': self.test_data_ids['users'][1],
                        'provider': 'github',
                        'provider_id': f'github_test_{int(time.time())}',
                        'email': test_users[1]['email'],
                        'name': 'Test GitHub User',
                        'access_token': f'mock_github_token_{int(time.time())}',
                        'refresh_token': f'mock_github_refresh_{int(time.time())}',
                        'login_count': 0,
                        'status': 1
                    }
                ]

                for binding in oauth_bindings:
                    cursor.execute("""
                        INSERT INTO t_user_oauth (user_id, provider, provider_id, email, name,
                                                access_token, refresh_token, login_count, status,
                                                create_time, update_time, deleted)
                        VALUES (%(user_id)s, %(provider)s, %(provider_id)s, %(email)s, %(name)s,
                                %(access_token)s, %(refresh_token)s, %(login_count)s, %(status)s,
                                NOW(), NOW(), 0)
                    """, binding)
                    oauth_id = cursor.lastrowid
                    self.test_data_ids['oauth_bindings'].append(oauth_id)
                    logger.info(f"✅ 创建OAuth绑定: {binding['provider']} (ID: {oauth_id})")
                
                # 3. 创建OAuth状态测试数据
                state_tokens = [
                    {
                        'state_token': self.generate_state_token(),
                        'provider': 'google',
                        'client_ip': '192.168.1.100',
                        'user_agent': 'Mozilla/5.0 Test Browser',
                        'expires_at': datetime.now() + timedelta(minutes=10),
                        'used': 0
                    },
                    {
                        'state_token': self.generate_state_token(),
                        'provider': 'github',
                        'client_ip': '192.168.1.101',
                        'user_agent': 'Mozilla/5.0 Test Browser',
                        'expires_at': datetime.now() + timedelta(minutes=10),
                        'used': 0
                    }
                ]

                for state in state_tokens:
                    cursor.execute("""
                        INSERT INTO t_oauth2_state (state_token, provider, client_ip, user_agent,
                                                  expires_at, used, create_time)
                        VALUES (%(state_token)s, %(provider)s, %(client_ip)s, %(user_agent)s,
                                %(expires_at)s, %(used)s, NOW())
                    """, state)
                    state_id = cursor.lastrowid
                    self.test_data_ids['oauth_states'].append(state_id)
                    logger.info(f"✅ 创建OAuth状态: {state['state_token'][:20]}... (ID: {state_id})")
                
                # 4. 创建审计日志测试数据
                audit_logs = [
                    {
                        'user_id': self.test_data_ids['users'][0],
                        'provider': 'google',
                        'action': 'login_attempt',
                        'status': 'success',
                        'client_ip': '192.168.1.100',
                        'user_agent': 'Mozilla/5.0 Test Browser',
                        'execution_time': 150
                    },
                    {
                        'user_id': self.test_data_ids['users'][1],
                        'provider': 'github',
                        'action': 'login_attempt',
                        'status': 'success',
                        'client_ip': '203.0.113.1',
                        'user_agent': 'Mozilla/5.0 Test Browser',
                        'execution_time': 200
                    }
                ]

                for log in audit_logs:
                    cursor.execute("""
                        INSERT INTO t_oauth2_audit_log (user_id, provider, action, status, client_ip,
                                                      user_agent, execution_time, create_time)
                        VALUES (%(user_id)s, %(provider)s, %(action)s, %(status)s, %(client_ip)s,
                                %(user_agent)s, %(execution_time)s, NOW())
                    """, log)
                    log_id = cursor.lastrowid
                    self.test_data_ids['audit_logs'].append(log_id)
                    logger.info(f"✅ 创建审计日志: {log['action']} (ID: {log_id})")
                
                conn.commit()
                logger.info("🎉 测试数据创建完成")
                
            except Exception as e:
                conn.rollback()
                logger.error(f"❌ 创建测试数据失败: {e}")
                raise
    
    def run_oauth2_tests(self):
        """运行OAuth2功能测试"""
        logger.info("🧪 开始运行OAuth2功能测试...")
        
        test_results = {
            'total': 0,
            'passed': 0,
            'failed': 0,
            'details': []
        }
        
        # 测试1: 数据库连接测试
        test_results['total'] += 1
        try:
            with self.get_db_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("SELECT 1")
                result = cursor.fetchone()
                if result:
                    test_results['passed'] += 1
                    test_results['details'].append("✅ 数据库连接测试通过")
                    logger.info("✅ 数据库连接测试通过")
        except Exception as e:
            test_results['failed'] += 1
            test_results['details'].append(f"❌ 数据库连接测试失败: {e}")
            logger.error(f"❌ 数据库连接测试失败: {e}")
        
        # 测试2: Redis连接测试
        test_results['total'] += 1
        try:
            r = self.get_redis_connection()
            r.set('oauth_test_key', 'test_value', ex=60)
            value = r.get('oauth_test_key')
            if value == 'test_value':
                test_results['passed'] += 1
                test_results['details'].append("✅ Redis连接测试通过")
                logger.info("✅ Redis连接测试通过")
            r.delete('oauth_test_key')
        except Exception as e:
            test_results['failed'] += 1
            test_results['details'].append(f"❌ Redis连接测试失败: {e}")
            logger.error(f"❌ Redis连接测试失败: {e}")
        
        # 测试3: OAuth用户查询测试
        test_results['total'] += 1
        try:
            with self.get_db_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("""
                    SELECT uo.*, u.username, u.email 
                    FROM t_user_oauth uo 
                    JOIN t_user u ON uo.user_id = u.id 
                    WHERE uo.id IN %s
                """, (tuple(self.test_data_ids['oauth_bindings']),))
                results = cursor.fetchall()
                if len(results) == len(self.test_data_ids['oauth_bindings']):
                    test_results['passed'] += 1
                    test_results['details'].append("✅ OAuth用户查询测试通过")
                    logger.info(f"✅ OAuth用户查询测试通过，查询到 {len(results)} 条记录")
                else:
                    test_results['failed'] += 1
                    test_results['details'].append("❌ OAuth用户查询测试失败：记录数不匹配")
        except Exception as e:
            test_results['failed'] += 1
            test_results['details'].append(f"❌ OAuth用户查询测试失败: {e}")
            logger.error(f"❌ OAuth用户查询测试失败: {e}")
        
        # 测试4: OAuth状态验证测试
        test_results['total'] += 1
        try:
            with self.get_db_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("""
                    SELECT * FROM t_oauth2_state 
                    WHERE id IN %s AND used = 0 AND expires_at > NOW()
                """, (tuple(self.test_data_ids['oauth_states']),))
                results = cursor.fetchall()
                if len(results) > 0:
                    test_results['passed'] += 1
                    test_results['details'].append("✅ OAuth状态验证测试通过")
                    logger.info(f"✅ OAuth状态验证测试通过，找到 {len(results)} 个有效状态")
                else:
                    test_results['failed'] += 1
                    test_results['details'].append("❌ OAuth状态验证测试失败：未找到有效状态")
        except Exception as e:
            test_results['failed'] += 1
            test_results['details'].append(f"❌ OAuth状态验证测试失败: {e}")
            logger.error(f"❌ OAuth状态验证测试失败: {e}")
        
        # 测试5: 审计日志查询测试
        test_results['total'] += 1
        try:
            with self.get_db_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("""
                    SELECT * FROM t_oauth2_audit_log 
                    WHERE id IN %s
                """, (tuple(self.test_data_ids['audit_logs']),))
                results = cursor.fetchall()
                if len(results) == len(self.test_data_ids['audit_logs']):
                    test_results['passed'] += 1
                    test_results['details'].append("✅ 审计日志查询测试通过")
                    logger.info(f"✅ 审计日志查询测试通过，查询到 {len(results)} 条日志")
                else:
                    test_results['failed'] += 1
                    test_results['details'].append("❌ 审计日志查询测试失败：记录数不匹配")
        except Exception as e:
            test_results['failed'] += 1
            test_results['details'].append(f"❌ 审计日志查询测试失败: {e}")
            logger.error(f"❌ 审计日志查询测试失败: {e}")
        
        return test_results
    
    def cleanup_test_data(self):
        """清理测试数据"""
        logger.info("🧹 开始清理测试数据...")
        
        with self.get_db_connection() as conn:
            cursor = conn.cursor()
            
            try:
                # 清理审计日志
                if self.test_data_ids['audit_logs']:
                    cursor.execute("""
                        DELETE FROM t_oauth2_audit_log WHERE id IN %s
                    """, (tuple(self.test_data_ids['audit_logs']),))
                    logger.info(f"🗑️ 清理了 {len(self.test_data_ids['audit_logs'])} 条审计日志")
                
                # 清理OAuth状态
                if self.test_data_ids['oauth_states']:
                    cursor.execute("""
                        DELETE FROM t_oauth2_state WHERE id IN %s
                    """, (tuple(self.test_data_ids['oauth_states']),))
                    logger.info(f"🗑️ 清理了 {len(self.test_data_ids['oauth_states'])} 个OAuth状态")
                
                # 清理OAuth绑定
                if self.test_data_ids['oauth_bindings']:
                    cursor.execute("""
                        DELETE FROM t_user_oauth WHERE id IN %s
                    """, (tuple(self.test_data_ids['oauth_bindings']),))
                    logger.info(f"🗑️ 清理了 {len(self.test_data_ids['oauth_bindings'])} 个OAuth绑定")
                
                # 清理测试用户
                if self.test_data_ids['users']:
                    cursor.execute("""
                        DELETE FROM t_user WHERE id IN %s
                    """, (tuple(self.test_data_ids['users']),))
                    logger.info(f"🗑️ 清理了 {len(self.test_data_ids['users'])} 个测试用户")
                
                conn.commit()
                logger.info("✅ 测试数据清理完成")
                
            except Exception as e:
                conn.rollback()
                logger.error(f"❌ 清理测试数据失败: {e}")
                raise
    
    def generate_state_token(self):
        """生成状态令牌"""
        uuid_part = str(uuid.uuid4()).replace('-', '')
        timestamp = str(int(time.time()))
        random_part = hashlib.md5(str(time.time()).encode()).hexdigest()[:8]
        return f"{uuid_part}{timestamp}{random_part}"
    
    def run_complete_test(self):
        """运行完整测试流程"""
        logger.info("🚀 开始OAuth2数据库完整测试流程")
        logger.info("=" * 60)
        
        try:
            # 1. 创建测试数据
            self.create_test_data()
            
            # 2. 运行测试
            test_results = self.run_oauth2_tests()
            
            # 3. 输出测试结果
            logger.info("=" * 60)
            logger.info("📊 测试结果统计")
            logger.info("=" * 60)
            logger.info(f"总测试数: {test_results['total']}")
            logger.info(f"通过: {test_results['passed']}")
            logger.info(f"失败: {test_results['failed']}")
            
            success_rate = (test_results['passed'] / test_results['total']) * 100
            logger.info(f"成功率: {success_rate:.2f}%")
            
            logger.info("\n详细结果:")
            for detail in test_results['details']:
                logger.info(f"  {detail}")
            
            return test_results['failed'] == 0
            
        finally:
            # 4. 清理测试数据
            try:
                self.cleanup_test_data()
            except Exception as e:
                logger.error(f"清理测试数据时发生错误: {e}")

def main():
    """主函数"""
    try:
        test_manager = DatabaseTestManager()
        success = test_manager.run_complete_test()
        
        if success:
            logger.info("🎉 所有测试通过！OAuth2数据库功能验证成功")
            return 0
        else:
            logger.warning("❌ 存在测试失败，请检查相关功能")
            return 1
            
    except Exception as e:
        logger.error(f"测试执行过程中发生错误: {e}")
        return 1

if __name__ == "__main__":
    import sys
    sys.exit(main())
