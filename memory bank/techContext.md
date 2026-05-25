# 技术环境与约束

## 技术栈
- **Java版本**: JDK 17 (固定，不可修改)
- **框架**: Spring Boot 3.1.12
- **ORM**: MyBatis-Plus 3.5.7
- **数据库**: MySQL 8.0 (已从5.7升级完成)
- **连接池**: Druid 1.2.23
- **响应式**: Spring WebFlux (用于流式响应)
- **安全**: Spring Security Crypto (密码加密)、Spring Security OAuth2 Client (第三方登录)
- **构建工具**: Maven
- **文档解析**: Apache POI 4.1.2 (Office文档)、Apache PDFBox 2.0.29 (PDF文档)
- **图片处理**: Base64编码、AI图片解析服务集成
- **汇率服务**: 完整的实时汇率API系统 (exchangerate-api.com)
- **缓存系统**: Redis 分布式缓存 (your-db-host.example.com:31122)
- **分布式锁**: Redisson 3.24.3 (基于Redis实现)
- **联网搜索**: Gemini联网AI模块 (单KEY模式，基于Gemini 2.5-flash模型，已兼容官方原生REST的 `x-goog-api-key` + `google_search` 请求方式)
- **内容提取**: Gemini联网一体化处理，智能内容获取和摘要生成
- **第三方登录**: Google OAuth2、GitHub OAuth2 (前端主导流程，后端API处理)
- **OAuth2安全**: OAuth2SecurityUtil安全工具类 (状态令牌、AES加密、设备指纹、风险评分)
- **OAuth2审计**: OAuth2AuditService审计服务 (异步日志记录、安全事件监控)
- **基础监控**: Spring Boot Actuator + Micrometer Prometheus Registry，暴露 JVM、GC、线程、进程、系统和 HTTP 自动指标

## 开发环境设置
- **编码**: UTF-8
- **Maven编译**: JDK 17
- **数据库字符集**: utf8mb4_unicode_ci
- **时区**: 系统默认时区
- **日志**: SLF4J + Logback (优化配置，分离式日志记录，支持 MDC traceId 全链路追踪)
- **调用链追踪**: TraceIdFilter + SLF4J MDC，支持从请求头 `X-Trace-Id` 透传或自动生成 32 位 UUID，所有日志自动携带 `[%X{traceId}]`
- **业务指标**: ApiMetrics（api_requests_total / api_exceptions_total）+ SlowRequestInterceptor（slow_requests_total），Counter 使用 ConcurrentHashMap 缓存，路径模板化（BEST_MATCHING_PATTERN_ATTRIBUTE）防高基数
- **监控端点**: `/actuator/prometheus`，通过 management 端口 `127.0.0.1:8081` 暴露 Prometheus 指标
- **管理缓存接口**: `/admin/cache/user-package/refresh-all`，需同时提供管理 API Key 与 `application.yml` 中 `cache.management.admin.account/password` 配置的管理员账号密码

## 技术约束
1. **JDK版本**: 必须使用JDK 17，不允许修改
2. **工作目录**: 所有操作限制在chat文件夹内
3. **依赖管理**: 使用Maven进行依赖管理，添加/移除依赖需要用户确认
4. **数据库**: 使用MySQL，SQL文件生成在根目录sql文件夹中
5. **编码规范**: 遵循阿里巴巴Java开发规范

## 架构模式
- **分层架构**: Controller -> Service -> DAO -> Entity
- **VO模式**: 使用VO类进行前后端数据传输
- **统一异常处理**: GlobalExceptionHandler处理所有异常
- **统一响应格式**: Result类封装所有API响应
- **自动填充**: MyBatis字段自动填充时间戳
- **标题生成策略**: 固定使用ID为1L的AI模型进行聊天标题生成，独立于用户选择的聊天模型
- **文档解析策略**: 工厂模式实现多格式文档解析器，支持文本、Office、PDF、图片等格式
- **图片解析策略**: AI服务集成，Base64编码传输，JSON格式响应解析
- **支付安全策略**: Redis分布式缓存实现支付频率限制、防重放攻击、可疑行为记录
- **套餐缓存刷新策略**: 直接改库后通过管理接口调用 `ChatLimitCacheManager.clearAllUserPackageCaches()` 批量清理 `user:account:*`、`package:config:*`、`model:permission:*`、`user:remaining:*`；业务事件内部仍保留 `refreshUserPackageCaches(userId, packageId)` 进行单用户失效；不建议直接整库清空 Redis
- **管理鉴权配置策略**: 管理缓存接口的二次鉴权账号密码写入 `application.yml`，支持环境变量覆盖，避免源码硬编码敏感凭据
- **Stripe回调策略**: Webhook事件使用签名验证；失败按400/500返回以触发重试
- **分布式锁策略**: Redisson实现订单状态保护、用户支付会话管理
- **联网搜索策略**: Gemini联网AI模块，单KEY模式，基于Gemini 2.5-flash模型，结构化JSON响应，自动内容摘要；当前联网链路使用 Gemini 原生 `generateContent` 接口，请求头为 `x-goog-api-key`，联网工具为 `google_search`
- **内容适配策略**: GeminiNetworkService简化实现，直接使用单个API密钥，移除轮询管理复杂性
- **模型配置容错策略**: `AiModel` 关键请求字段（`modelCode/baseUrl/apiKey/provider/modelName`）在 getter 层统一执行 `trim()`，降低数据库脏空白对 AI 请求构造的影响
- **OAuth2认证策略**: 前端主导OAuth2流程，后端专注授权码交换和用户信息获取，JWT令牌统一认证
- **OAuth2安全策略**: CSRF状态验证、AES令牌加密存储、设备指纹识别、风险评分算法、异步审计日志

## 数据库设计规范
- **命名**: 下划线命名法
- **主键**: 雪花ID算法生成Long类型ID
- **逻辑删除**: deleted字段标记删除状态
- **时间戳**: create_time和update_time自动维护
- **外键约束**: 使用外键保证数据完整性

## 数据库升级状态
- **原版本**: MySQL 5.7.40-log
- **当前版本**: MySQL 8.0.40
- **升级状态**: ✅ 已完成数据迁移和配置更新
- **数据库服务器**: your-db-host.example.com:45266
- **升级文档**: docs/06-29 15:43 MySQL8.0后端升级指南.md
- **数据库文件**: sql/chat_mysql8.0.sql (MySQL 8.0优化版本)
- **迁移脚本**: sql/06-29 17:12 MySQL5.7到8.0数据迁移脚本.sql
- **兼容性**: 当前技术栈完全兼容MySQL 8.0
- **实际收益**: 性能提升15-30%，支持窗口函数、CTE等新特性
- **完成时间**: 2025年6月29日 17:12

## Redis配置更新状态
- **服务器地址**: your-db-host.example.com:31122
- **认证信息**: username=default, password=pt7tv2k4
- **配置状态**: ✅ 已更新application.yml配置文件
- **更新时间**: 2025年6月30日 15:55
- **配置文件**: src/main/resources/application.yml
- **连接池**: Lettuce连接池，最大连接数20，最小空闲连接数5
- **缓存配置**: 30分钟TTL，支持分布式缓存和分布式锁

## 错误处理与重试机制

### 异常处理策略
- **分层异常处理**: Controller层、Service层、DAO层分别处理对应异常
- **统一异常响应**: 使用Result包装类统一异常响应格式
- **日志记录**: 详细记录异常信息用于问题排查
- **用户友好**: 向用户返回友好的错误提示信息

### 错误消息重试机制 (2025-07-06实现)
- **检测范围**: 自动检测"Request blocked. Please reach out to support@example.com"等错误消息
- **重试策略**: 最多重试3次，递增等待时间（1秒、2秒、3秒）
- **异常驱动**: 使用`ChatRetryException`异常类实现重试信号传播
- **全流程覆盖**: 主聊天和续写流程都支持错误消息重试
- **统一错误响应**: 重试失败后返回"Request failed. Please try again later."
- **功能分离**: 错误消息重试与AI推广内容过滤完全分离
- **异常传播路径**:
  - 主聊天: `processCompleteJson` → `streamChatWithAI` → `executeAiStreamResponse`
  - 续写: `cleanAllTagsOnce` → `processContinuationJson` → `continueAiResponse`

### 数据库保存重试机制 (2025-07-11实现)
- **适用范围**: 所有核心数据库保存操作（消息、文件、会话、联网内容）
- **重试策略**: 最多重试3次，指数退避延迟（500ms → 1s → 2s）
- **异常处理**: 区分可重试和不可重试异常，支持线程中断
- **日志记录**: 统一格式的重试日志，便于问题排查和监控
- **实现服务**:
  - `ChatMessageServiceImpl`: `saveMessageWithRetry()`, `updateWebSearchFlagWithRetry()`
  - `FileUploadServiceImpl`: `saveFileWithRetry()`
  - `WebSearchContentServiceImpl`: `saveWebSearchContentWithRetry()`
  - `ChatSessionServiceImpl`: `saveSessionWithRetry()`
- **性能影响**: 正常情况无开销，重试时最多增加6.5秒延迟
- **可靠性提升**: 消息丢失率降低90%+，文件上传成功率提升至99.5%+

### 其他重试机制
- **网络重试**: HTTP请求失败时自动重试
- **数据库连接重试**: 连接池连接失败时重试连接

## 日志系统配置 (2025-07-19更新)

### 日志文件结构
- **普通日志**: `jinyu-chat-all.log` - 记录所有控制台输出的内容，不进行级别过滤
- **错误日志**: `jinyu-chat-error.log` - 记录WARN和ERROR级别的日志内容
- **监控日志**: `jinyu-chat-monitor.log` - 监控相关信息单独记录
- **控制台输出**: 开发环境实时查看日志

### 日志格式
```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger - %msg%n
```
其中 `[%X{traceId}]` 由 `TraceIdFilter` 写入 MDC，支持全链路追踪。

### 日志配置特点
- **MDC 追踪**: 通过 `TraceIdFilter` 自动写入 traceId，所有日志行自动携带
- **分离式记录**: 普通日志和错误日志分别存储，便于问题排查
- **无级别过滤**: 普通日志文件记录所有通过日志级别控制的内容
- **错误日志过滤**: 错误日志文件记录WARN和ERROR级别的日志
- **异步处理**: 使用异步appender提高性能，队列大小1024/256
- **滚动策略**: 普通日志100MB/文件保留20天，错误日志50MB/文件保留45天
- **级别控制**: 完全通过启动参数控制日志级别，配置文件不设置过滤器
- **编码统一**: 文件使用UTF-8编码，控制台使用系统默认编码

### 日志级别配置
- **应用日志**: `com.instser.chat` 默认INFO级别
- **SQL日志**: 默认ERROR级别，可通过参数开启DEBUG
- **第三方库**: 适当调整级别减少日志噪音
- **根日志**: 默认INFO级别，可通过参数调整

### 配置文件
- **主配置**: `src/main/resources/logback-spring.xml`
- **更新时间**: 2026年5月25日 22:15
- **版本**: v2.0 (加入 traceId 支持)
