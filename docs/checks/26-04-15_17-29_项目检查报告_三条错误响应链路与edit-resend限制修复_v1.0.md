# 项目检查报告

- 分析时间（北京时间，已按 `time.is/Beijing` 要求校对）：2026-04-15 17:29
- 版本标识：v1.0
- 检查范围：普通 `chat`、`retry`、`edit-resend` 三条错误响应链路
- 检查目标：核对三条链路在命中聊天限制时的消息落库/删除/替换顺序，并修复 `edit-resend` 在限制场景下未替换为用户新编辑内容的问题

## 架构与模块快照

- 启动入口：
  - 普通聊天：`ChatApiController.chat`
  - 重新生成：`ChatApiController.retryChat`
  - 编辑重发：`ChatApiController.editResendChat`
- 核心模块职责：
  - `ChatApiController`：接收请求并转发到聊天服务
  - `ChatServiceImpl`：执行权限校验、限制校验、消息删除/保存、AI调用
  - `ChatMessageServiceImpl`：负责消息落库、错误响应保存、消息区间删除
  - `ChatMessageMapper.xml`：AI上下文查询时排除 `status=3` 的错误响应
- 依赖方向：Controller -> ChatServiceImpl -> ChatMessageService/SessionService/LimitService

## 关键调用链

1. 普通 `chat`
   - `ChatApiController.chat`
   - `ChatServiceImpl.processStreamChat`
   - `saveUserMessageEarly`
   - `performPreValidationWithMessageSaving`
   - 命中限制时 `saveErrorResponseAndReturn`

2. `retry`
   - `ChatApiController.retryChat`
   - `ChatServiceImpl.processRetryChat`
   - `deleteMessagesFromPoint`
   - `validateChatRateLimit`
   - 命中限制时 `saveErrorResponse`

3. `edit-resend`
   - `ChatApiController.editResendChat`
   - `ChatServiceImpl.processEditResendChat`
   - 删除被编辑消息及后续消息
   - 保存新用户消息并转移附件
   - `validateChatRateLimit`
   - 命中限制时 `saveErrorResponse`

## 问题清单

### 高风险

- `edit-resend` 原实现先做聊天频率/每日限制校验，再删除旧消息、再保存新用户内容。结果是在限制场景下，用户虽然提交了新编辑内容，但后端直接返回限制说明，数据库仍保留旧用户内容，编辑结果没有真正生效。
  - 文件证据：`src/main/java/com/instser/chat/service/serviceimpl/ChatServiceImpl.java:4416`
  - 结论类型：`Verified`

### 中风险

- 普通 `chat` 链路已经是“先保存用户消息，再返回限制说明”，因此用户输入在限制场景下会被保留；`retry` 链路也已经是“先删除旧目标消息，再返回限制说明”。三条链路此前只剩 `edit-resend` 的顺序与另外两条不一致。
  - 文件证据：`src/main/java/com/instser/chat/service/serviceimpl/ChatServiceImpl.java:119`
  - 文件证据：`src/main/java/com/instser/chat/service/serviceimpl/ChatServiceImpl.java:4165`
  - 结论类型：`Verified`

### 中风险

- 普通 `chat` 的限制响应通过 `saveAiLimitResponseToDatabase` 使用普通消息保存方法落库，状态为 `1`；而 `retry/edit-resend` 的限制响应通过 `saveErrorResponse` 落库为 `status=3`。三条链路在“错误响应状态标记”上仍存在行为差异。
  - 文件证据：`src/main/java/com/instser/chat/service/serviceimpl/ChatServiceImpl.java:548`
  - 文件证据：`src/main/java/com/instser/chat/service/serviceimpl/ChatMessageServiceImpl.java:104`
  - 结论类型：`Verified`

## 风险影响与修复优先级

- 影响面：
  - `edit-resend` 在每日限制/频率限制场景下的用户内容一致性
  - 三条链路的错误响应行为一致性
- 触发概率：中到高，用户在限制状态下编辑消息后重发即可触发
- 修复成本：低，主要为顺序调整与回归测试
- 修复优先级：P1

## 修复说明

- 已将 `ChatServiceImpl.processEditResendChat` 中的 `validateChatRateLimit` 从删除前移动到“删除旧消息 + 保存新用户消息 + 附件转移”之后执行。
- 修复后行为：
  - 用户在 `edit-resend` 时即使命中限制，旧用户消息也会被新编辑内容替换
  - 后端随后只追加一条新的限制说明
  - 与普通 `chat` 的“先保留用户新输入再返回限制”原则保持一致

## 证据与置信度

- 回归测试：
  - `src/test/java/com/instser/chat/service/serviceimpl/ChatServiceImplTest.java`
- 覆盖场景：
  - `retry` 命中每日限制时，必须先删除旧错误消息再保存新限制响应
  - `edit-resend` 命中每日限制时，必须先删除旧用户消息、保存新编辑内容，再保存新限制响应
- 验证命令：
  - `mvn -Dtest=ChatServiceImplTest test`
- 验证结果：通过
- 综合置信度：高

## 行动建议

- 24小时内：
  - 前端实际联调验证 `edit-resend` 命中限制后，会话中显示的是新编辑内容 + 最新限制说明
- 1-2周内：
  - 统一三条链路的错误响应状态标记策略，决定普通 `chat` 限制响应是否也应使用 `status=3`
- 1-2月内：
  - 抽象“限制命中后的消息持久化策略”，减少 `chat/retry/edit-resend` 分支散落导致的顺序漂移
