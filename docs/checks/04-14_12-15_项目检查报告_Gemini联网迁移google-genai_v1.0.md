# 项目检查报告

- 分析时间（北京时间）: 2026-04-14 12:15
- 文档版本: v1.0
- 检查范围: 联网搜索 Gemini 调用链、请求/响应格式、迁移到 `google-genai` 的影响面
- 检查目标: 确认当前项目是否使用 Gemini 原生格式，评估改为 Google 官方 `google-genai` Java SDK 的可行性与改造边界

## 1. 架构与模块快照

### 启动链路中的联网模块
- 聊天流程在 `ChatServiceImpl` 中通过 `processNetworkContent(...)` 进入联网分支，先把完整会话历史压成自然语言上下文，再调用 `NetworkAiServiceImpl.queryWithGeminiGrounding(...)` 获取联网结果。
- `NetworkAiServiceImpl` 只是薄封装，核心联网调用实际落在 `GeminiNetworkServiceImpl.queryWithGrounding(...)`。
- `GeminiNetworkServiceImpl` 当前没有使用 Google 官方 SDK，而是使用 `RestTemplate` 手动拼装 HTTP 请求到 `.../models/{model}:generateContent`。
- 响应回来后由 `GeminiResponseParserImpl` 手动解析 `candidates`、`groundingMetadata`、`groundingChunks`、`groundingSupports` 等字段，再回填到自定义实体 `GeminiGroundingResponse`。

### 依赖方向
- `ChatServiceImpl` -> `NetworkAiServiceImpl` -> `GeminiNetworkServiceImpl` -> `RestTemplate`
- `GeminiNetworkServiceImpl` -> `GeminiResponseParserImpl` -> `GeminiGroundingResponse/GeminiSource/GeminiTextSegment`
- 数据库存储只依赖 `GeminiSource` 转成 `WebSearchResult` 的 `title/url`，没有把 Gemini 原始响应整包入库

## 2. 关键调用链

### 调用链 1：聊天时触发联网
1. `ChatServiceImpl.processNetworkContent(...)` 构造完整上下文。
2. 通过 `((NetworkAiServiceImpl) networkAiService).queryWithGeminiGrounding(fullContext)` 发起联网。
3. 成功后调用 `formatCompleteGeminiResponse(...)`，把响应文本、搜索词、来源、引用段落拼回用户最后一条消息。
4. 同时将来源列表转为 `WebSearchResult` 用于数据库存储。

### 调用链 2：Gemini 请求构造
1. `GeminiNetworkServiceImpl` 根据数据库中模型 ID `6L` 读取 `baseUrl`、`modelCode`、`apiKey`。
2. 手工构造请求体：
   - `contents: [{ parts: [{ text: userContext }] }]`
   - `tools: [{ googleSearch: {} }]`
3. 使用 `Authorization: Bearer <apiKey>` 请求 `POST {baseUrl}/models/{model}:generateContent`

### 调用链 3：Gemini 响应解析
1. `GeminiResponseParserImpl` 读取顶层 `candidates[0]`
2. 从 `content.parts[0].text` 提取主回答文本
3. 从 `groundingMetadata.webSearchQueries` 提取搜索词
4. 从 `groundingMetadata.groundingChunks[].web` 提取标题和 URL
5. 从 `groundingMetadata.groundingSupports` 提取引用片段与来源索引

## 3. 关键发现

### 高风险

#### 1. 当前实现并不是“Gemini OpenAI 兼容格式”，而是 Gemini `generateContent` 原生 HTTP 格式
- 证据:
  - `src/main/java/com/instser/chat/service/serviceimpl/GeminiNetworkServiceImpl.java:58-79`
  - `src/main/java/com/instser/chat/service/serviceimpl/GeminiNetworkServiceImpl.java:110-129`
- 结论: `Verified`
- 说明:
  - 请求体使用 `contents + tools.googleSearch`
  - URL 使用 `:generateContent`
  - 这和 OpenAI 兼容层的 `chat.completions` 完全不是同一套格式

#### 2. 项目当前没有引入 `google-genai` Java SDK，直接迁移会涉及 Maven 依赖变更
- 证据:
  - `pom.xml` 当前不存在 `com.google.genai` 相关依赖
- 结论: `Verified`
- 影响:
  - 根据仓库规范，新增依赖前必须先得到用户确认
  - 本次仅完成检查和迁移评估，未直接改代码

### 中风险

#### 3. 当前响应解析强依赖手写 JSON 路径，SDK 迁移后解析层可大幅收敛
- 证据:
  - `src/main/java/com/instser/chat/service/serviceimpl/GeminiResponseParserImpl.java:30-225`
- 结论: `Verified`
- 说明:
  - 现有解析器绑定了 `candidates`、`groundingMetadata`、`groundingChunks`、`groundingSupports`
  - 改为 `google-genai` 后，可以优先从 SDK 类型对象读取，再只保留少量适配层

#### 4. 业务真正依赖的不是 Gemini 原始 JSON，而是“结构化引用结果 + 文本拼接结果”
- 证据:
  - `src/main/java/com/instser/chat/service/serviceimpl/ChatServiceImpl.java:3880-3905`
  - `src/main/java/com/instser/chat/service/serviceimpl/ChatServiceImpl.java:4922-4988`
  - `src/main/java/com/instser/chat/service/serviceimpl/NetworkAiServiceImpl.java:52-60`
- 结论: `Verified`
- 说明:
  - 这意味着迁移重点是适配 `responseText/searchQueries/sources/textSegments`
  - 数据库存储层基本可以保持不动

### 低风险

#### 5. 当前联网模型配置来自数据库，不是固定写死在 `application.yml`
- 证据:
  - `src/main/java/com/instser/chat/service/serviceimpl/GeminiNetworkServiceImpl.java:48-50`
  - `src/main/resources/application.yml` 中虽保留 `gemini.api` 配置，但联网实现实际读取的是 `AiModel`
- 结论: `Verified`
- 说明:
  - 迁移到 SDK 时，仍建议保留数据库中的 `modelCode/baseUrl/apiKey`
  - 但 `baseUrl` 是否继续可配，取决于你是接 Google 官方域名还是继续走自定义中转

## 4. 当前请求格式结论

### 当前项目实际发送的 Gemini 联网请求格式
```json
{
  "contents": [
    {
      "parts": [
        {
          "text": "完整会话上下文"
        }
      ]
    }
  ],
  "tools": [
    {
      "googleSearch": {}
    }
  ]
}
```

### 当前项目不是以下格式
- 不是 OpenAI 兼容层 `chat.completions`
- 不是你之前示例里的 `messages` 数组格式
- 不是 `reasoning_effort` 那套 OpenAI 兼容参数

## 5. 迁移到 `google-genai` 的建议方案

### 推荐方案
- 保留现有业务边界:
  - `ChatServiceImpl` 不动
  - `NetworkAiServiceImpl` 接口尽量不动
  - 重点替换 `GeminiNetworkServiceImpl`
- 新实现改为:
  - 用 `google-genai` 官方 Java SDK 创建 client
  - 用 `generateContent` + `google_search` 工具发请求
  - 将 SDK 返回对象映射回现有 `GeminiGroundingResponse`
- 这样可以最小化影响面，数据库结构、上层拼接逻辑和现有联网内容存储逻辑基本不用动

### 不建议的方案
- 直接把整个联网链路改成 OpenAI 兼容层 `chat.completions`
- 原因:
  - 你现在的代码已经围绕 grounding metadata 做了完整结构化处理
  - 回退到兼容层会让引用、搜索词、来源段落这类字段更难稳定获取

## 6. 与官方资料的对应关系

### 官方结论
- Google 官方当前推荐 Gemini API 使用 Google GenAI SDK，而不是自行手写 HTTP 层。
- Grounding with Google Search 在当前模型上推荐使用 `google_search` 工具。
- Java 已有官方 SDK，可直接用于 `generateContent` 调用。

### 参考资料
- Google 官方库说明: https://ai.google.dev/gemini-api/docs/libraries
- Grounding with Google Search: https://ai.google.dev/gemini-api/docs/google-search
- Gemini API 参考: https://ai.google.dev/api
- 官方 Java SDK 仓库: https://github.com/googleapis/java-genai

## 7. 风险影响与修复优先级

### 影响面
- 低影响:
  - `ChatServiceImpl` 上层聊天逻辑
  - 数据库存储 `WebSearchResult`
- 中影响:
  - `NetworkAiServiceImpl` 到 `GeminiNetworkServiceImpl` 的适配层
- 高影响:
  - `GeminiResponseParserImpl` 可能被完全替换或大幅瘦身

### 触发概率
- 依赖变更导致编译或 API 类型差异: 中
- 如果继续沿用中转 `baseUrl`，SDK 是否支持自定义 endpoint: 需要实测确认
- 若改为直连 Google 官方域名，配置与鉴权方式会发生变化: 高

### 修复成本
- 只替换调用层并保留现有实体映射: 中
- 连同配置模型、异常处理、测试一起补齐: 中高

## 8. 行动建议

### 24小时内
1. 先确认是否允许我新增 Maven 依赖 `com.google.genai`。
2. 确认你准备直连 Google 官方 Gemini API，还是继续走现有 `baseUrl` 中转。
3. 确认联网模型 ID `6L` 是否仍继续作为联网专用模型配置。

### 1-2周内
1. 落地 `google-genai` 版本的 `GeminiNetworkServiceImpl`
2. 用官方 SDK 响应对象替代手写 JSON 解析的主路径
3. 补一组最小回归测试，覆盖:
   - 有来源
   - 无来源
   - 多 citation
   - API 异常降级

### 1-2月内
1. 统一普通 Gemini 文本调用与联网 Gemini 调用的 SDK 接入方式
2. 清理项目内所有历史“Gemini 特殊解析/兼容层”遗留逻辑
3. 为联网响应建立单独的适配器层，避免业务层再出现 SDK 或 JSON 细节

## 9. 结论

- 你现在的联网 AI 调用使用的是 Gemini 原生 `generateContent` 格式，不是 OpenAI 兼容格式。
- 从现有代码结构看，迁移到 Google 官方 `google-genai` Java SDK 是可行的，而且是比较合适的方向。
- 真正需要改的核心只有“联网调用层 + 响应映射层”，上层聊天拼接和数据库存储层可以基本保持不变。
- 但因为这一步涉及新增依赖，按项目规范必须先得到你的确认，我这次没有直接实施代码迁移。
