# Gemini API密钥管理接口使用手册

## 概述

Gemini API密钥管理系统提供了一套完整的REST API接口，用于管理多个Gemini API密钥的轮询使用、健康监控和动态配置。

**⚠️ 安全限制**：所有管理接口仅限localhost访问，无需身份认证。

## 接口列表

### 1. 获取密钥统计信息

**接口地址**：`GET /api/admin/gemini/keys/stats`

**功能**：获取所有API密钥的详细统计信息

**请求示例**：
```bash
curl "http://localhost:8080/api/admin/gemini/keys/stats"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "获取密钥统计成功",
  "data": {
    "keys": [
      {
        "apiKey": "your-google-api-key",
        "available": true,
        "totalRequests": 156,
        "successRequests": 152,
        "failureCount": 0,
        "successRate": 0.974,
        "lastUsed": "2025-07-16T19:30:15",
        "weight": 1
      }
    ],
    "totalKeys": 3,
    "availableKeys": 2,
    "strategy": "ROUND_ROBIN"
  }
}
```

**字段说明**：
- `apiKey`: 完整的API密钥
- `available`: 密钥是否可用
- `totalRequests`: 总请求次数
- `successRequests`: 成功请求次数
- `failureCount`: 当前失败次数
- `successRate`: 成功率（0-1之间）
- `lastUsed`: 最后使用时间
- `weight`: 权重（用于加权轮询）
- `totalKeys`: 总密钥数量
- `availableKeys`: 可用密钥数量
- `strategy`: 当前轮询策略

### 2. 更新密钥列表

**接口地址**：`POST /api/admin/gemini/keys/update`

**功能**：动态更新API密钥列表

**请求示例**：
```bash
curl -X POST "http://localhost:8080/api/admin/gemini/keys/update" \
  -H "Content-Type: application/json" \
  -d '{
    "keys": [
      "your-google-api-key",
      "your-google-api-key2",
      "your-google-api-key3"
    ]
  }'
```

**请求参数**：
```json
{
  "keys": ["密钥1", "密钥2", "密钥3"]
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": "API密钥列表更新成功"
}
```

### 3. 获取健康状态

**接口地址**：`GET /api/admin/gemini/keys/health`

**功能**：获取密钥系统的健康状态（与stats接口返回相同数据）

**请求示例**：
```bash
curl "http://localhost:8080/api/admin/gemini/keys/health"
```

### 4. 禁用指定密钥

**接口地址**：`POST /api/admin/gemini/keys/{keyIndex}/disable`

**功能**：手动禁用指定索引的密钥

**请求示例**：
```bash
curl -X POST "http://localhost:8080/api/admin/gemini/keys/0/disable"
```

### 5. 启用指定密钥

**接口地址**：`POST /api/admin/gemini/keys/{keyIndex}/enable`

**功能**：手动启用指定索引的密钥

**请求示例**：
```bash
curl -X POST "http://localhost:8080/api/admin/gemini/keys/0/enable"
```

## 轮询策略

系统支持三种轮询策略，可通过配置文件设置：

### 1. 轮询策略 (ROUND_ROBIN)
- **默认策略**
- 按顺序依次使用每个可用密钥
- 适合负载均衡场景

### 2. 随机策略 (RANDOM)
- 随机选择可用密钥
- 适合避免固定模式的场景

### 3. 加权策略 (WEIGHTED)
- 根据密钥权重分配使用概率
- 适合不同密钥有不同配额的场景

## 配置文件示例

```yaml
gemini:
  api:
    # 多密钥配置
    keys:
      - your-google-api-key
      - your-google-api-key2
      - your-google-api-key3
    
    # 轮询策略
    strategy: ROUND_ROBIN
    
    # 健康检查间隔（秒）
    health-check-interval: 300
    
    # 密钥失败重试次数
    max-retries: 3
    
    # 密钥禁用时间（秒）
    disable-timeout: 600
```

## 故障处理机制

### 自动故障检测
- 当密钥连续失败达到`max-retries`次时自动禁用
- 禁用时间为`disable-timeout`秒
- 禁用期满后自动重新启用

### 健康检查
- 每`health-check-interval`秒执行一次健康检查
- 监控长时间未使用的密钥
- 自动恢复被禁用的密钥

## 使用场景

### 1. 日常监控
```bash
# 查看当前密钥状态
curl "http://localhost:8080/api/admin/gemini/keys/stats" | jq .
```

### 2. 添加新密钥
```bash
# 获取当前密钥列表，添加新密钥后更新
curl -X POST "http://localhost:8080/api/admin/gemini/keys/update" \
  -H "Content-Type: application/json" \
  -d '{"keys": ["现有密钥1", "现有密钥2", "新密钥"]}'
```

### 3. 应急处理
```bash
# 当某个密钥出现问题时，临时移除
curl -X POST "http://localhost:8080/api/admin/gemini/keys/update" \
  -H "Content-Type: application/json" \
  -d '{"keys": ["正常密钥1", "正常密钥2"]}'
```

## 注意事项

1. **localhost限制**：接口只能从localhost访问，确保安全性
2. **无需认证**：接口不需要JWT认证，但有IP限制
3. **动态生效**：密钥更新立即生效，无需重启应用
4. **向后兼容**：保持原有单密钥配置的兼容性
5. **日志记录**：所有操作都有详细的日志记录

## 错误处理

### 常见错误码
- `200`: 操作成功
- `400`: 请求参数错误
- `403`: 访问被拒绝（非localhost访问）
- `500`: 服务器内部错误

### 错误示例
```json
{
  "code": 403,
  "message": "访问被拒绝：仅允许localhost访问",
  "data": null
}
```

---

**文档版本**: v1.0  
**最后更新**: 2025-07-16  
**适用版本**: Chat Backend v1.0+
