#!/bin/bash

# 日志级别管理测试脚本
# 用于验证启动参数和Actuator端点的日志级别管理功能

echo "========================================="
echo "金语聊天系统日志级别管理测试"
echo "========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 测试函数
test_actuator_endpoint() {
    echo -e "${BLUE}测试Actuator端点...${NC}"
    
    # 测试健康检查端点
    echo "1. 测试健康检查端点"
    curl -s http://127.0.0.1:8081/actuator/health | jq . || echo "健康检查端点不可用"
    
    # 测试日志端点
    echo -e "\n2. 测试日志端点"
    curl -s http://127.0.0.1:8081/actuator/loggers/com.instser.chat | jq . || echo "日志端点不可用"
    
    echo -e "${GREEN}Actuator端点测试完成${NC}\n"
}

test_log_management_api() {
    echo -e "${BLUE}测试自定义日志管理API...${NC}"
    
    # 测试获取所有Logger级别
    echo "1. 获取所有Logger级别"
    curl -s http://127.0.0.1:8080/api/management/logs/levels | jq . || echo "日志管理API不可用"
    
    # 测试设置开发环境级别
    echo -e "\n2. 设置开发环境级别"
    curl -s -X POST http://127.0.0.1:8080/api/management/logs/presets/development | jq . || echo "设置开发环境级别失败"
    
    echo -e "${GREEN}自定义日志管理API测试完成${NC}\n"
}

show_startup_examples() {
    echo -e "${YELLOW}启动参数示例：${NC}"
    echo ""
    echo "1. 开发环境启动："
    echo "java -jar chat.jar --logging.level.com.instser.chat=DEBUG --logging.level.root=INFO"
    echo ""
    echo "2. 生产环境启动："
    echo "java -jar chat.jar --logging.level.com.instser.chat=INFO --logging.level.root=WARN"
    echo ""
    echo "3. 问题排查启动："
    echo "java -jar chat.jar --logging.level.com.instser.chat=DEBUG --logging.level.com.instser.chat.service.serviceimpl.ChatServiceImpl=DEBUG"
    echo ""
    echo "4. 开启SQL日志："
    echo "java -jar chat.jar --logging.level.com.instser.chat.dao=DEBUG --logging.level.java.sql.PreparedStatement=DEBUG"
    echo ""
}

show_actuator_examples() {
    echo -e "${YELLOW}Actuator端点使用示例：${NC}"
    echo ""
    echo "1. 查看当前日志级别："
    echo "curl http://127.0.0.1:8081/actuator/loggers/com.instser.chat"
    echo ""
    echo "2. 设置DEBUG级别："
    echo "curl -X POST http://127.0.0.1:8081/actuator/loggers/com.instser.chat -H \"Content-Type: application/json\" -d '{\"configuredLevel\": \"DEBUG\"}'"
    echo ""
    echo "3. 恢复INFO级别："
    echo "curl -X POST http://127.0.0.1:8081/actuator/loggers/com.instser.chat -H \"Content-Type: application/json\" -d '{\"configuredLevel\": \"INFO\"}'"
    echo ""
    echo "4. 重置为继承级别："
    echo "curl -X POST http://127.0.0.1:8081/actuator/loggers/com.instser.chat -H \"Content-Type: application/json\" -d '{\"configuredLevel\": null}'"
    echo ""
}

show_custom_api_examples() {
    echo -e "${YELLOW}自定义日志管理API示例：${NC}"
    echo ""
    echo "1. 获取所有Logger级别："
    echo "curl http://127.0.0.1:8080/api/management/logs/levels"
    echo ""
    echo "2. 设置开发环境级别："
    echo "curl -X POST http://127.0.0.1:8080/api/management/logs/presets/development"
    echo ""
    echo "3. 设置生产环境级别："
    echo "curl -X POST http://127.0.0.1:8080/api/management/logs/presets/production"
    echo ""
    echo "4. 开启SQL日志："
    echo "curl -X POST http://127.0.0.1:8080/api/management/logs/sql/enable"
    echo ""
    echo "5. 关闭SQL日志："
    echo "curl -X POST http://127.0.0.1:8080/api/management/logs/sql/disable"
    echo ""
}

# 主菜单
show_menu() {
    echo -e "${GREEN}请选择测试选项：${NC}"
    echo "1. 测试Actuator端点"
    echo "2. 测试自定义日志管理API"
    echo "3. 显示启动参数示例"
    echo "4. 显示Actuator端点使用示例"
    echo "5. 显示自定义API使用示例"
    echo "6. 退出"
    echo ""
    read -p "请输入选项 (1-6): " choice
}

# 主循环
while true; do
    show_menu
    case $choice in
        1)
            test_actuator_endpoint
            ;;
        2)
            test_log_management_api
            ;;
        3)
            show_startup_examples
            ;;
        4)
            show_actuator_examples
            ;;
        5)
            show_custom_api_examples
            ;;
        6)
            echo -e "${GREEN}测试结束${NC}"
            exit 0
            ;;
        *)
            echo -e "${RED}无效选项，请重新选择${NC}"
            ;;
    esac
    echo ""
    read -p "按回车键继续..."
    echo ""
done
