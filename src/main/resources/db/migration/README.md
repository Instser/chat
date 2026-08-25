# Flyway 数据库版本控制

本目录使用 [Flyway](https://flywaydb.org/) 管理数据库 schema 版本控制。

## 工作原理

应用启动时，Flyway 自动检查 `flyway_schema_history` 表，比对已执行的迁移脚本版本，
按版本号顺序执行尚未应用的迁移脚本。

## 两种数据库场景的行为

### 1. 已有表结构的数据库（生产/开发库）

- `baseline-on-migrate: true` 触发 baseline
- Flyway 将当前库标记为基线版本 1
- **V1 脚本不会执行**（被基线跳过），现有表结构保持不变
- 从 V2 起的增量迁移才会应用

### 2. 空数据库（新环境）

- Flyway 检测到库为空，**不触发 baseline**，直接从 V1 开始执行
- `V1__baseline.sql` 包含 17 张表的完整建表语句，自动建出全部表结构
- 之后继续执行 V2+ 增量迁移

> 参考 [Flyway 官方文档](https://documentation.red-gate.com/fd/flyway-baseline-on-migrate-setting-277578974.html)：
> "If the database appears empty and a relevant baseline script exists then Flyway will attempt to execute it as part of the migration."

## V1 基线脚本内容

`V1__baseline.sql` 包含 17 张业务表的完整建表结构（仅 DDL，不含数据）：

| 表名 | 说明 |
|------|------|
| t_admin_operation_log | 管理员操作日志 |
| t_admin_user | 管理员用户 |
| t_ai_model | AI 模型 |
| t_oauth2_audit_log | OAuth2 审计日志 |
| t_package | 套餐 |
| t_system_setting | 系统设置 |
| t_payment_audit | 支付审计日志 |
| t_user | 用户 |
| t_user_account | 用户账户 |
| t_user_daily_usage | 用户每日使用记录 |
| t_user_oauth | OAuth2 用户绑定 |
| t_user_order | 用户订单 |
| t_operation_audit | 通用运维操作审计 |
| t_chat_session | 聊天会话 |
| t_chat_message | 聊天消息（含 reasoning 推理列） |
| t_file_upload | 文件上传 |
| t_web_search_content | 联网查询内容 |

已合并以下增量改动到 V1：
- `t_chat_message.reasoning` 列（2026-08-03 增量）
- `t_operation_audit` 管理员审计字段（2026-08-03 增量）

## 脚本命名规范

```
V{版本号}__{描述}.sql
```

- `V` 前缀，版本号为整数，按升序执行
- 双下划线 `__` 分隔版本号与描述
- 描述用英文或拼音，单词间用单下划线或连字符
- 文件名唯一，已应用的脚本不可改名/删除/修改内容（checksum 校验）

### 示例

```
V1__baseline.sql                              # 基线（完整建库）
V2__add_user_phone_column.sql                 # 增量：用户表加手机号列
V3__create_notification_table.sql             # 增量：新建通知表
```

## 新建增量迁移

1. 确定下一个版本号（当前最大版本号 +1）
2. 在本目录创建 `V{n}__{描述}.sql`
3. 编写 DDL/DML
4. 提交到版本库，应用启动时自动执行

## 与旧 sql/ 目录的关系

根目录 `sql/` 下的 92 个历史 SQL 脚本为引入 Flyway 前的手工管理记录，
保留作历史参考，**不再新增**。新的数据库变更一律通过本目录的 Flyway 脚本管理。

## 新环境部署流程

```
1. 创建空数据库（CREATE DATABASE chat ...）
2. 启动应用 → Flyway 自动执行 V1 建出全部 17 张表
3. 如需基础数据（AI模型、套餐等），手动执行 sql/chat.sql 中的 INSERT 语句
4. 后续增量迁移由 Flyway 自动应用
```

## 配置位置

Flyway 配置在 `src/main/resources/application.yml` 的 `spring.flyway.*` 节：

```yaml
spring:
  flyway:
    enabled: ${FLYWAY_ENABLED:true}
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 1
    validate-on-migrate: true
    placeholder-replacement: false
    table: flyway_schema_history
```
