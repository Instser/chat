# Flyway 数据库版本控制

本目录使用 [Flyway](https://flywaydb.org/) 管理数据库 schema 版本控制。

## 工作原理

应用启动时，Flyway 自动检查 `flyway_schema_history` 表，比对已执行的迁移脚本版本，
按版本号顺序执行尚未应用的迁移脚本。

## 基线策略

本项目在已有完整表结构的数据库上引入 Flyway，采用 **baseline（基线标记）** 模式：

- `baseline-on-migrate: true` — 首次对已有库执行时，将当前库标记为基线版本
- `baseline-version: 1` — `V1__baseline.sql` 为空占位脚本，仅标记基线点
- 后续增量迁移从 **V2** 起编号

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
V1__baseline.sql                              # 基线占位
V2__add_reasoning_to_chat_message.sql         # 增量：聊天消息增加推理列
V3__add_admin_audit_fields.sql                # 增量：管理员审计字段
```

## 新建增量迁移

1. 确定下一个版本号（当前最大版本号 +1）
2. 在本目录创建 `V{n}__{描述}.sql`
3. 编写 DDL/DML（幂等优先，避免对已存在对象重复创建）
4. 提交到版本库，应用启动时自动执行

## 与旧 sql/ 目录的关系

根目录 `sql/` 下的 92 个历史 SQL 脚本为引入 Flyway 前的手工管理记录，
保留作历史参考，**不再新增**。新的数据库变更一律通过本目录的 Flyway 脚本管理。

## 新建空环境部署流程

1. 从 `sql/chat.sql` 导入基础表结构与数据
2. 应用 `sql/2026-08-03_*.sql` 等基线后增量脚本（如未包含在 chat.sql 中）
3. 启动应用，Flyway 自动标记基线并应用 V2+ 迁移

## 常用命令

```bash
# 查看 Flyway 迁移状态（需配置 Maven Flyway 插件或通过应用日志查看）
mvn flyway:info    # 待后续引入插件
```

当前版本控制通过应用启动日志观察 Flyway 执行情况。
