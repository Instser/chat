# Chat消息接口filesName字段功能测试指南

**📅 创建时间**: 2025-06-25 22:45:00  
**👤 创建人员**: Augment Agent  
**📊 测试版本**: v7.0  

## 功能概述

测试GET `/api/chats/{chatId}/messages`接口的filesName字段功能，验证消息关联文件时是否正确返回文件名列表。

## 测试前提条件

1. **数据库中需要有测试数据**：
   - 至少一个聊天会话（t_chat_session表）
   - 该会话中至少一条消息（t_chat_message表）
   - 至少一个文件上传记录（t_file_upload表），且message_id关联到消息

2. **应用正常运行**：
   - Spring Boot应用已启动
   - 数据库连接正常
   - JWT认证配置正确

## 测试步骤

### 1. 准备测试数据

#### 创建测试会话
```sql
INSERT INTO t_chat_session (session_id, user_id, title, model_id, created_time, update_time, deleted) 
VALUES ('test-session-001', 1, '测试会话', 1, NOW(), NOW(), 0);
```

#### 创建测试消息
```sql
INSERT INTO t_chat_message (message_id, session_id, role, content, message_type, user_id, model_id, token_count, status, create_time, update_time, deleted) 
VALUES ('test-message-001', 'test-session-001', 'user', '请分析这些文件', 'text', 1, 1, 10, 1, NOW(), NOW(), 0);
```

#### 创建测试文件记录
```sql
INSERT INTO t_file_upload (file_id, user_id, session_id, message_id, file_name, file_type, file_size, storage_path, content, status, create_time, update_time, deleted) 
VALUES 
('test-file-001', 1, 'test-session-001', 'test-message-001', 'document1.txt', 'text/plain', 1024, '/path/to/file1', 'test content 1', 1, NOW(), NOW(), 0),
('test-file-002', 1, 'test-session-001', 'test-message-001', 'document2.pdf', 'application/pdf', 2048, '/path/to/file2', 'test content 2', 1, NOW(), NOW(), 0);
```

### 2. 获取JWT Token

```bash
# 登录获取token
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test@example.com",
    "password": "password123"
  }'
```

### 3. 测试API调用

```bash
# 调用获取消息接口
curl -X GET "http://localhost:8080/api/chats/test-session-001/messages" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json"
```

## 预期响应格式

### 成功响应示例
```json
{
    "code": 200,
    "message": "获取消息成功",
    "data": {
        "total": 1,
        "hasMore": false,
        "messages": [
            {
                "id": "test-message-001",
                "chatId": "test-session-001",
                "role": "user",
                "content": "请分析这些文件",
                "filesName": "document1.txt, document2.pdf",
                "messageType": "text",
                "userId": "1",
                "model": null,
                "tokenCount": 10,
                "status": 1,
                "errorMessage": null,
                "createdTime": "2025-06-25T22:45:00",
                "updateTime": "2025-06-25T22:45:00"
            }
        ]
    },
    "timestamp": 1750851538422
}
```

### 无文件关联的消息
```json
{
    "id": "test-message-002",
    "chatId": "test-session-001",
    "role": "assistant",
    "content": "我来帮您分析这些文件",
    "filesName": null,
    "messageType": "text",
    "userId": "1",
    "model": null,
    "tokenCount": 15,
    "status": 1,
    "errorMessage": null,
    "createdTime": "2025-06-25T22:45:00",
    "updateTime": "2025-06-25T22:45:00"
}
```

## 验证要点

### 1. 字段存在性验证
- ✅ 响应中每个message对象都包含filesName字段
- ✅ 有文件关联的消息filesName为逗号分隔的字符串
- ✅ 无文件关联的消息filesName为null

### 2. 文件名格式验证
- ✅ 多个文件名使用", "（逗号+空格）分隔
- ✅ 文件名顺序按照create_time DESC排序
- ✅ 只包含文件名，不包含路径或其他信息

### 3. 权限验证
- ✅ 只能查看自己的聊天消息
- ✅ 只显示属于当前用户的文件
- ✅ 无权访问的会话返回403错误

### 4. 性能验证
- ✅ 接口响应时间在合理范围内（<2秒）
- ✅ 数据库查询次数合理（每个消息一次文件查询）
- ✅ 内存使用正常，无内存泄漏

## 错误场景测试

### 1. 会话不存在
```bash
curl -X GET "http://localhost:8080/api/chats/non-existent-session/messages" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```
**预期**: 返回404或相应错误信息

### 2. 无权访问会话
```bash
# 使用其他用户的token访问
curl -X GET "http://localhost:8080/api/chats/test-session-001/messages" \
  -H "Authorization: Bearer OTHER_USER_TOKEN"
```
**预期**: 返回403无权访问错误

### 3. 无效Token
```bash
curl -X GET "http://localhost:8080/api/chats/test-session-001/messages" \
  -H "Authorization: Bearer invalid_token"
```
**预期**: 返回401认证失败错误

## 清理测试数据

测试完成后清理测试数据：

```sql
DELETE FROM t_file_upload WHERE file_id IN ('test-file-001', 'test-file-002');
DELETE FROM t_chat_message WHERE message_id = 'test-message-001';
DELETE FROM t_chat_session WHERE session_id = 'test-session-001';
```

## 总结

通过以上测试步骤，可以全面验证Chat消息接口filesName字段功能是否正确实现，确保：

1. 字段正确添加到响应中
2. 文件名格式符合要求
3. 权限控制正常工作
4. 性能表现良好
5. 错误处理完善

如果测试过程中发现问题，请检查：
- VOConverter转换逻辑是否正确
- FileUploadService查询方法是否正常
- 数据库关联关系是否正确
- JWT认证配置是否有效
