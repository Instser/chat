@echo off
chcp 65001 >nul
echo ========================================
echo 缓存管理平台API测试脚本
echo 创建时间: 2025-07-24 19:19
echo ========================================
echo.

REM 检查Python是否安装
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ 错误: 未找到Python，请先安装Python 3.6+
    echo 💡 下载地址: https://www.python.org/downloads/
    pause
    exit /b 1
)

echo ✅ Python已安装
python --version

REM 检查并安装依赖
echo.
echo 🔍 检查Python依赖...
python -c "import requests" >nul 2>&1
if %errorlevel% neq 0 (
    echo ⚠️  未找到requests库，正在安装...
    pip install requests
    if %errorlevel% neq 0 (
        echo ❌ 安装requests失败，请手动执行: pip install requests
        pause
        exit /b 1
    )
)

echo ✅ 依赖检查完成

REM 检查应用是否运行
echo.
echo 🔍 检查应用状态...
netstat -an | findstr :8081 >nul 2>&1
if %errorlevel% neq 0 (
    echo ⚠️  警告: 未检测到8081端口监听
    echo 💡 请确保应用已启动: mvn spring-boot:run
    echo.
    set /p choice="是否继续测试? (y/n): "
    if /i not "%choice%"=="y" (
        echo 测试已取消
        pause
        exit /b 0
    )
) else (
    echo ✅ 检测到8081端口正在监听
)

REM 运行测试
echo.
echo 🚀 开始执行API测试...
echo ========================================
python run_cache_tests.py

REM 检查测试结果
if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo 🎉 测试完成！所有测试通过
    echo ========================================
) else (
    echo.
    echo ========================================
    echo ⚠️  测试完成，但有部分测试失败
    echo 💡 请检查应用日志和配置
    echo ========================================
)

echo.
echo 📋 后续操作建议:
echo    1. 查看应用日志: tail -f logs/application.log
echo    2. 检查Redis连接: redis-cli ping
echo    3. 重启应用: mvn spring-boot:run
echo    4. 运行完整测试: python cache_management_test.py

pause
