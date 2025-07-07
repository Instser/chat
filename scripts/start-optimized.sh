#!/bin/bash

# ========================================
# 金语聊天系统优化启动脚本 (8核8G服务器)
# ========================================

# 应用配置
APP_NAME="jinyu-chat"
JAR_FILE="target/jinyu-chat-1.0.0.jar"
LOG_DIR="logs"
PID_FILE="${APP_NAME}.pid"

# 创建日志目录
mkdir -p ${LOG_DIR}

# JVM内存配置 (8G内存服务器)
HEAP_SIZE="-Xms4g -Xmx6g"                    # 堆内存: 初始4G, 最大6G
METASPACE="-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m"  # 元空间配置
DIRECT_MEMORY="-XX:MaxDirectMemorySize=1g"    # 直接内存限制

# GC配置 (G1垃圾收集器 - 适合大堆内存)
GC_CONFIG="-XX:+UseG1GC"                     # 使用G1垃圾收集器
GC_CONFIG="${GC_CONFIG} -XX:MaxGCPauseMillis=200"  # 最大GC暂停时间200ms
GC_CONFIG="${GC_CONFIG} -XX:G1HeapRegionSize=16m"  # G1堆区域大小
GC_CONFIG="${GC_CONFIG} -XX:G1NewSizePercent=30"   # 新生代占比30%
GC_CONFIG="${GC_CONFIG} -XX:G1MaxNewSizePercent=40" # 新生代最大占比40%
GC_CONFIG="${GC_CONFIG} -XX:+G1UseAdaptiveIHOP"    # 自适应IHOP

# GC日志配置
GC_LOG="-Xlog:gc*:${LOG_DIR}/gc.log:time,tags"
GC_LOG="${GC_LOG} -XX:+UseGCLogFileRotation"
GC_LOG="${GC_LOG} -XX:NumberOfGCLogFiles=5"
GC_LOG="${GC_LOG} -XX:GCLogFileSize=100M"

# 性能优化参数
PERFORMANCE="-server"                         # 服务器模式
PERFORMANCE="${PERFORMANCE} -XX:+UseCompressedOops"      # 压缩指针
PERFORMANCE="${PERFORMANCE} -XX:+UseCompressedClassPointers"  # 压缩类指针
PERFORMANCE="${PERFORMANCE} -XX:+UseFastUnorderedTimeStamps" # 快速时间戳
PERFORMANCE="${PERFORMANCE} -XX:+OptimizeStringConcat"   # 字符串连接优化
PERFORMANCE="${PERFORMANCE} -XX:+UseStringDeduplication" # 字符串去重

# 并发优化参数
CONCURRENCY="-XX:+UseBiasedLocking"           # 偏向锁
CONCURRENCY="${CONCURRENCY} -XX:+UseThreadPriorities"    # 线程优先级
CONCURRENCY="${CONCURRENCY} -XX:ThreadPriorityPolicy=1"  # 线程优先级策略

# 网络和IO优化
NETWORK="-Djava.net.preferIPv4Stack=true"    # 优先使用IPv4
NETWORK="${NETWORK} -Djava.awt.headless=true"            # 无头模式
NETWORK="${NETWORK} -Dfile.encoding=UTF-8"               # 文件编码

# 监控和诊断参数
MONITORING="-XX:+HeapDumpOnOutOfMemoryError"  # OOM时生成堆转储
MONITORING="${MONITORING} -XX:HeapDumpPath=${LOG_DIR}/heapdump.hprof"
MONITORING="${MONITORING} -XX:+PrintGCDetails"           # 打印GC详情
MONITORING="${MONITORING} -XX:+PrintGCTimeStamps"        # 打印GC时间戳
MONITORING="${MONITORING} -Dcom.sun.management.jmxremote" # 启用JMX
MONITORING="${MONITORING} -Dcom.sun.management.jmxremote.port=9999"
MONITORING="${MONITORING} -Dcom.sun.management.jmxremote.authenticate=false"
MONITORING="${MONITORING} -Dcom.sun.management.jmxremote.ssl=false"

# Spring Boot特定优化
SPRING_OPTS="-Dspring.profiles.active=prod"  # 生产环境配置
SPRING_OPTS="${SPRING_OPTS} -Dspring.jmx.enabled=true"   # 启用JMX
SPRING_OPTS="${SPRING_OPTS} -Dspring.output.ansi.enabled=never" # 禁用ANSI输出

# 组合所有JVM参数
JVM_OPTS="${HEAP_SIZE} ${METASPACE} ${DIRECT_MEMORY}"
JVM_OPTS="${JVM_OPTS} ${GC_CONFIG} ${GC_LOG}"
JVM_OPTS="${JVM_OPTS} ${PERFORMANCE} ${CONCURRENCY}"
JVM_OPTS="${JVM_OPTS} ${NETWORK} ${MONITORING}"
JVM_OPTS="${JVM_OPTS} ${SPRING_OPTS}"

# 函数: 启动应用
start() {
    echo "========================================="
    echo "启动 ${APP_NAME} (优化配置)"
    echo "========================================="
    
    # 检查是否已经运行
    if [ -f ${PID_FILE} ]; then
        PID=$(cat ${PID_FILE})
        if ps -p ${PID} > /dev/null 2>&1; then
            echo "应用已经在运行 (PID: ${PID})"
            exit 1
        else
            rm -f ${PID_FILE}
        fi
    fi
    
    # 检查JAR文件是否存在
    if [ ! -f ${JAR_FILE} ]; then
        echo "错误: JAR文件不存在: ${JAR_FILE}"
        echo "请先执行: mvn clean package"
        exit 1
    fi
    
    # 显示JVM配置
    echo "JVM配置:"
    echo "  堆内存: 4G-6G"
    echo "  垃圾收集器: G1GC"
    echo "  最大GC暂停: 200ms"
    echo "  JMX端口: 9999"
    echo ""
    
    # 启动应用
    echo "启动命令:"
    echo "java ${JVM_OPTS} -jar ${JAR_FILE}"
    echo ""
    
    nohup java ${JVM_OPTS} -jar ${JAR_FILE} > ${LOG_DIR}/application.log 2>&1 &
    
    # 保存PID
    echo $! > ${PID_FILE}
    
    echo "应用启动中... PID: $(cat ${PID_FILE})"
    echo "日志文件: ${LOG_DIR}/application.log"
    echo "GC日志: ${LOG_DIR}/gc.log"
    echo ""
    
    # 等待启动
    sleep 5
    
    # 检查启动状态
    if ps -p $(cat ${PID_FILE}) > /dev/null 2>&1; then
        echo "✅ 应用启动成功!"
        echo "🌐 访问地址: http://localhost:8080/api"
        echo "📊 健康检查: http://localhost:8080/api/actuator/health"
        echo "📈 性能指标: http://localhost:8080/api/actuator/metrics"
        echo "🔧 JMX端口: 9999"
        echo "ℹ️  注意: Druid监控页面已禁用"
    else
        echo "❌ 应用启动失败，请检查日志: ${LOG_DIR}/application.log"
        rm -f ${PID_FILE}
        exit 1
    fi
}

# 函数: 停止应用
stop() {
    echo "停止 ${APP_NAME}..."
    
    if [ -f ${PID_FILE} ]; then
        PID=$(cat ${PID_FILE})
        if ps -p ${PID} > /dev/null 2>&1; then
            kill ${PID}
            echo "等待应用停止..."
            
            # 等待最多30秒
            for i in {1..30}; do
                if ! ps -p ${PID} > /dev/null 2>&1; then
                    echo "✅ 应用已停止"
                    rm -f ${PID_FILE}
                    return 0
                fi
                sleep 1
            done
            
            # 强制停止
            echo "强制停止应用..."
            kill -9 ${PID}
            rm -f ${PID_FILE}
            echo "✅ 应用已强制停止"
        else
            echo "应用未运行"
            rm -f ${PID_FILE}
        fi
    else
        echo "PID文件不存在，应用可能未运行"
    fi
}

# 函数: 重启应用
restart() {
    stop
    sleep 2
    start
}

# 函数: 查看状态
status() {
    if [ -f ${PID_FILE} ]; then
        PID=$(cat ${PID_FILE})
        if ps -p ${PID} > /dev/null 2>&1; then
            echo "✅ ${APP_NAME} 正在运行 (PID: ${PID})"
            
            # 显示内存使用情况
            echo ""
            echo "内存使用情况:"
            ps -p ${PID} -o pid,ppid,pcpu,pmem,vsz,rss,comm
            
            # 显示JVM信息
            echo ""
            echo "JVM信息:"
            jps -v | grep ${PID} 2>/dev/null || echo "无法获取JVM信息"
        else
            echo "❌ ${APP_NAME} 未运行 (PID文件存在但进程不存在)"
            rm -f ${PID_FILE}
        fi
    else
        echo "❌ ${APP_NAME} 未运行"
    fi
}

# 主程序
case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status}"
        echo ""
        echo "命令说明:"
        echo "  start   - 启动应用 (使用优化的JVM参数)"
        echo "  stop    - 停止应用"
        echo "  restart - 重启应用"
        echo "  status  - 查看应用状态"
        echo ""
        echo "优化特性:"
        echo "  ✅ G1垃圾收集器 (低延迟)"
        echo "  ✅ 4G-6G堆内存配置"
        echo "  ✅ JMX监控支持"
        echo "  ✅ GC日志记录"
        echo "  ✅ 性能调优参数"
        exit 1
        ;;
esac

exit 0
