# 零 AI Token 并发检测指南

## 原则

| 想测的能力 | 是否需要真 AI | 做法 |
|---|---|---|
| 登录/鉴权/Tomcat/DB/Redis | 否 | `load_concurrent_api.py` |
| Emitter 非阻塞、协议帧、stop | 否 | Mock 上游 + 流式脚本 |
| 上游 DeepSeek 真实延迟/配额 | 是 | 才用真 Key（尽量少样本） |

---

## 方案 1：只压短接口（最省事，0 token）

```bash
python scripts/load_concurrent_api.py \
  --base http://127.0.0.1:8080/api \
  --concurrency 50 --total 200
```

覆盖：登录、`/chat/models`、`/chat/sessions`。  
**不覆盖**流式写出与 AI 处理链路。

---

## 方案 2：启动参数劫持上游（推荐，不改 DB）

**不要改数据库模型 baseUrl。** 用运行时开关把主聊天流（及标题生成）指到本地 Mock：

```properties
chat.loadtest.mock-upstream-enabled=true
chat.loadtest.mock-upstream-url=http://127.0.0.1:18080/v1/chat/completions
```

### 步骤

1. 启动 Mock：
```bash
python scripts/mock_openai_sse_server.py --port 18080 --delay-ms 5
```

2. 启动后端时打开开关（示例）：
```bash
mvn -DskipTests spring-boot:run \
  -Dspring-boot.run.arguments="--chat.loadtest.mock-upstream-enabled=true --chat.loadtest.mock-upstream-url=http://127.0.0.1:18080/v1/chat/completions"
```

3. 跑流式并发（0 token）：
```bash
# 同账号错峰（避开「同时只能一路聊天」）
python scripts/load_concurrent_stream.py --concurrency 5 --users 5 --stagger-ms 6000 --max-wait 30

# 真·同时并发会触发 product_limit:active_session（预期）
python scripts/load_concurrent_stream.py --concurrency 10 --users 10 --max-wait 20
```

日志应出现：`LOADTEST mock-upstream 已启用，AI 流改打: http://127.0.0.1:18080/...`

### 为何不「写死测试模型 / 改库」

| 做法 | 问题 |
|---|---|
| 改 DB 某模型 baseUrl | 容易忘改回，污染环境 |
| 代码写死 modelId→mock | 耦合业务、难关 |
| **启动参数覆盖** | 默认关；不改库；可随时关 |

### 服务器上

- Mock 与后端同机即可
- **生产默认 enabled=false**
- 压测完关掉开关重启

---

## 方案 3：Spring 测试 Profile 注入 Fake AiChatClient（工程化）

适合 CI：
- `AiChatClient` 测试实现直接 `Flux` 吐假 SSE 行
- `@ActiveProfiles("loadtest")` 或 `@MockBean`
- 零网络、零 token、可进流水线

代价：要加一点测试代码，与生产 bean 隔离。

---

## 方案 4：绝对不要用的“省 token”假方法

- 把并发脚本 prompt 改短但还打真模型 → **仍计 token**
- 只看 HTTP 200 不读流 → **测不到流式**
- 共享 CookieJar 高并发 → 假失败（AccessDenied）

---

## 建议组合（部署验收）

1. `load_concurrent_api.py` 50×200  
2. Mock 上游 + `load_concurrent_stream.py` 10×20（多账号更佳）  
3. 抽样 1～3 次真 AI 冒烟（才允许耗 token）
