#!/bin/bash
# 缓存管理平台API测试脚本
# 创建时间: 2025-07-24 19:19

echo "========================================"
echo "缓存管理平台API测试脚本"
echo "创建时间: 2025-07-24 19:19"
echo "========================================"
echo

# 检查Python是否安装
if ! command -v python3 &> /dev/null; then
    echo "❌ 错误: 未找到Python3，请先安装Python 3.6+"
    echo "💡 Ubuntu/Debian: sudo apt install python3 python3-pip"
    echo "💡 CentOS/RHEL: sudo yum install python3 python3-pip"
    echo "💡 macOS: brew install python3"
    exit 1
fi

echo "✅ Python已安装"
python3 --version

# 检查并安装依赖
echo
echo "🔍 检查Python依赖..."
if ! python3 -c "import requests" &> /dev/null; then
    echo "⚠️  未找到requests库，正在安装..."
    if ! pip3 install requests; then
        echo "❌ 安装requests失败，请手动执行: pip3 install requests"
        exit 1
    fi
fi

echo "✅ 依赖检查完成"

# 检查应用是否运行
echo
echo "🔍 检查应用状态..."
if ! netstat -an 2>/dev/null | grep -q ":8081.*LISTEN" && ! ss -an 2>/dev/null | grep -q ":8081.*LISTEN"; then
    echo "⚠️  警告: 未检测到8081端口监听"
    echo "💡 请确保应用已启动: mvn spring-boot:run"
    echo
    read -p "是否继续测试? (y/n): " choice
    if [[ ! "$choice" =~ ^[Yy]$ ]]; then
        echo "测试已取消"
        exit 0
    fi
else
    echo "✅ 检测到8081端口正在监听"
fi

# 运行测试
echo
echo "🚀 开始执行API测试..."
echo "========================================"
python3 run_cache_tests.py

# 检查测试结果
if [ $? -eq 0 ]; then
    echo
    echo "========================================"
    echo "🎉 测试完成！所有测试通过"
    echo "========================================"
else
    echo
    echo "========================================"
    echo "⚠️  测试完成，但有部分测试失败"
    echo "💡 请检查应用日志和配置"
    echo "========================================"
fi

echo
echo "📋 后续操作建议:"
echo "   1. 查看应用日志: tail -f logs/application.log"
echo "   2. 检查Redis连接: redis-cli ping"
echo "   3. 重启应用: mvn spring-boot:run"
echo "   4. 运行完整测试: python3 cache_management_test.py"
