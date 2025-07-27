# 缓存管理平台API测试脚本使用指南

**创建时间**: 2025年07月24日 19:19  
**版本**: v1.0  
**适用系统**: Windows / Linux / macOS  

## 📋 文件说明

### 测试脚本文件
- `cache_management_test.py` - 完整的Python测试脚本（高级功能）
- `run_cache_tests.py` - 简化的Python测试脚本（推荐使用）
- `run_tests.bat` - Windows批处理脚本
- `run_tests.sh` - Linux/macOS Shell脚本

### 测试报告
- `test_results/` - 测试结果输出目录
- `cache_test.log` - 测试执行日志

## 🚀 快速开始

### Windows用户
```cmd
# 方法1: 直接运行批处理脚本
scripts\run_tests.bat

# 方法2: 手动运行Python脚本
cd scripts
python run_cache_tests.py
```

### Linux/macOS用户
```bash
# 方法1: 运行Shell脚本
./scripts/run_tests.sh

# 方法2: 手动运行Python脚本
cd scripts
python3 run_cache_tests.py
```

## 📋 前置条件

### 1. 应用启动
确保缓存管理应用已经启动并监听8081端口：
```bash
# 启动应用
mvn spring-boot:run

# 验证端口监听
netstat -an | findstr :8081    # Windows
netstat -an | grep :8081       # Linux/macOS
```

### 2. Python环境
- Python 3.6+ 
- requests库（脚本会自动安装）

### 3. 网络连接
- 能够访问 http://127.0.0.1:8081
- Redis服务正常运行（如果使用Redis缓存）

## 🧪 测试内容

### 基础API测试
1. **全局缓存统计** - `GET /api/admin/cache/stats`
2. **缓存健康检查** - `GET /api/admin/cache/health`
3. **缓存配置信息** - `GET /api/admin/cache/config`
4. **模块统计查询** - `GET /api/admin/cache/module/{module}`
5. **类型统计查询** - `GET /api/admin/cache/type/{type}`

### 管理操作测试
6. **重置熔断器** - `POST /api/admin/cache/circuit-breaker/reset`
7. **删除缓存项** - `DELETE /api/admin/cache/evict/{key}`
8. **功能测试** - `POST /api/admin/cache/test`
9. **清空所有缓存** - `POST /api/admin/cache/clear`

### 安全控制测试
10. **本地访问验证** - 正常访问应该成功
11. **远程访问拒绝** - 伪造远程IP应该被拒绝

## 📊 测试结果解读

### 成功示例
```
✅ 通过 全局缓存统计
   📈 总缓存数: 156
   📊 类型分布: {"L1": 89, "L2": 67}

✅ 通过 缓存健康检查
   💓 Redis状态: true
   🔧 熔断器状态: CLOSED

🎉 所有测试通过！缓存管理平台工作正常。
```

### 失败示例
```
❌ 失败 全局缓存统计
   连接失败: 无法连接到 http://127.0.0.1:8081/api/admin/cache/stats
   💡 请确保应用已启动并监听8081端口

⚠️  有 1 个测试失败，请检查应用状态和配置。
```

## 🔧 故障排查

### 常见问题

#### 1. 连接失败
**现象**: `连接失败: 无法连接到 http://127.0.0.1:8081`

**解决方案**:
```bash
# 检查应用是否启动
ps aux | grep java                    # Linux/macOS
tasklist | findstr java              # Windows

# 检查端口监听
netstat -tulpn | grep :8081          # Linux/macOS
netstat -an | findstr :8081          # Windows

# 重新启动应用
mvn spring-boot:run
```

#### 2. 403错误
**现象**: `请求失败: 403`

**解决方案**:
- 检查IP访问控制配置
- 确认安全配置是否正确
- 查看应用日志中的访问控制信息

#### 3. 超时错误
**现象**: `请求超时`

**解决方案**:
- 检查Redis连接状态
- 查看应用性能和资源使用
- 增加超时时间配置

#### 4. Python依赖问题
**现象**: `ModuleNotFoundError: No module named 'requests'`

**解决方案**:
```bash
# 安装requests库
pip install requests      # Windows
pip3 install requests     # Linux/macOS
```

### 调试命令

```bash
# 查看应用日志
tail -f logs/application.log

# 检查Redis状态
redis-cli ping

# 检查系统资源
top -p $(pgrep java)      # Linux/macOS
tasklist /fi "imagename eq java.exe"  # Windows

# 手动测试API
curl http://127.0.0.1:8081/api/admin/cache/stats
```

## 📈 高级测试

### 运行完整测试套件
```bash
cd scripts
python cache_management_test.py --concurrent-users 10 --output-dir ./results
```

### 自定义测试参数
```bash
# 指定不同的服务器地址
python run_cache_tests.py http://192.168.1.100:8081

# 运行性能测试
python cache_management_test.py --test performance

# 生成HTML报告
python cache_management_test.py --report-format html
```

## 📝 测试报告

测试完成后，会在以下位置生成报告：
- `test_results/test_report.json` - JSON格式详细报告
- `cache_test.log` - 测试执行日志
- 控制台输出 - 实时测试结果

## 🔄 持续集成

可以将测试脚本集成到CI/CD流程中：

```yaml
# GitHub Actions示例
- name: 运行缓存管理API测试
  run: |
    cd scripts
    python run_cache_tests.py
```

## 📞 技术支持

如果遇到问题，请：
1. 查看本文档的故障排查部分
2. 检查应用日志文件
3. 确认网络和端口配置
4. 验证Python环境和依赖

---

**最后更新**: 2025年07月24日 19:19  
**维护者**: Augment Agent  
**版本**: v1.0
