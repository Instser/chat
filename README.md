# Jinyu Chat

一个独立开发并持续运营的 AI 聊天服务后端，2025 年 6 月起迭代至今，运行在生产环境，服务真实用户、接真实支付。本仓库包含后端与管理后台前端（admin-web），用户聊天前端为独立仓库。

## 功能亮点

### 对话核心

- 多 AI 模型并存，按模型动态路由，支持任何 OpenAI 兼容接口的上游（DeepSeek、通义、Kimi 等）
- SSE 流式输出，支持用户主动中断、编辑重发、失败重试（固定间隔多次，流式中途断流不再重试以避免重复内容）
- 推理模型的思考过程（reasoning）与正文走并行通道分别落库，前端可展开查看
- 会话内 skill 按需注入：关键词 + 状态机判定，只在相关对话时把对应 skill 提示词挂进 system，控制 token 开销

### API 密钥池

同一模型可配置多把 API key（t_ai_model_key 表），按权重行内轮询选取；调用失败自动降权、恢复后自动回权。上游单 key 限流或被封时服务不中断，也便于在多平台间分摊额度。

### 提示词泄露防护

防止用户诱导模型把系统提示词整段复述出来（PromptLeakGuard）：

- 思维链与正文双通道扫描，累计缓冲 + 单帧增量双扫，分片泄露也拦得住
- 锚点在启动时从当前提示词自动派生——提示词内容变更后防护自动跟随，不会出现改了提示词防护就静默失效的情况
- 命中后思维链清空落库缓冲、正文截断并整体替换为兜底文案，同时暴露 `prompt_leak_detections_total` 指标便于确认防护真实在生效
- 核心判定逻辑有完整测试覆盖（41 个用例，含逐字边界实测定档）

### 支付与套餐

- 支付宝、易支付、Stripe、PayPal 四个渠道，策略模式接入，回调验签 + Redis 防重放
- 订单完整生命周期：创建、支付回调、自动过期、幂等防超卖（分布式锁）
- 套餐绑定模型权限与对话额度，支持优惠价；USD/CNY 双货币按实时汇率换算

### 账号与登录

- 常规注册登录（JWT）之外，支持 Google / GitHub OAuth2 一键登录
- 游客一键试用：免注册直接聊，每账号 10 次额度（AI 成功回复计 1 次）
- 前后台会话隔离：管理员与普通用户使用两套独立 JWT 密钥和 Cookie，同一浏览器双身份并存互不顶号

### 管理后台与可观测

- admin-web 管理后台（本仓库内）：AI 调用监控（TTFT、token 消耗、按 1d/7d/30d 聚合）、日志在线查看、模型与密钥管理、缓存管理
- traceId 全链路贯穿：一次请求从入口到上游调用的所有日志可串起来排查
- 慢接口拦截器 + API 指标（请求数、异常数、慢请求计数），Prometheus 指标暴露
- 8 个定时任务负责缓存清理、过期订单回收、健康巡检等日常维护

### 文档解析

上传 Word / Excel / PPT / PDF 即解析入库随对话引用；图片走 VL 视觉模型生成内容描述。上传前做文件魔数校验，不轻信扩展名。

## 技术栈

- Java 17（固定）+ Spring Boot 3.1，分层架构，MyBatis-Plus + Druid
- MySQL 8.0，Flyway 做 schema 版本控制（baseline 模式，空库启动自动建表）
- Redis（缓存、分布式锁 Redisson、会话状态）+ Caffeine 本地缓存，构成 L1+L2+L3 三级缓存
- 管理后台前端为独立 SPA（admin-web 目录）

## 快速开始

在线体验：访问 [chat.instser.cn](https://chat.instser.cn)，点击"游客试用"即可免注册开始对话（每位访客 10 次对话额度）。

## 本地运行

环境要求：JDK 17+、Maven 3.6+、MySQL 8.0+、Redis 6.0+。

```bash
git clone <repo> && cd chat
```

1. MySQL 建一个空库 `chat`（utf8mb4），不用导表——首次启动 Flyway 自动建全部表
2. 准备一个本地 Redis
3. 编辑 `src/main/resources/application.yml` 填数据库与 Redis 连接（所有敏感项支持同名环境变量覆盖）

配置分级：数据库和 Redis 是必需的，缺了起不来；AI 模型 Key、支付商户凭证、OAuth2 凭证都可以先空着，服务照常启动，只是对应功能不可用。

```bash
mvn spring-boot:run
```

以 dev profile 启动时（默认），系统会自动创建默认管理员 `admin@example.com / admin123`（登录后请立即改掉），并初始化模型元数据和套餐。

启动后登录管理后台 `/admin`，在模型管理里给任一模型填入 API Key 即可开始对话。

验证入口：

- 健康检查 `http://localhost:8080/actuator/health`
- 管理后台 `http://localhost:8080/admin`
- Prometheus 指标 `http://localhost:8081/api/actuator/prometheus`

## 仓库导览

- `src/` — 后端源码（controller / service / dao / config 常规分层）
- `admin-web/` — 管理后台前端
- `src/main/resources/db/migration/` — Flyway 迁移脚本
- `sql/` — 历次手工执行的增量 SQL（生产变更记录，新环境无需执行）
- `docs/` — 540+ 篇开发过程文档：方案设计、问题排查、上线验证记录，按日期命名
- `memory bank/` — 项目上下文记忆库（产品定位、架构决策、进度状态），AI 协作开发的工作底稿
- `README-OPS.md` — 运维手册：部署架构、端口、监控指标、日志体系、定时任务明细

## 边界说明

这是一个个人项目，不是框架或库。代码里的配置默认值都是占位符，支付、OAuth2 等能力需要你自己申请对应平台的商户/应用凭证才能跑通。如果你在评估这份代码，建议从 `docs/` 里的方案文档和 `memory bank/` 的决策记录看起——比起最终代码，那些东西更能说明问题是怎么被想清楚和解决的。
