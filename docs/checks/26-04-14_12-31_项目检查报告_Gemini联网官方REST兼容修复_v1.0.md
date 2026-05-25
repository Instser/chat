# 项目检查报告

- 分析时间（北京时间）: 2026-04-14 12:31
- 文档版本: v1.0
- 检查范围: Gemini 联网模块切换官方原生 REST 的兼容性修复
- 检查目标: 在数据库 `baseUrl` 改为 `https://generativelanguage.googleapis.com/v1beta` 后，修复联网模块剩余不兼容点，并确认是否还存在其他阻塞项

## 1. 架构与模块快照

### 本次改动模块
- `GeminiNetworkServiceImpl`
  - 负责联网搜索请求的构造、鉴权、发送和响应解析入口
- `WebClientConfig`
  - 负责 `RestTemplate` Bean 配置
- `GeminiNetworkServiceImplTest`
  - 负责验证官方 REST 兼容性

### 依赖方向
- `ChatServiceImpl` -> `NetworkAiServiceImpl` -> `GeminiNetworkServiceImpl`
- `GeminiNetworkServiceImpl` -> `RestTemplate` + `AiModelService` + `GeminiResponseParser`

## 2. 关键调用链

### 调用链 1：联网请求发送
1. 从数据库读取联网模型 `baseUrl/modelCode/apiKey`
2. 构造请求 URL：`{baseUrl}/models/{modelCode}:generateContent`
3. 请求头使用 `x-goog-api-key`
4. 请求体使用 `contents + tools[google_search]`
5. 响应交给 `GeminiResponseParserImpl` 解析

### 调用链 2：测试验证
1. 单测构造官方地址 `https://generativelanguage.googleapis.com/v1beta`
2. 断言请求头必须存在 `x-goog-api-key`
3. 断言请求头不得再带 `Authorization`
4. 断言工具名必须为 `google_search`
5. 当前代码先红后绿，证明修复点有效

## 3. 关键发现

### 高风险

#### 1. 官方原生 REST 下原来的 Bearer 鉴权不兼容，已修复
- 证据:
  - 修复位置: `src/main/java/com/instser/chat/service/serviceimpl/GeminiNetworkServiceImpl.java`
  - 验证用例: `src/test/java/com/instser/chat/service/serviceimpl/GeminiNetworkServiceImplTest.java`
- 结论: `Verified`
- 说明:
  - 原实现使用 `Authorization: Bearer`
  - 现已改为 `x-goog-api-key`

#### 2. 官方 Google Search 工具名与原实现不一致，已修复
- 证据:
  - 修复位置: `src/main/java/com/instser/chat/service/serviceimpl/GeminiNetworkServiceImpl.java`
  - 验证用例: `src/test/java/com/instser/chat/service/serviceimpl/GeminiNetworkServiceImplTest.java`
- 结论: `Verified`
- 说明:
  - 原实现发送 `googleSearch`
  - 现已改为 `google_search`

### 中风险

#### 3. `geminiRestTemplate` Bean 在源码中缺失，已补齐
- 证据:
  - 修复位置: `src/main/java/com/instser/chat/config/WebClientConfig.java`
- 结论: `Verified`
- 说明:
  - `GeminiNetworkServiceImpl` 原先声明了 `@Qualifier("geminiRestTemplate")`
  - 但仓库里没有对应 Bean，存在运行期注入风险
  - 本次新增同名 Bean，避免隐藏故障

#### 4. 数据库中的 `model_code` 仍必须是 Google 官方支持且支持联网工具的模型
- 证据:
  - 代码构造 URL 直接使用数据库中的 `modelCode`
- 结论: `Verified`
- 说明:
  - 如果你库里填的是代理专用模型名或旧别名，官方接口仍会报错
  - 这不是代码兼容问题，而是配置前提

### 低风险

#### 5. 响应解析层仍依赖 `groundingMetadata` 结构
- 证据:
  - `src/main/java/com/instser/chat/service/serviceimpl/GeminiResponseParserImpl.java`
- 结论: `Verified`
- 说明:
  - 如果官方返回结构中没有 grounding 元数据，请求仍可能成功，但来源列表会为空
  - 这不会导致联网调用直接失败，但会影响搜索来源展示和数据库落库

## 4. 剩余兼容性判断

### 联网模块当前还需要满足的前置条件
1. 数据库中的联网模型 `baseUrl` 必须为:
   - `https://generativelanguage.googleapis.com/v1beta`
2. `api_key` 必须是 Google Gemini 官方可用 Key
3. `model_code` 必须是官方支持且支持 Google Search 工具的模型

### 仍然可能报错但不属于本次代码问题的情况
1. API Key 无效或额度不足
2. 该模型不支持 Google Search 工具
3. 数据库里模型名仍是代理专用命名
4. 账号侧未开通对应 Gemini API 能力

### 与本次联网修复无关、但仓库中仍存在的其他 Gemini 兼容风险
1. 普通聊天主链路仍是 OpenAI 兼容写法，不是官方原生 Gemini REST
2. `ImageDocumentParser` 若未来改指向官方原生 Gemini 地址，也会因为仍使用 Bearer + `chat.completions` 格式而不兼容
3. `TruncationDetectionServiceImpl` 等普通模型调用逻辑同样基于 OpenAI 兼容风格

## 5. 风险影响与修复优先级

### 影响面
- 低: 联网结果存储、聊天拼接逻辑
- 中: 联网模型配置正确性
- 高: 错把普通聊天模型地址也切到官方原生 Gemini 地址

### 触发概率
- 数据库模型名不兼容: 中高
- Key 无效/配额问题: 中
- 联网响应无 grounding metadata: 中

### 修复成本
- 本次已完成的联网 REST 兼容修复: 低
- 普通聊天全链路迁移到官方原生 Gemini: 高

## 6. 行动建议

### 24小时内
1. 将联网模型的 `baseUrl` 改为 `https://generativelanguage.googleapis.com/v1beta`
2. 确认联网模型 `model_code` 使用官方有效模型名
3. 用真实请求验证一次是否能返回 `sources`

### 1-2周内
1. 给联网模块补一个集成测试或沙箱验证脚本
2. 为官方 API 报错增加更明确的日志，例如区分 400/401/403/429

### 1-2月内
1. 评估普通聊天与图片解析是否也需要统一迁移到官方 Gemini SDK 或官方原生 REST
2. 收敛项目内 Gemini 相关的多种调用风格，避免后续配置混淆

## 7. 验证结果

- 单测命令:
  - `mvn -Dtest=GeminiNetworkServiceImplTest test`
- 编译命令:
  - `mvn -DskipTests compile`
- 验证结论:
  - 联网模块针对官方原生 REST 的关键不兼容项已修复
  - 当前代码可以配合你即将修改的联网模型 `baseUrl` 使用
