# 管理员 dashboard/overview 超时修复

> 时间：2026-08-04 10:27（北京时间）  
> 版本：v1.0

## 现象
浏览器访问 `GET /api/admin/dashboard/overview` 长时间无响应（体感超时）。

## 根因
`AdminOpsService.overview()` 内嵌 `health()`，其中 `checkAiApi()` 调用：

```java
geminiNetworkService.isServiceAvailable()
```

该方法会真实请求 Gemini 联网代理：

```
http://181.215.6.137:5321/v1beta/models/gemini-2.5-flash:generateContent
```

外网不可达时连接超时约 **130s**，导致 overview 慢请求（日志 `SLOW_REQUEST_ERROR costMs≈130000`）。

其它接口正常（`/api/admin/users` ~0.6s，`/api/admin/ops/version` ~50ms），说明不是鉴权/frp 全链路故障。

另：从本机直连 `https://chat.instser.cn` 可能被备案/防火墙 `Connection reset`，公网机本机 `--resolve` 正常。

## 修复
`checkAiApi()` 改为 **仅检查可用模型配置**（DB 查询），不再发起外部 AI 探测：

- 文件：`src/main/java/com/instser/chat/admin/service/AdminOpsService.java`
- 返回 `probe=config-only` + `availableModels`

## 验证
- 家用机本机：`overview 200 time 0.28s`
- 公网 Nginx：`overview 200 time 0.20s`
- health.aiApi：`UP, probe=config-only, availableModels=6`
- 后端已重启：`chat:chat_00 RUNNING`

## 备注
若后续需要真实 AI 连通性探测，应使用短超时（≤2s）异步/可选接口，不要挂在仪表盘 overview 同步路径上。
