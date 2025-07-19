#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
检查OAuth2用户权限问题的脚本
"""

import pymysql
from datetime import datetime
import pytz

# 数据库连接配置 - 使用生产环境数据库
DB_CONFIG = {
    'host': 'your-db-host.example.com',
    'port': 32232,
    'user': 'root',
    'password': 'wsq54wg4',
    'database': 'chat',
    'charset': 'utf8mb4'
}

def get_db_connection():
    """获取数据库连接"""
    return pymysql.connect(**DB_CONFIG)

def check_user_info(user_id):
    """检查用户信息"""
    conn = get_db_connection()
    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cursor:
            # 查询用户基本信息
            sql = """
            SELECT
                u.id as user_id,
                u.username,
                u.email,
                u.role,
                u.status,
                u.create_time,
                u.last_login_time
            FROM t_user u
            WHERE u.id = %s
            """
            cursor.execute(sql, (user_id,))
            user_info = cursor.fetchone()
            
            if not user_info:
                print(f"❌ 用户ID {user_id} 不存在")
                return
                
            print("=== 用户基本信息 ===")
            print(f"用户ID: {user_info['user_id']}")
            print(f"用户名: {user_info['username']}")
            print(f"邮箱: {user_info['email']}")
            print(f"角色: {user_info['role']}")
            print(f"状态: {user_info['status']}")
            print(f"注册时间: {user_info['create_time']}")
            print(f"最后登录: {user_info['last_login_time']}")
            print()
            
            # 查询OAuth绑定信息
            sql = """
            SELECT
                provider,
                provider_id,
                email,
                name,
                status,
                login_count,
                last_login_time,
                create_time
            FROM t_user_oauth
            WHERE user_id = %s
            """
            cursor.execute(sql, (user_id,))
            oauth_info = cursor.fetchall()
            
            print("=== OAuth绑定信息 ===")
            if oauth_info:
                for oauth in oauth_info:
                    print(f"提供商: {oauth['provider']}")
                    print(f"提供商ID: {oauth['provider_id']}")
                    print(f"OAuth邮箱: {oauth['email']}")
                    print(f"OAuth名称: {oauth['name']}")
                    print(f"状态: {oauth['status']}")
                    print(f"登录次数: {oauth['login_count']}")
                    print(f"最后登录: {oauth['last_login_time']}")
                    print(f"绑定时间: {oauth['create_time']}")
                    print()
            else:
                print("无OAuth绑定信息")
                print()
            
            # 查询用户账户信息
            sql = """
            SELECT
                ua.id as account_id,
                ua.package_id,
                ua.chat_count_remaining,
                ua.package_expire_time,
                ua.total_chat_count,
                ua.membership_level,
                ua.membership_expire_time,
                ua.create_time as account_create_time,
                ua.update_time as account_update_time,
                p.package_name,
                p.limit_type,
                p.validity_days,
                p.model_permissions,
                p.is_enabled as package_enabled
            FROM t_user_account ua
            LEFT JOIN t_package p ON ua.package_id = p.id
            WHERE ua.user_id = %s
            """
            cursor.execute(sql, (user_id,))
            account_info = cursor.fetchone()
            
            print("=== 用户账户信息 ===")
            if account_info:
                print(f"账户ID: {account_info['account_id']}")
                print(f"套餐ID: {account_info['package_id']}")
                print(f"剩余聊天次数: {account_info['chat_count_remaining']}")
                print(f"套餐过期时间: {account_info['package_expire_time']}")
                print(f"总聊天次数: {account_info['total_chat_count']}")
                print(f"会员等级: {account_info['membership_level']}")
                print(f"会员过期时间: {account_info['membership_expire_time']}")
                print(f"账户创建时间: {account_info['account_create_time']}")
                print(f"账户更新时间: {account_info['account_update_time']}")
                print()
                
                if account_info['package_id']:
                    print("=== 套餐详情 ===")
                    print(f"套餐名称: {account_info['package_name']}")
                    print(f"限制类型: {account_info['limit_type']}")
                    print(f"有效天数: {account_info['validity_days']}")
                    print(f"模型权限配置: {account_info['model_permissions']}")
                    print(f"套餐启用状态: {account_info['package_enabled']}")
                    print()
                    
                    # 检查过期状态
                    if account_info['package_expire_time']:
                        # 使用上海时区进行比较
                        shanghai_tz = pytz.timezone('Asia/Shanghai')
                        current_time = datetime.now(shanghai_tz)
                        
                        # 如果数据库中的时间是naive datetime，假设它是上海时区
                        expire_time = account_info['package_expire_time']
                        if expire_time.tzinfo is None:
                            expire_time = shanghai_tz.localize(expire_time)
                        
                        print("=== 过期状态检查 ===")
                        print(f"当前时间 (上海): {current_time}")
                        print(f"过期时间: {expire_time}")
                        
                        if expire_time < current_time:
                            print("❌ 套餐已过期")
                            time_diff = current_time - expire_time
                            print(f"过期时长: {time_diff}")
                        else:
                            print("✅ 套餐未过期")
                            time_diff = expire_time - current_time
                            print(f"剩余时间: {time_diff}")
                    else:
                        print("⚠️  套餐无过期时间设置")
                else:
                    print("❌ 用户未绑定套餐")
            else:
                print("❌ 用户账户信息不存在")
                
    except Exception as e:
        print(f"❌ 查询出错: {e}")
    finally:
        conn.close()

def check_package_info(package_id):
    """检查套餐信息"""
    conn = get_db_connection()
    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cursor:
            sql = """
            SELECT
                id,
                package_name,
                package_type,
                price,
                validity_days,
                limit_type,
                model_permissions,
                is_enabled,
                create_time,
                update_time
            FROM t_package
            WHERE id = %s
            """
            cursor.execute(sql, (package_id,))
            package_info = cursor.fetchone()

            if package_info:
                print("=== 套餐详细信息 ===")
                for key, value in package_info.items():
                    print(f"{key}: {value}")
            else:
                print(f"❌ 套餐ID {package_id} 不存在")

    except Exception as e:
        print(f"❌ 查询套餐信息出错: {e}")
    finally:
        conn.close()

def main():
    """主函数"""
    user_id = 1945912505721913345  # OAuth2用户ID
    print(f"检查用户ID: {user_id}")
    print("=" * 50)
    check_user_info(user_id)

    print("\n" + "=" * 50)
    print("检查Free套餐配置:")
    check_package_info(1)

if __name__ == "__main__":
    main()
