# Jinyu Chat — 运维手册

## 1. 部署说明

### 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17 |
| MySQL | 8.0 |
| Redis | 6.x（家用服务器本机 `127.0.0.1:6379`） |

### 启动命令

```bash
java -jar chat.jar --spring.profiles.active=prod
```

### 必需环境变量

| 变量 | 说明 |
|------|------|
| `REDIS_HOST` | Redis 主机地址（生产默认 `127.0.0.1`） |
| `REDIS_PORT` | Redis 端口（生产默认 `6379`） |
| `REDIS_USERNAME` | Redis 用户名（本地 Redis 可为空；云 ACL 才需要） |
| `REDIS_PASSWORD` | Redis 密码（本地 `requirepass`） |
| `GEMINI_API_KEY` | Gemini API 密钥 |
| `GEMINI_BASE_URL` | Gemini API 地址 |
| `MYSQL_PASSWORD` | MySQL 密码 |
| `CACHE_API_KEY` | 管理 API Key（用于 /ops 和 /admin 接口鉴权） |

### 端口说明

| 端口 | 用途 | 对外暴露 |
|------|------|----------|
| 8080 | 应用主端口（/api） | 是 |
| 8081 | Management 端口（Actuator） | 否（127.0.0.1） |

---

## 2. 监控指标

### Prometheus 抓取

```
http://127.0.0.1:8081/api/actuator/prometheus
```

### 关键指标

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `ai_calls_total` | Counter | AI 调用总数（status=success/failed） |
| `ai_call_duration_seconds` | Timer | AI 调用耗时（model + status） |
| `ai_ttft_seconds` | Timer | 首 Token 延迟（model） |
| `ai_token_consumption_total` | Counter | Token 消耗（prompt/completion） |
| `ai_calls_active` | Gauge | 当前活跃 AI 调用数 |
| `ai_calls_failed_total` | Counter | AI 失败分类（model + error_type） |
| `api_requests_total` | Counter | API 请求总数（method/path/status） |
| `api_exceptions_total` | Counter | API 异常总数（method/path/exception_type） |
| `slow_requests_total` | Counter | 慢请求计数（threshold=warn/error） |

---

## 3. 日志规范

### 日志位置

```
logs/
├── jinyu-chat-all.log        # 全量日志
├── jinyu-chat-error.log      # WARN + ERROR
└── jinyu-chat-monitor.log    # 监控日志
```

### 日志格式

```
时间 [线程] [traceId] 级别 Logger - 消息
```

示例：
```
2026-05-25 23:30:00.123 [http-nio-8080-exec-1] [a1b2c3d4e5f6] INFO c.i.c.s.serviceimpl.ChatServiceImpl - AI_CALL_START model=gpt-4
```

### 轮转策略

| 日志文件 | 单文件上限 | 保留天数 | 总量上限 |
|----------|-----------|----------|---------|
| all.log | 100MB | 20 天 | 3GB |
| error.log | 50MB | 45 天 | 1.5GB |
| monitor.log | 100MB | 7 天 | 1GB |

### 排查技巧

```bash
# 按 traceId 追踪完整请求链
grep "a1b2c3d4e5f6" logs/jinyu-chat-all.log

# 查看 AI 调用慢请求（>3s）
grep "SLOW_REQUEST_ERROR" logs/jinyu-chat-all.log

# 查看 AI 调用失败
grep "AI_CALL_FAILED" logs/jinyu-chat-all.log

# 查看指定时间段日志
grep "2026-05-25 23:3[0-9]" logs/jinyu-chat-all.log
```

---

## 4. 运维接口

所有运维接口通过 `X-API-Key` 请求头鉴权。

### 4.1 版本信息

```bash
curl -H "X-API-Key: ${CACHE_API_KEY}" http://127.0.0.1:8080/api/ops/version
```

返回：应用版本、Git commit、构建时间、Java/OS 信息、启动时长、Spring Boot 版本、活跃 Profile。

### 4.2 全组件健康检查

```bash
curl -H "X-API-Key: ${CACHE_API_KEY}" http://127.0.0.1:8080/api/ops/health/full
```

检查项：
- **application** — 应用自身状态 + 运行时长
- **database** — MySQL 连接池（Druid 活跃连接数/最大连接数）
- **redis** — Redis PING 连通性
- **aiApi** — Gemini API 可用性

返回 `overall: UP` 或 `overall: DEGRADED`。

### 4.3 AI 错误分析

```bash
curl -X POST http://127.0.0.1:8080/api/ops/ai/analyze-error \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${CACHE_API_KEY}" \
  -d '{"errorLog": "NullPointerException at ChatServiceImpl.java:1602", "contextInfo": "用户发送聊天消息时触发"}'
```

返回 AI 分析的异常摘要、可能原因、排查步骤、建议的日志查询语句。

### 4.4 Spring Boot Actuator（端口 8081）

```
http://127.0.0.1:8081/api/actuator/health
http://127.0.0.1:8081/api/actuator/metrics
http://127.0.0.1:8081/api/actuator/prometheus
http://127.0.0.1:8081/api/actuator/loggers
```

### 4.5 管理接口（端口 8080）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/admin/cache/user-package/refresh-all` | POST | 全量清理用户套餐与聊天次数缓存 |
| `/api/admin/logs/levels` | GET | 获取所有 Logger 级别 |
| `/api/admin/logs/levels/{name}` | POST | 设置指定 Logger 级别 |
| `/api/admin/logs/presets/development` | POST | 开发环境日志预设 |
| `/api/admin/logs/presets/production` | POST | 生产环境日志预设 |
| `/api/admin/logs/presets/debugging` | POST | 排查模式日志预设 |
| `/api/admin/logs/sql/enable` | POST | 开启 SQL 日志 |
| `/api/admin/logs/sql/disable` | POST | 关闭 SQL 日志 |
| `/api/admin/logs/files/status` | GET | 日志文件状态 |

---

## 5. 常见故障排查

### 场景1：接口返回 500

1. 从响应中获取 `traceId`
2. `grep "{traceId}" logs/jinyu-chat-all.log` 查看请求完整日志
3. `curl http://127.0.0.1:8080/api/ops/health/full -H "X-API-Key: xxx"` 检查依赖状态
4. 根据 health 结果排查对应组件

### 场景2：AI 调用超时

1. 查看 Prometheus `ai_ttft_seconds` 指标，确认首 Token 延迟
2. `grep "AI_CALL_FAILED" logs/jinyu-chat-error.log` 查看失败原因
3. 检查 AI API 连通性：`curl http://127.0.0.1:8080/api/ops/health/full -H "X-API-Key: xxx"`
4. 如 `aiApi: DOWN`，检查 Gemini API 地址和密钥是否正确

### 场景3：Redis 连接失败

1. 应用自动触发熔断，部分缓存功能降级
2. 查看日志 `grep "circuit" logs/jinyu-chat-error.log`
3. 检查 Redis 服务状态：`redis-cli -h $REDIS_HOST -p $REDIS_PORT ping`
4. 恢复后熔断器自动愈合

### 场景4：支付回调失败

1. 查看 `t_operation_audit` 表确认相关操作记录
2. 查看 `t_payment_audit` 表获取支付审计详情
3. 根据订单号在日志中搜索：`grep "{orderNo}" logs/jinyu-chat-all.log`
4. 必要时通过支付服务商后台手动核实订单状态

### 场景5：缓存数据不一致

1. 调用 `/api/admin/cache/user-package/refresh-all` 全量刷新套餐与权限缓存
2. 或重启应用（启动时 CacheCleanupRunner 自动清理旧格式缓存）

---

## 6. 告警响应

### P0（紧急 — 服务不可用）
- 现象：health check overall=DOWN，接口大面积 500
- 响应：立即查看 error.log，检查 MySQL/Redis/AI API 连通性
- 升级：15 分钟内未恢复 → 通知技术负责人

### P1（严重 — 功能降级）
- 现象：health check overall=DEGRADED，部分接口异常
- 响应：查看对应组件日志，确认降级范围
- 升级：30 分钟内未恢复 → 通知技术负责人

### P2（警告 — 性能劣化）
- 现象：slow_requests_total 持续增长，AI 调用延迟升高
- 响应：分析慢请求路径，检查数据库连接池和 AI API 响应时间
- 升级：1 小时内未缓解 → 评估是否需要扩容
