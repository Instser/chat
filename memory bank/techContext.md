# 技术环境与约束

## 技术栈
- **Java版本**: JDK 17 (固定，不可修改)
- **框架**: Spring Boot 3.1.12
- **ORM**: MyBatis-Plus 3.5.7
- **数据库**: MySQL 8.0
- **连接池**: Druid 1.2.23
- **响应式**: Spring WebFlux (用于流式响应)
- **安全**: Spring Security Crypto (密码加密)
- **构建工具**: Maven
- **文档解析**: Apache POI 4.1.2 (Office文档)、Apache PDFBox 2.0.29 (PDF文档)
- **图片处理**: Base64编码、AI图片解析服务集成

## 开发环境设置
- **编码**: UTF-8
- **Maven编译**: JDK 17
- **数据库字符集**: utf8mb4_unicode_ci
- **时区**: 系统默认时区
- **日志**: SLF4J + Logback

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

## 数据库设计规范
- **命名**: 下划线命名法
- **主键**: 雪花ID算法生成Long类型ID
- **逻辑删除**: deleted字段标记删除状态
- **时间戳**: create_time和update_time自动维护
- **外键约束**: 使用外键保证数据完整性
