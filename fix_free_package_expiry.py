#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修复Free套餐用户的过期时间问题
将使用Free套餐（ID=1）且validity_days=-1的用户的过期时间设置为NULL
"""

import pymysql
from datetime import datetime

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

def fix_free_package_expiry():
    """修复Free套餐用户的过期时间"""
    conn = get_db_connection()
    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cursor:
            # 1. 首先查询需要修复的用户
            sql = """
            SELECT 
                ua.id,
                ua.user_id,
                ua.package_id,
                ua.package_expire_time,
                p.package_name,
                p.validity_days,
                u.username,
                u.email
            FROM t_user_account ua
            LEFT JOIN t_package p ON ua.package_id = p.id
            LEFT JOIN t_user u ON ua.user_id = u.id
            WHERE ua.package_id = 1 
            AND p.validity_days = -1
            AND ua.package_expire_time IS NOT NULL
            """
            cursor.execute(sql)
            affected_users = cursor.fetchall()
            
            if not affected_users:
                print("✅ 没有需要修复的用户")
                return
                
            print(f"📋 找到 {len(affected_users)} 个需要修复的用户:")
            print("-" * 80)
            for user in affected_users:
                print(f"用户ID: {user['user_id']}")
                print(f"用户名: {user['username']}")
                print(f"邮箱: {user['email']}")
                print(f"套餐: {user['package_name']} (ID: {user['package_id']})")
                print(f"当前过期时间: {user['package_expire_time']}")
                print("-" * 40)
            
            # 2. 确认是否执行修复
            confirm = input("\n是否执行修复？(y/N): ").strip().lower()
            if confirm != 'y':
                print("❌ 取消修复操作")
                return
            
            # 3. 执行修复
            print("\n🔧 开始执行修复...")
            
            update_sql = """
            UPDATE t_user_account 
            SET package_expire_time = NULL,
                update_time = NOW()
            WHERE package_id = 1 
            AND package_id IN (
                SELECT id FROM t_package WHERE validity_days = -1
            )
            AND package_expire_time IS NOT NULL
            """
            
            cursor.execute(update_sql)
            updated_count = cursor.rowcount
            conn.commit()
            
            print(f"✅ 修复完成！共更新了 {updated_count} 个用户的套餐过期时间")
            
            # 4. 验证修复结果
            print("\n🔍 验证修复结果...")
            verify_sql = """
            SELECT 
                ua.user_id,
                ua.package_expire_time,
                u.username,
                u.email
            FROM t_user_account ua
            LEFT JOIN t_user u ON ua.user_id = u.id
            WHERE ua.package_id = 1 
            AND ua.user_id IN ({})
            """.format(','.join([str(user['user_id']) for user in affected_users]))
            
            cursor.execute(verify_sql)
            verified_users = cursor.fetchall()
            
            print("修复后的用户状态:")
            print("-" * 60)
            for user in verified_users:
                expire_status = "永久有效" if user['package_expire_time'] is None else f"过期时间: {user['package_expire_time']}"
                print(f"用户: {user['username']} ({user['email']}) - {expire_status}")
                
    except Exception as e:
        print(f"❌ 修复过程中出错: {e}")
        conn.rollback()
    finally:
        conn.close()

def main():
    """主函数"""
    print("Free套餐过期时间修复工具")
    print("=" * 50)
    print("此工具将修复Free套餐用户的过期时间问题")
    print("将validity_days=-1的Free套餐用户的过期时间设置为NULL（永久有效）")
    print("=" * 50)
    
    fix_free_package_expiry()

if __name__ == "__main__":
    main()
