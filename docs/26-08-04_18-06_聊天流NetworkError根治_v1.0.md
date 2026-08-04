# 26-08-04 18:06 聊天流 Network Error 根治 v1.0

## 问题

1. 前端 AI 正常出字，但每次对话结束 UI 显示 `Error: network error`
2. 此时点「重新生成」提示没有该条消息；刷新后正常

## 根因

### 主因：chunked 响应缺终止块

`ChatApiController` 使用 `ResponseBodyEmitter`，业务线程（Reactor）调用 `emitter.complete()` 后，Tomcat 写出的 HTTP/1.1 chunked 响应**没有**终止块 `0\r\n\r\n`，连接直接关闭。

抓包/raw socket 证据（修复前）：

```text
...d:{"finishReason":"stop",...}\n\r\n
FINAL_CHUNK: NO
curl: (18) transfer closed with outstanding read data remaining
HTTP/2: INTERNAL_ERROR (err 2)
```

内容与 `d:` 完成帧都已到达，但连接收尾不干净 → 浏览器 `useChat` 抛 Network error。

### 连带：重试用临时 messageId

`useChat` 流式阶段用前端临时 id；真实 DB id 依赖 `onFinish` → `loadChatMessages`。
Network error 时常跳过 `onFinish` → 重试用临时 id → 后端找不到消息。

## 修复

### 后端（根治）

- `ChatStreamSink.ofAsync(AsyncContext, writer)`：直接写 `ServletOutputStream` + `asyncContext.complete()`
- `ChatApiController` 的 `/chat`、`/retry`、`/edit-resend` 改为 Servlet Async，不再返回 `ResponseBodyEmitter`
- 废弃跨线程 `ResponseBodyEmitter` 路径

修复后：

```text
FINAL_CHUNK: True
curl_exit=0（直连 / Nginx HTTP1.1 / HTTP2 均为 0）
```

### 前端（兜底，防御性）

- `onError`：静默 `loadChatMessages` 对齐 DB id
- `handleRetryMessage`：重试前若本地 id 不在库中，用接口消息列表解析真实 id
- 已有 assistant 内容时不展示残留 `network` 错误红条

### 代码清理

- 删除无调用的 `ChatStreamSink.of(ResponseBodyEmitter)` 死路径，避免双实现冗余

## 部署

- 后端 jar 已发版并重启 `chat:chat_00`
- 用户前端 Build ID：`eioZoffBLNxuwBT1ZpBu7`，已部署 `/opt/tousechat-frontend`

## 验证清单

- [x] 直连 8080 raw socket 含 `0\r\n\r\n`
- [x] curl 直连 exit 0
- [x] 公网 HTTP/1.1 exit 0
- [x] 公网 HTTP/2 exit 0
- [ ] 浏览器硬刷新后发一条消息：无 Network error 红字
- [ ] 不刷新直接点重新生成：成功
