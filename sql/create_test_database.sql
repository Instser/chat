-- 创建测试数据库
-- 用于支付审计日志功能测试

-- 创建测试数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS chat_test 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

-- 使用测试数据库
USE chat_test;

-- 注意：实际的表结构应该与生产环境保持一致
-- 这里只是创建数据库，表结构应该通过现有的SQL脚本创建

-- 清理测试数据（可选，用于测试前清理）
-- TRUNCATE TABLE t_payment_audit;
-- TRUNCATE TABLE t_user_order;
-- TRUNCATE TABLE t_user;
-- TRUNCATE TABLE t_ai_model;
-- TRUNCATE TABLE t_package;
