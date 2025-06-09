# 金语聊天系统 (Jinyu Chat)

一个基于 Spring Boot 3.1 的现代化 AI 聊天系统后端服务，支持多种 AI 模型，提供完整的用户认证、会话管理和流式对话功能。

## 🌟 项目特色

- 🚀 **现代化架构**: Spring Boot 3.1 + MyBatis-Plus + Druid
- 🤖 **多AI模型支持**: 集成 GPT、Claude、通义千问等主流AI模型
- 💬 **流式响应**: 基于 WebFlux 的 SSE 流式对话体验
- 🔐 **完整用户系统**: 用户注册、登录、权限管理
- 📊 **数据库优化**: 雪花ID、逻辑删除、自动时间戳
- 🎯 **RESTful API**: 统一的响应格式和错误处理

## 🛠️ 技术栈

- **Spring Boot 3.1.12** - 基础框架
- **MyBatis-Plus 3.5.7** - ORM框架，简化数据库操作
- **Druid 1.2.23** - 高性能数据库连接池
- **Spring WebFlux** - 响应式Web框架（用于流式响应）
- **Spring Security Crypto** - 密码加密
- **MySQL 8.0** - 关系型数据库
- **JDK 17** - Java运行环境
- **Lombok** - 简化代码编写

## 🏗️ 项目结构

```
src/main/java/com/instser/chat/
├── ChatApplication.java           # 主启动类
├── common/                        # 公共模块
│   ├── exception/                 # 异常处理
│   │   ├── BusinessException.java # 业务异常
│   │   └── GlobalExceptionHandler.java # 全局异常处理器
│   └── result/                    # 统一返回结果
│       ├── Result.java            # 统一返回结果类
│       └── ResultCode.java        # 结果状态码枚举
├── config/                        # 配置类
│   ├── DataInitializer.java       # 数据初始化
│   ├── MetaObjectHandler.java     # MyBatis字段自动填充
│   ├── MybatisPlusConfig.java     # MyBatis-Plus配置
│   └── WebConfig.java             # Web配置（跨域等）
├── controller/                    # 控制器层
│   ├── ApiController.java         # 主API控制器
│   ├── AiSdkController.java       # AI SDK兼容控制器
│   ├── ChatApiController.java     # 聊天API控制器
│   └── TestController.java        # 测试控制器
├── dao/                          # 数据访问层
│   ├── AiModelDao.java            # AI模型DAO
│   ├── ChatMessageDao.java        # 聊天消息DAO
│   ├── ChatSessionDao.java        # 聊天会话DAO
│   └── UserDao.java               # 用户DAO
├── entity/                       # 实体类
│   ├── AiModel.java              # AI模型实体
│   ├── ChatMessage.java          # 聊天消息实体
│   ├── ChatSession.java          # 聊天会话实体
│   └── User.java                 # 用户实体
├── service/                      # 服务层
│   ├── AiModelService.java       # AI模型服务接口
│   ├── ChatMessageService.java   # 聊天消息服务接口
│   ├── ChatSessionService.java   # 聊天会话服务接口
│   ├── UserService.java          # 用户服务接口
│   └── serviceimpl/              # 服务实现
├── util/                         # 工具类
└── vo/                          # 视图对象
    ├── AiModelVO.java            # AI模型VO
    ├── ChatMessageVO.java        # 聊天消息VO
    ├── ChatSessionVO.java        # 聊天会话VO
    ├── UserVO.java               # 用户VO
    └── converter/                # VO转换器
        └── VOConverter.java      # 统一转换工具
```

## 🚀 快速开始

### 环境要求

- **JDK 17+** (必须，项目严格依赖JDK 17)
- **MySQL 8.0+**
- **Maven 3.6+**

### 1. 数据库初始化

```bash
# 创建数据库并导入初始数据
mysql -u root -p < sql/jinyu_chat_database.sql
```

### 2. 配置修改

修改 `src/main/resources/application.yml` 中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/chat?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
    username: your_username
    password: your_password
```

### 3. 启动项目

```bash
# 方式1: Maven直接运行
mvn spring-boot:run

# 方式2: 打包后运行
mvn clean package -DskipTests
java -jar target/chat-0.0.1-SNAPSHOT.jar

# 方式3: IDE中运行
# 直接运行 ChatApplication.java 主类
```

### 4. 验证启动

项目启动后，访问以下接口进行测试：

- **健康检查**: `GET http://localhost:8080/api/test/health`
- **测试接口**: `GET http://localhost:8080/api/test/hello`
- **Druid监控**: `http://localhost:8080/druid/` (admin/123456)

## 📡 核心API接口

### 认证相关
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/register` - 用户注册
- `GET /api/auth/me` - 获取当前用户信息
- `POST /api/auth/logout` - 用户登出

### 聊天会话
- `GET /api/chats` - 获取聊天列表
- `POST /api/chats` - 创建新聊天
- `GET /api/chats/{id}` - 获取聊天详情
- `PUT /api/chats/{id}` - 更新聊天信息
- `DELETE /api/chats/{id}` - 删除聊天

### 消息管理
- `GET /api/chats/{chatId}/messages` - 获取聊天消息
- `POST /api/chats/{chatId}/messages` - 发送消息
- `POST /api/ai-sdk/chat` - AI流式对话接口

### AI模型
- `GET /api/models` - 获取可用AI模型列表
- `GET /api/models/{id}` - 获取指定模型信息

## 🗄️ 数据库设计

### 核心表结构

#### t_user (用户表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 用户ID (雪花ID) |
| username | varchar(50) | 用户名 |
| email | varchar(100) | 邮箱 |
| password | varchar(255) | 加密密码 |
| role | varchar(20) | 角色 (user/admin) |
| status | tinyint | 状态 (0-禁用，1-启用) |

#### t_ai_model (AI模型表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 模型ID |
| model_code | varchar(50) | 模型代码 |
| model_name | varchar(100) | 模型名称 |
| provider | varchar(50) | 提供商 |
| token_limit | int | Token上限 |
| is_available | tinyint | 是否可用 |

#### t_chat_session (聊天会话表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键ID |
| session_id | varchar(64) | 会话UUID |
| title | varchar(255) | 会话标题 |
| user_id | bigint | 用户ID |
| model_id | bigint | AI模型ID |
| token_count | int | 累计Token数 |

#### t_chat_message (聊天消息表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键ID |
| message_id | varchar(64) | 消息UUID |
| session_id | varchar(64) | 会话ID |
| role | varchar(20) | 消息角色 |
| content | longtext | 消息内容 |
| token_count | int | Token数量 |

## 🎯 核心功能

### 1. 用户认证系统
- 用户注册/登录功能
- BCrypt密码加密存储
- 用户权限验证
- 自动初始化默认管理员账户

### 2. AI模型管理
- 支持多种AI模型 (GPT、Claude、通义千问等)
- 模型可用性控制
- 自动初始化默认模型数据
- 模型信息动态配置

### 3. 聊天会话管理
- 创建/删除聊天会话
- 会话标题管理
- 会话权限验证
- 会话历史记录

### 4. 消息处理系统
- 消息发送/接收
- 流式响应支持
- Token使用统计
- 消息状态管理

### 5. 数据安全保障
- 雪花ID生成策略
- 逻辑删除机制
- 自动时间戳填充
- 完整的外键约束

## 🔧 配置说明

### 应用配置 (application.yml)
```yaml
spring:
  datasource:
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20
      max-wait: 60000
      
server:
  port: 8080
  
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

### 数据初始化
系统启动时会自动初始化：
- 默认管理员账户: admin@example.com / admin123
- 预置AI模型数据
- 必要的数据库字段

## 🔍 技术亮点

### JavaScript大整数精度问题解决
使用 `@JsonSerialize(using = ToStringSerializer.class)` 注解，确保Long类型ID在前端显示为字符串，避免精度丢失。

### 流式响应实现
基于Spring WebFlux的 `Flux<String>` 实现SSE流式响应，提供实时AI对话体验。

### VO模式设计
通过VO类和转换器实现前后端数据格式统一，保护内部实体结构。

### 统一异常处理
全局异常处理器提供统一的错误响应格式，提升API使用体验。

## 🚧 开发规范

1. **代码风格**: 遵循阿里巴巴Java开发规范
2. **API设计**: RESTful风格，统一Result响应格式
3. **数据库**: 下划线命名，逻辑删除，自动时间戳
4. **异常处理**: 业务异常使用BusinessException
5. **日志记录**: 使用SLF4J进行结构化日志

## 📈 监控和运维

- **Druid监控**: http://localhost:8080/druid/ (admin/123456)
- **健康检查**: /api/test/health
- **日志文件**: logs/jinyu-chat.log
- **JVM监控**: 支持JMX监控

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支
3. 提交代码更改
4. 创建 Pull Request

## 📄 许可证

本项目采用 MIT 许可证

---

⭐ 如果这个项目对你有帮助，请给它一个星标！
