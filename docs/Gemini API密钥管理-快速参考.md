# Gemini API密钥管理 - 快速参考

## 🔑 接口概览

**基础URL**: `http://localhost:8080/api/admin/gemini`  
**访问限制**: 仅限localhost，无需认证

## 📋 常用命令

### 查看密钥状态
```bash
curl "http://localhost:8080/api/admin/gemini/keys/stats" | jq .
```

### 更新密钥列表
```bash
curl -X POST "http://localhost:8080/api/admin/gemini/keys/update" \
  -H "Content-Type: application/json" \
  -d '{"keys": ["密钥1", "密钥2", "密钥3"]}'
```

### 禁用/启用密钥
```bash
# 禁用第0个密钥
curl -X POST "http://localhost:8080/api/admin/gemini/keys/0/disable"

# 启用第0个密钥  
curl -X POST "http://localhost:8080/api/admin/gemini/keys/0/enable"
```

## 📊 响应数据说明

```json
{
  "keys": [
    {
      "apiKey": "完整密钥",           // 完整API密钥
      "available": true,            // 是否可用
      "totalRequests": 156,         // 总请求数
      "successRequests": 152,       // 成功请求数
      "successRate": 0.974,         // 成功率
      "failureCount": 0,            // 当前失败次数
      "lastUsed": "2025-07-16T19:30:15"  // 最后使用时间
    }
  ],
  "totalKeys": 3,                   // 总密钥数
  "availableKeys": 2,               // 可用密钥数
  "strategy": "ROUND_ROBIN"         // 轮询策略
}
```

## ⚙️ 轮询策略

- **ROUND_ROBIN**: 轮询使用（默认）
- **RANDOM**: 随机选择
- **WEIGHTED**: 按权重分配

## 🔧 配置示例

```yaml
gemini:
  api:
    keys:
      - your-google-api-key
      - your-google-api-key2
    strategy: ROUND_ROBIN
    max-retries: 3              # 失败重试次数
    disable-timeout: 600        # 禁用时间(秒)
    health-check-interval: 300  # 健康检查间隔(秒)
```

## 🚨 故障处理

- **自动禁用**: 连续失败3次后自动禁用600秒
- **自动恢复**: 禁用期满后自动重新启用
- **健康检查**: 每5分钟检查一次密钥状态

## 💡 使用技巧

### 1. 监控脚本
```bash
#!/bin/bash
# 每分钟检查密钥状态
while true; do
  echo "=== $(date) ==="
  curl -s "http://localhost:8080/api/admin/gemini/keys/stats" | jq '.data | {totalKeys, availableKeys, strategy}'
  sleep 60
done
```

### 2. 批量更新
```bash
# 从文件读取密钥列表
KEYS=$(cat gemini_keys.txt | jq -R . | jq -s .)
curl -X POST "http://localhost:8080/api/admin/gemini/keys/update" \
  -H "Content-Type: application/json" \
  -d "{\"keys\": $KEYS}"
```

### 3. 应急处理
```bash
# 快速移除故障密钥
curl -X POST "http://localhost:8080/api/admin/gemini/keys/update" \
  -H "Content-Type: application/json" \
  -d '{"keys": ["备用密钥1", "备用密钥2"]}'
```

---
**快速参考** | **版本**: v1.0 | **更新**: 2025-07-16
