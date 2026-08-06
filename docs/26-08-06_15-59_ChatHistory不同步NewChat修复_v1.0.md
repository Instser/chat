# Chat history 不同步 New Chat 修复 v1.0

- 时间：2026-08-06 15:59（北京时间）

## 现象

侧边栏 New Chat 后左侧会话列表有新会话，但 Chat history（`/all-chats`）不出现。

## 根因

1. `/all-chats` 使用**独立** `allChats` 状态，只在 `user` 首次登录时 `loadAllChats()`。
2. 侧边栏 `createNewChat` 只更新 `chat-context.chats`，history 页不订阅。
3. `GET /api/chats` 无参时后端默认 `limit=20`，history 误以为已拉全量。

## 修复（tousechat `6e4ba74`）

- 进入 `/all-chats` 时重新分页拉取全量（pageSize=100）
- 监听 `context.chats`，与本地列表 **merge 去重**
- 按 `updateTime/createdTime` 倒序

## 部署

- 干净 standalone 打包，避免再次出现 `static/static` 错位
- Build ID：`W-iREJLdhxR30Zsmq1o7A`
- systemd：`tousechat-frontend.service`
