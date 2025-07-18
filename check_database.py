#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据库检查脚本
检查订单状态和用户账户信息
"""

import pymysql
import json
from datetime import datetime

def check_database():
    """检查数据库中的订单和用户账户信息"""
    try:
        # 数据库连接配置
        connection = pymysql.connect(
            host='your-db-host.example.com',
            port=32232,
            user='root',
            password='wsq54wg4',
            database='chat',
            charset='utf8mb4'
        )
        
        cursor = connection.cursor(pymysql.cursors.DictCursor)
        
        print("=" * 60)
        print("数据库检查结果")
        print("=" * 60)
        print(f"检查时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print()
        
        # 检查最近的订单
        print("🔍 检查最近的订单:")
        cursor.execute("""
            SELECT order_no, user_id, package_id, amount, currency, 
                   payment_method, payment_status, payment_time, 
                   transaction_id, created_time
            FROM t_user_order 
            WHERE user_id = 1 
            ORDER BY created_time DESC 
            LIMIT 5
        """)
        
        orders = cursor.fetchall()
        if orders:
            for order in orders:
                print(f"  订单号: {order['order_no']}")
                print(f"  用户ID: {order['user_id']}")
                print(f"  套餐ID: {order['package_id']}")
                print(f"  金额: {order['amount']} {order['currency']}")
                print(f"  支付方式: {order['payment_method']}")
                print(f"  支付状态: {order['payment_status']}")
                print(f"  支付时间: {order['payment_time']}")
                print(f"  交易号: {order['transaction_id']}")
                print(f"  创建时间: {order['created_time']}")
                print("  " + "-" * 40)
        else:
            print("  未找到订单记录")
        
        print()
        
        # 检查用户账户信息
        print("🔍 检查用户账户信息:")
        cursor.execute("""
            SELECT user_id, package_id, package_expire_time, 
                   chat_count_remaining, total_chat_count, 
                   total_amount_spent, create_time, update_time
            FROM t_user_account 
            WHERE user_id = 1
        """)
        
        account = cursor.fetchone()
        if account:
            print(f"  用户ID: {account['user_id']}")
            print(f"  套餐ID: {account['package_id']}")
            print(f"  套餐过期时间: {account['package_expire_time']}")
            print(f"  剩余聊天次数: {account['chat_count_remaining']}")
            print(f"  总聊天次数: {account['total_chat_count']}")
            print(f"  总消费金额: {account['total_amount_spent']}")
            print(f"  创建时间: {account['create_time']}")
            print(f"  更新时间: {account['update_time']}")
        else:
            print("  未找到用户账户记录")
        
        print()
        
        # 检查套餐信息
        print("🔍 检查套餐信息:")
        cursor.execute("""
            SELECT id, name, description, price, currency, 
                   validity_days, chat_count, is_enabled
            FROM t_package 
            WHERE id IN (1, 2, 3, 4, 5, 6)
            ORDER BY id
        """)
        
        packages = cursor.fetchall()
        if packages:
            for package in packages:
                print(f"  套餐ID: {package['id']}")
                print(f"  套餐名称: {package['name']}")
                print(f"  价格: {package['price']} {package['currency']}")
                print(f"  有效期: {package['validity_days']} 天")
                print(f"  聊天次数: {package['chat_count']}")
                print(f"  是否启用: {package['is_enabled']}")
                print("  " + "-" * 30)
        else:
            print("  未找到套餐记录")
        
        print("=" * 60)
        
        cursor.close()
        connection.close()
        
    except Exception as e:
        print(f"数据库检查异常: {e}")

if __name__ == "__main__":
    check_database()
