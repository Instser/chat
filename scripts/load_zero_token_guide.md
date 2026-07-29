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

## 方案 2：本地 Mock 上游（推荐，0 token 测流式）

### 步骤

1. 启动 Mock：
```bash
python scripts/mock_openai_sse_server.py --port 18080 --delay-ms 10
```

2. **临时**把「仅测试用」模型的 `base_url` 改成：
```text
http://127.0.0.1:18080/v1/chat/completions
```
（数据库 `ai_model` 表，或管理后台；测完改回。）

3. 跑流式并发（仍走完整后端，但不打真实 AI）：
```bash
python scripts/load_concurrent_stream.py \
  --base http://127.0.0.1:8080/api \
  --model-id <测试模型ID> \
  --concurrency 10 --users 10
```

注意：若产品限制「同用户同时只能 1 路聊天」，单账号 10 并发仍会有一半业务拒绝——这是产品规则，不是 token 问题。真并发请多测试账号，或错峰 `--stagger-ms`。

### 服务器上

- Mock 可与后端同机：`127.0.0.1:18080`
- 或单独压测机跑 Mock，模型 `baseUrl` 指向该内网地址
- **不要**把生产主模型永久改成 Mock

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
