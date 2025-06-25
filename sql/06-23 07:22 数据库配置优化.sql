-- =====================================================
-- 数据库配置优化脚本
-- =====================================================
-- 生成时间: 2025年6月23日 07:22 (北京时间)
-- 版本: v1.0
-- 项目: jinyu-chat 聊天系统
-- 目标: 优化MySQL配置参数，提升数据库整体性能
-- =====================================================

-- =====================================================
-- 第一部分: 当前配置检查
-- =====================================================

SELECT '=== 当前数据库配置检查 ===' AS info;

-- 检查MySQL版本和基本信息
SELECT 
    '数据库版本信息' AS config_type,
    VERSION() AS current_value,
    '确认MySQL版本兼容性' AS description;

-- 检查当前关键配置参数
SELECT 
    'InnoDB缓冲池大小' AS config_type,
    CONCAT(ROUND(@@innodb_buffer_pool_size/1024/1024/1024, 2), ' GB') AS current_value,
    '建议设置为可用内存的70-80%' AS description
UNION ALL
SELECT 
    '最大连接数',
    @@max_connections,
    '建议根据并发需求调整到200-500'
UNION ALL
SELECT 
    '查询缓存大小',
    CONCAT(ROUND(@@query_cache_size/1024/1024, 2), ' MB'),
    'MySQL 8.0已废弃查询缓存'
UNION ALL
SELECT 
    '慢查询日志',
    @@slow_query_log,
    '建议开启以监控性能问题'
UNION ALL
SELECT 
    '慢查询时间阈值',
    CONCAT(@@long_query_time, ' 秒'),
    '建议设置为1秒'
UNION ALL
SELECT 
    'InnoDB日志文件大小',
    CONCAT(ROUND(@@innodb_log_file_size/1024/1024, 2), ' MB'),
    '建议设置为256MB-1GB'
UNION ALL
SELECT 
    'InnoDB刷新日志频率',
    @@innodb_flush_log_at_trx_commit,
    '1=最安全但性能较低，2=性能较好'
UNION ALL
SELECT 
    '表打开缓存',
    @@table_open_cache,
    '建议设置为2000-4000'
UNION ALL
SELECT 
    '临时表大小',
    CONCAT(ROUND(@@tmp_table_size/1024/1024, 2), ' MB'),
    '建议设置为64-128MB';

-- =====================================================
-- 第二部分: 性能优化配置建议
-- =====================================================

SELECT '=== 性能优化配置建议 ===' AS info;

-- 注意: 以下配置需要在MySQL配置文件(my.cnf)中设置，并重启MySQL服务
-- 这里提供配置建议，不能直接通过SQL执行

SELECT '
-- =====================================================
-- MySQL配置文件优化建议 (my.cnf)
-- =====================================================

[mysqld]
# 基本设置
port = 3306
bind-address = 0.0.0.0
max_connections = 300
max_connect_errors = 1000
table_open_cache = 2048
max_allowed_packet = 64M

# InnoDB设置 (核心优化)
default-storage-engine = INNODB
innodb_buffer_pool_size = 2G          # 设置为可用内存的70-80%
innodb_log_file_size = 512M           # 日志文件大小
innodb_log_buffer_size = 64M          # 日志缓冲区大小
innodb_flush_log_at_trx_commit = 2    # 性能优化(2比1性能更好)
innodb_lock_wait_timeout = 120        # 锁等待超时时间
innodb_file_per_table = 1             # 每个表独立表空间

# 查询优化
tmp_table_size = 128M                 # 临时表大小
max_heap_table_size = 128M            # 内存表大小
query_cache_type = 0                  # MySQL 8.0已废弃
query_cache_size = 0                  # MySQL 8.0已废弃

# 慢查询日志
slow_query_log = 1                    # 开启慢查询日志
long_query_time = 1                   # 慢查询时间阈值(秒)
slow_query_log_file = /var/log/mysql/slow.log

# 连接优化
interactive_timeout = 300             # 交互式连接超时
wait_timeout = 300                    # 非交互式连接超时
connect_timeout = 60                  # 连接超时

# 排序和分组优化
sort_buffer_size = 4M                 # 排序缓冲区大小
read_buffer_size = 2M                 # 读缓冲区大小
read_rnd_buffer_size = 8M             # 随机读缓冲区大小
join_buffer_size = 4M                 # 连接缓冲区大小

# 二进制日志 (如果需要主从复制)
log-bin = mysql-bin
binlog_format = ROW
expire_logs_days = 7

# 错误日志
log-error = /var/log/mysql/error.log

# 字符集设置
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci

[mysql]
default-character-set = utf8mb4

[client]
default-character-set = utf8mb4
' AS mysql_config_recommendations;

-- =====================================================
-- 第三部分: 运行时可调整的参数
-- =====================================================

SELECT '=== 运行时可调整的参数优化 ===' AS info;

-- 以下参数可以在运行时动态调整，无需重启MySQL

-- 1. 调整慢查询日志设置
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;

-- 2. 调整连接超时设置
SET GLOBAL interactive_timeout = 300;
SET GLOBAL wait_timeout = 300;

-- 3. 调整InnoDB设置 (部分参数)
-- SET GLOBAL innodb_lock_wait_timeout = 120;  -- 锁等待超时

-- 4. 开启性能监控
SET GLOBAL performance_schema = 'ON';

-- 显示调整后的设置
SELECT 
    '调整后的运行时参数' AS config_type,
    '慢查询日志' AS parameter_name,
    @@slow_query_log AS current_value
UNION ALL
SELECT 
    '调整后的运行时参数',
    '慢查询时间阈值',
    CONCAT(@@long_query_time, ' 秒')
UNION ALL
SELECT 
    '调整后的运行时参数',
    '交互式连接超时',
    CONCAT(@@interactive_timeout, ' 秒')
UNION ALL
SELECT 
    '调整后的运行时参数',
    '非交互式连接超时',
    CONCAT(@@wait_timeout, ' 秒');

-- =====================================================
-- 第四部分: 连接池配置建议
-- =====================================================

SELECT '=== 应用层连接池配置建议 ===' AS info;

SELECT '
-- =====================================================
-- Druid连接池配置优化建议 (application.yml)
-- =====================================================

spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      # 基本连接池配置
      initial-size: 10                    # 初始连接数 (提高到10)
      min-idle: 15                        # 最小空闲连接数 (提高到15)
      max-active: 50                      # 最大连接数 (提高到50)
      max-wait: 30000                     # 获取连接等待超时时间 (降低到30秒)
      
      # 连接检测配置
      time-between-eviction-runs-millis: 30000    # 检测空闲连接间隔
      min-evictable-idle-time-millis: 180000      # 连接最小空闲时间
      max-evictable-idle-time-millis: 600000      # 连接最大空闲时间
      
      # 连接验证配置
      validation-query: SELECT 1                   # 验证查询语句
      test-while-idle: true                       # 空闲时检测连接有效性
      test-on-borrow: false                       # 获取连接时不检测(性能优化)
      test-on-return: false                       # 归还连接时不检测(性能优化)
      
      # 预编译语句缓存
      pool-prepared-statements: true              # 开启预编译语句缓存
      max-pool-prepared-statement-per-connection-size: 50  # 每连接最大缓存数
      
      # 监控配置
      connection-properties: druid.stat.mergeSql=true;druid.stat.slowSqlMillis=1000
      use-global-data-source-stat: true
      
      # 过滤器配置
      filters: stat,wall,slf4j
      
      # Web监控配置
      web-stat-filter:
        enabled: true
        url-pattern: /*
        exclusions: "*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*"
        
      stat-view-servlet:
        enabled: true
        url-pattern: /druid/*
        reset-enable: false
        login-username: admin
        login-password: admin123
        
# MyBatis-Plus配置优化
mybatis-plus:
  configuration:
    # 开启缓存
    cache-enabled: true
    # 延迟加载
    lazy-loading-enabled: true
    aggressive-lazy-loading: false
    # 批量操作优化
    default-executor-type: REUSE
    # 日志配置
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      # 逻辑删除配置
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
' AS druid_config_recommendations;

-- =====================================================
-- 第五部分: 监控和维护脚本
-- =====================================================

SELECT '=== 数据库监控脚本 ===' AS info;

-- 1. 连接状态监控
SELECT 
    '当前连接状态' AS monitor_type,
    'Threads_connected' AS metric_name,
    VARIABLE_VALUE AS current_value,
    '当前活跃连接数' AS description
FROM performance_schema.global_status 
WHERE VARIABLE_NAME = 'Threads_connected'
UNION ALL
SELECT 
    '当前连接状态',
    'Threads_running',
    VARIABLE_VALUE,
    '当前运行中的线程数'
FROM performance_schema.global_status 
WHERE VARIABLE_NAME = 'Threads_running'
UNION ALL
SELECT 
    '当前连接状态',
    'Max_used_connections',
    VARIABLE_VALUE,
    '历史最大连接数'
FROM performance_schema.global_status 
WHERE VARIABLE_NAME = 'Max_used_connections';

-- 2. InnoDB状态监控
SELECT 
    'InnoDB状态' AS monitor_type,
    'Innodb_buffer_pool_read_requests' AS metric_name,
    VARIABLE_VALUE AS current_value,
    '缓冲池读请求数' AS description
FROM performance_schema.global_status 
WHERE VARIABLE_NAME = 'Innodb_buffer_pool_read_requests'
UNION ALL
SELECT 
    'InnoDB状态',
    'Innodb_buffer_pool_reads',
    VARIABLE_VALUE,
    '物理读次数'
FROM performance_schema.global_status 
WHERE VARIABLE_NAME = 'Innodb_buffer_pool_reads';

-- 计算缓冲池命中率
SELECT 
    'InnoDB缓冲池命中率' AS monitor_type,
    CONCAT(
        ROUND(
            (1 - (
                (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Innodb_buffer_pool_reads') /
                (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Innodb_buffer_pool_read_requests')
            )) * 100, 2
        ), '%'
    ) AS hit_ratio,
    '建议保持在95%以上' AS description;

-- =====================================================
-- 第六部分: 定期维护任务
-- =====================================================

SELECT '=== 定期维护任务建议 ===' AS info;

SELECT '
-- =====================================================
-- 定期维护任务 (建议加入定时任务)
-- =====================================================

-- 1. 每日任务 (凌晨执行)
-- 更新表统计信息
ANALYZE TABLE t_chat_message, t_chat_session, t_user_order, t_user_account;

-- 2. 每周任务
-- 检查索引使用情况
SELECT * FROM sys.schema_unused_indexes WHERE object_schema = ''chat'';

-- 检查表碎片情况
SELECT 
    TABLE_NAME,
    ROUND(DATA_LENGTH/1024/1024, 2) AS data_mb,
    ROUND(INDEX_LENGTH/1024/1024, 2) AS index_mb,
    ROUND(DATA_FREE/1024/1024, 2) AS free_mb
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = ''chat'' AND DATA_FREE > 0;

-- 3. 每月任务
-- 优化表 (重建索引，回收空间)
-- OPTIMIZE TABLE t_chat_message;  -- 注意：大表操作需要在低峰期执行

-- 4. 监控脚本
-- 检查慢查询
SELECT 
    DIGEST_TEXT,
    COUNT_STAR,
    ROUND(AVG_TIMER_WAIT/1000000000, 3) AS avg_time_sec
FROM performance_schema.events_statements_summary_by_digest 
WHERE SCHEMA_NAME = ''chat''
ORDER BY AVG_TIMER_WAIT DESC 
LIMIT 10;
' AS maintenance_tasks;

-- =====================================================
-- 配置优化完成
-- =====================================================

SELECT 
    '=== 数据库配置优化建议完成 ===' AS info,
    '请根据建议调整MySQL配置文件和应用配置' AS next_step,
    NOW() AS completion_time;

-- =====================================================
-- 重要提醒
-- =====================================================
/*
重要提醒:
1. MySQL配置文件修改需要重启MySQL服务才能生效
2. 连接池配置修改需要重启应用服务
3. 建议在测试环境先验证配置效果
4. 生产环境修改前请做好备份
5. 监控系统性能变化，必要时回滚配置

配置优化顺序:
1. 先执行索引优化脚本
2. 再调整MySQL配置参数
3. 最后优化应用层连接池配置
4. 持续监控和调优

性能监控指标:
- 查询响应时间
- 连接池使用率
- 缓冲池命中率
- 慢查询数量
- 系统资源使用率
*/
