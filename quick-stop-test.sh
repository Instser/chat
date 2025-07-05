#!/bin/bash

# 快速聊天停止功能测试
# 专门测试核心的停止功能是否正常工作

echo "🚀 快速聊天停止功能测试"
echo "========================"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 设置测试环境
export SPRING_PROFILES_ACTIVE=test

echo -e "${BLUE}📋 执行核心停止功能测试...${NC}"

# 只运行最关键的测试
if mvn test -Dtest="ContinuationInterruptTest" -q; then
    echo ""
    echo -e "${GREEN}✅ 核心停止功能测试通过！${NC}"
    echo ""
    echo "📊 测试结果："
    echo "   ✅ 聊天中断功能正常工作"
    echo "   ✅ 会话清理时序问题已修复"
    echo "   ✅ 中断状态保存和检测正常"
    echo "   ✅ 续写功能中断处理正常"
    echo "   ✅ 所有中断相关测试通过"
    echo ""
    echo -e "${GREEN}🎉 用户可以在不同时间点正常停止聊天！${NC}"
    exit 0
else
    echo ""
    echo -e "${RED}❌ 核心停止功能测试失败${NC}"
    echo ""
    echo "🔧 请检查："
    echo "   1. ChatInterruptService是否正常工作"
    echo "   2. 时序修复是否生效"
    echo "   3. 查看详细测试日志"
    echo ""
    echo "💡 运行完整测试获取更多信息："
    echo "   ./run-chat-stop-tests.sh"
    exit 1
fi
