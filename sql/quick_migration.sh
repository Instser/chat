#!/bin/bash

# MySQL 5.7 到 8.0 快速迁移脚本
# 创建时间: 2025-06-29 17:12
# 适用场景: 小到中等数据量（<1GB）的快速迁移
# 使用方法: chmod +x quick_migration.sh && ./quick_migration.sh

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 配置变量（请根据实际情况修改）
SOURCE_HOST="localhost"
SOURCE_PORT="3306"
SOURCE_USER="root"
SOURCE_DB="chat"

TARGET_HOST="localhost"
TARGET_PORT="3306"
TARGET_USER="root"
TARGET_DB="chat_new"

BACKUP_DIR="./migration_backup_$(date +%Y%m%d_%H%M%S)"

# 检查必要工具
check_tools() {
    log_info "检查必要工具..."
    
    if ! command -v mysql &> /dev/null; then
        log_error "MySQL客户端未安装，请先安装MySQL客户端"
        exit 1
    fi
    
    if ! command -v mysqldump &> /dev/null; then
        log_error "mysqldump工具未安装，请先安装MySQL客户端工具"
        exit 1
    fi
    
    log_success "工具检查完成"
}

# 检查源数据库连接
check_source_connection() {
    log_info "检查源数据库连接..."
    
    if ! mysql -h $SOURCE_HOST -P $SOURCE_PORT -u $SOURCE_USER -p -e "USE $SOURCE_DB; SELECT 1;" &> /dev/null; then
        log_error "无法连接到源数据库，请检查连接参数"
        exit 1
    fi
    
    # 获取源数据库版本
    SOURCE_VERSION=$(mysql -h $SOURCE_HOST -P $SOURCE_PORT -u $SOURCE_USER -p -N -e "SELECT VERSION();" 2>/dev/null)
    log_success "源数据库连接成功，版本: $SOURCE_VERSION"
}

# 检查目标数据库连接
check_target_connection() {
    log_info "检查目标数据库连接..."
    
    if ! mysql -h $TARGET_HOST -P $TARGET_PORT -u $TARGET_USER -p -e "SELECT 1;" &> /dev/null; then
        log_error "无法连接到目标数据库，请检查连接参数"
        exit 1
    fi
    
    # 获取目标数据库版本
    TARGET_VERSION=$(mysql -h $TARGET_HOST -P $TARGET_PORT -u $TARGET_USER -p -N -e "SELECT VERSION();" 2>/dev/null)
    log_success "目标数据库连接成功，版本: $TARGET_VERSION"
    
    # 检查是否为MySQL 8.0
    if [[ ! $TARGET_VERSION =~ ^8\. ]]; then
        log_warning "目标数据库不是MySQL 8.0版本，当前版本: $TARGET_VERSION"
        read -p "是否继续？(y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

# 获取源数据库统计信息
get_source_stats() {
    log_info "获取源数据库统计信息..."
    
    mysql -h $SOURCE_HOST -P $SOURCE_PORT -u $SOURCE_USER -p $SOURCE_DB -e "
    SELECT 
        table_name as '表名',
        table_rows as '记录数',
        ROUND(((data_length + index_length) / 1024 / 1024), 2) AS '大小(MB)'
    FROM information_schema.tables 
    WHERE table_schema = '$SOURCE_DB'
    ORDER BY table_rows DESC;"
}

# 创建备份目录
create_backup_dir() {
    log_info "创建备份目录: $BACKUP_DIR"
    mkdir -p $BACKUP_DIR
    log_success "备份目录创建成功"
}

# 备份源数据库
backup_source_database() {
    log_info "开始备份源数据库..."
    
    local backup_file="$BACKUP_DIR/source_backup.sql"
    
    mysqldump -h $SOURCE_HOST -P $SOURCE_PORT -u $SOURCE_USER -p \
        --single-transaction \
        --routines \
        --triggers \
        --set-gtid-purged=OFF \
        --default-character-set=utf8mb4 \
        --hex-blob \
        --complete-insert \
        $SOURCE_DB > $backup_file
    
    # 检查备份文件大小
    local backup_size=$(du -h $backup_file | cut -f1)
    log_success "数据库备份完成，文件大小: $backup_size"
    
    # 验证备份文件
    if [[ ! -s $backup_file ]]; then
        log_error "备份文件为空，备份失败"
        exit 1
    fi
    
    local table_count=$(grep -c "CREATE TABLE" $backup_file)
    log_info "备份文件包含 $table_count 个表"
}

# 创建目标数据库
create_target_database() {
    log_info "创建目标数据库..."
    
    mysql -h $TARGET_HOST -P $TARGET_PORT -u $TARGET_USER -p -e "
    CREATE DATABASE IF NOT EXISTS $TARGET_DB 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;"
    
    log_success "目标数据库创建成功"
}

# 导入数据到目标数据库
import_data() {
    log_info "开始导入数据到目标数据库..."
    
    local backup_file="$BACKUP_DIR/source_backup.sql"
    
    # 显示进度
    local total_lines=$(wc -l < $backup_file)
    log_info "备份文件总行数: $total_lines"
    
    mysql -h $TARGET_HOST -P $TARGET_PORT -u $TARGET_USER -p $TARGET_DB < $backup_file
    
    log_success "数据导入完成"
}

# 验证数据完整性
verify_data() {
    log_info "验证数据完整性..."
    
    mysql -h $TARGET_HOST -P $TARGET_PORT -u $TARGET_USER -p $TARGET_DB -e "
    SELECT 'AI模型表' as table_name, COUNT(*) as record_count FROM t_ai_model WHERE deleted = 0
    UNION ALL
    SELECT '用户表', COUNT(*) FROM t_user WHERE deleted = 0
    UNION ALL
    SELECT '聊天会话表', COUNT(*) FROM t_chat_session WHERE deleted = 0
    UNION ALL
    SELECT '聊天消息表', COUNT(*) FROM t_chat_message WHERE deleted = 0
    UNION ALL
    SELECT '套餐表', COUNT(*) FROM t_package WHERE deleted = 0
    UNION ALL
    SELECT '用户订单表', COUNT(*) FROM t_user_order WHERE deleted = 0
    UNION ALL
    SELECT '用户套餐表', COUNT(*) FROM t_user_package WHERE deleted = 0
    UNION ALL
    SELECT '文件上传表', COUNT(*) FROM t_upload WHERE deleted = 0;"
    
    log_success "数据完整性验证完成"
}

# 优化目标数据库
optimize_database() {
    log_info "优化目标数据库..."
    
    mysql -h $TARGET_HOST -P $TARGET_PORT -u $TARGET_USER -p $TARGET_DB -e "
    -- 分析表统计信息
    ANALYZE TABLE t_ai_model, t_user, t_chat_session, t_chat_message, 
                  t_package, t_user_order, t_user_package, t_upload;
    
    -- 优化表
    OPTIMIZE TABLE t_ai_model, t_user, t_chat_session, t_chat_message, 
                   t_package, t_user_order, t_user_package, t_upload;"
    
    log_success "数据库优化完成"
}

# 生成配置更新提示
generate_config_update() {
    log_info "生成配置更新提示..."
    
    local config_file="$BACKUP_DIR/application_config_update.yml"
    
    cat > $config_file << EOF
# 更新应用配置文件 application.yml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://$TARGET_HOST:$TARGET_PORT/$TARGET_DB?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: $TARGET_USER
    # password: 请设置实际密码
    
    druid:
      initial-size: 10
      min-idle: 10
      max-active: 50
      max-wait: 60000
      validation-query: SELECT 1
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false

# MySQL 8.0 优化配置建议
# my.cnf 配置文件添加:
[mysqld]
default_authentication_plugin=mysql_native_password
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
innodb_buffer_pool_size=1G
innodb_log_file_size=256M
innodb_flush_log_at_trx_commit=2
innodb_flush_method=O_DIRECT
innodb_dedicated_server=ON
innodb_redo_log_capacity=512M
EOF
    
    log_success "配置更新文件已生成: $config_file"
}

# 主函数
main() {
    echo "=========================================="
    echo "MySQL 5.7 到 8.0 快速迁移脚本"
    echo "创建时间: 2025-06-29 17:12"
    echo "=========================================="
    
    # 确认迁移参数
    echo "迁移参数:"
    echo "源数据库: $SOURCE_HOST:$SOURCE_PORT/$SOURCE_DB"
    echo "目标数据库: $TARGET_HOST:$TARGET_PORT/$TARGET_DB"
    echo "备份目录: $BACKUP_DIR"
    echo
    
    read -p "确认开始迁移？(y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "迁移已取消"
        exit 0
    fi
    
    # 执行迁移步骤
    check_tools
    check_source_connection
    check_target_connection
    get_source_stats
    create_backup_dir
    backup_source_database
    create_target_database
    import_data
    verify_data
    optimize_database
    generate_config_update
    
    echo "=========================================="
    log_success "迁移完成！"
    echo "备份文件保存在: $BACKUP_DIR"
    echo "配置更新文件: $BACKUP_DIR/application_config_update.yml"
    echo "请更新应用配置文件中的数据库连接信息"
    echo "=========================================="
}

# 执行主函数
main "$@"
