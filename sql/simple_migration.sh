#!/bin/bash

# MySQL 5.7 到 8.0 简化数据迁移脚本
# 使用方法: ./simple_migration.sh [5.7服务器IP] [8.0服务器IP] [用户名] [数据库名]

# 参数设置
SOURCE_HOST=${1:-"localhost"}
TARGET_HOST=${2:-"localhost"}
DB_USER=${3:-"root"}
DB_NAME=${4:-"chat"}

echo "=========================================="
echo "MySQL 数据迁移脚本"
echo "源服务器: $SOURCE_HOST"
echo "目标服务器: $TARGET_HOST"
echo "数据库: $DB_NAME"
echo "=========================================="

# 1. 备份源数据库
echo "步骤1: 备份源数据库..."
mysqldump -h $SOURCE_HOST -u $DB_USER -p \
  --single-transaction \
  --routines \
  --triggers \
  --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 \
  $DB_NAME > ${DB_NAME}_backup_$(date +%Y%m%d_%H%M%S).sql

if [ $? -eq 0 ]; then
    echo "✓ 数据备份完成"
else
    echo "✗ 数据备份失败"
    exit 1
fi

# 2. 创建目标数据库
echo "步骤2: 创建目标数据库..."
mysql -h $TARGET_HOST -u $DB_USER -p -e "CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

if [ $? -eq 0 ]; then
    echo "✓ 目标数据库创建完成"
else
    echo "✗ 目标数据库创建失败"
    exit 1
fi

# 3. 导入数据
echo "步骤3: 导入数据到目标数据库..."
mysql -h $TARGET_HOST -u $DB_USER -p $DB_NAME < ${DB_NAME}_backup_*.sql

if [ $? -eq 0 ]; then
    echo "✓ 数据导入完成"
else
    echo "✗ 数据导入失败"
    exit 1
fi

# 4. 验证数据
echo "步骤4: 验证数据完整性..."
mysql -h $TARGET_HOST -u $DB_USER -p $DB_NAME -e "
SELECT 
    table_name as '表名',
    table_rows as '记录数'
FROM information_schema.tables 
WHERE table_schema = '$DB_NAME'
ORDER BY table_rows DESC;"

# 5. 优化数据库
echo "步骤5: 优化数据库..."
mysql -h $TARGET_HOST -u $DB_USER -p $DB_NAME -e "
ANALYZE TABLE t_ai_model, t_user, t_chat_session, t_chat_message, t_package, t_user_order, t_user_package, t_upload;
OPTIMIZE TABLE t_ai_model, t_user, t_chat_session, t_chat_message, t_package, t_user_order, t_user_package, t_upload;"

echo "=========================================="
echo "✓ 迁移完成！"
echo "备份文件: ${DB_NAME}_backup_*.sql"
echo "请更新应用配置文件中的数据库连接信息"
echo "=========================================="

# 生成配置更新提示
echo "应用配置更新:"
echo "spring.datasource.url: jdbc:mysql://$TARGET_HOST:3306/$DB_NAME?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
