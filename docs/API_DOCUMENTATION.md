# API 文档

## 概述
这是一个基于 Next.js 的 ChatGPT 克隆应用的 API 文档，提供了完整的用户认证、聊天管理和消息处理功能。

## 基本信息
- **基础URL**: `http://localhost:3000` (开发环境)
- **认证方式**: JWT Token (通过 HTTP-only Cookie)
- **数据格式**: JSON
- **编码**: UTF-8

## 认证机制
API 使用 JWT Token 进行身份验证，Token 存储在名为 `auth-token` 的 HTTP-only Cookie 中。
- Token 有效期：7天
- 自动在请求中携带
- 退出登录时自动清除

---

## API 接口详情

### 🔐 用户认证相关

#### 1. 用户注册
**接口**: `POST /api/auth/register`

**请求参数**:
```json
{
  "username": "string (3-50字符)",
  "email": "string (有效邮箱格式)",
  "password": "string (至少6字符)"
}
```

**成功响应** (201):
```json
{
  "success": true,
  "user": {
    "id": "string",
    "username": "string",
    "email": "string"
  }
}
```

**错误响应**:
- `400`: 参数缺失或格式错误
- `409`: 用户名或邮箱已存在
- `500`: 服务器内部错误

---

#### 2. 用户登录
**接口**: `POST /api/auth/login`

**请求参数**:
```json
{
  "usernameOrEmail": "string (用户名或邮箱)",
  "password": "string"
}
```

**成功响应** (200):
```json
{
  "success": true,
  "user": {
    "id": "string",
    "username": "string",
    "email": "string"
  }
}
```

**错误响应**:
- `400`: 参数缺失
- `401`: 用户名/邮箱或密码错误
- `500`: 服务器内部错误

---

#### 3. 获取当前用户信息
**接口**: `GET /api/auth/me`

**请求参数**: 无

**成功响应** (200):
```json
{
  "success": true,
  "user": {
    "id": "string",
    "username": "string",
    "email": "string"
  }
}
```

**错误响应**:
- `401`: 未认证
- `500`: 服务器内部错误

---

#### 4. 用户退出登录
**接口**: `POST /api/auth/logout`

**请求参数**: 无

**成功响应** (200):
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

**错误响应**:
- `500`: 服务器内部错误

---

#### 5. 修改密码
**接口**: `PUT /api/auth/change-password`

**请求参数**:
```json
{
  "currentPassword": "string",
  "newPassword": "string (至少6字符)",
  "confirmPassword": "string"
}
```

**成功响应** (200):
```json
{
  "success": true,
  "message": "Password changed successfully"
}
```

**错误响应**:
- `400`: 参数错误或当前密码不正确
- `401`: 未认证
- `500`: 服务器内部错误

---

#### 6. 更新用户信息
**接口**: `PUT /api/auth/update-profile`

**请求参数**:
```json
{
  "username": "string (3-50字符)",
  "email": "string (有效邮箱格式)"
}
```

**成功响应** (200):
```json
{
  "success": true,
  "user": {
    "id": "string",
    "username": "string",
    "email": "string"
  }
}
```

**错误响应**:
- `400`: 参数错误
- `401`: 未认证
- `409`: 用户名或邮箱已存在
- `500`: 服务器内部错误

---

### 💬 聊天管理相关

#### 7. 获取聊天列表
**接口**: `GET /api/chats`

**查询参数**:
- `limit`: number (可选，默认20) - 每页数量
- `offset`: number (可选，默认0) - 偏移量

**成功响应** (200):
```json
{
  "chats": [
    {
      "id": "string",
      "title": "string",
      "created_at": "string (ISO 8601)",
      "updated_at": "string (ISO 8601)"
    }
  ],
  "total": "number",
  "hasMore": "boolean"
}
```

**错误响应**:
- `401`: 未认证
- `500`: 服务器内部错误

---

#### 8. 创建新聊天
**接口**: `POST /api/chats`

**请求参数**:
```json
{
  "title": "string (可选，默认为 'New Chat')"
}
```

**成功响应** (200):
```json
{
  "chat": {
    "id": "string",
    "title": "string",
    "created_at": "string (ISO 8601)",
    "updated_at": "string (ISO 8601)"
  }
}
```

**错误响应**:
- `401`: 未认证
- `500`: 服务器内部错误

---

#### 9. 获取特定聊天信息
**接口**: `GET /api/chats/{chatId}`

**路径参数**:
- `chatId`: string - 聊天ID

**成功响应** (200):
```json
{
  "chat": {
    "id": "string",
    "title": "string",
    "created_at": "string (ISO 8601)",
    "updated_at": "string (ISO 8601)"
  }
}
```

**错误响应**:
- `401`: 未认证
- `404`: 聊天不存在
- `500`: 服务器内部错误

---

#### 10. 更新聊天标题
**接口**: `PUT /api/chats/{chatId}`

**路径参数**:
- `chatId`: string - 聊天ID

**请求参数**:
```json
{
  "title": "string"
}
```

**成功响应** (200):
```json
{
  "chat": {
    "id": "string",
    "title": "string",
    "created_at": "string (ISO 8601)",
    "updated_at": "string (ISO 8601)"
  }
}
```

**错误响应**:
- `401`: 未认证
- `404`: 聊天不存在
- `500`: 服务器内部错误

---

#### 11. 删除聊天
**接口**: `DELETE /api/chats/{chatId}`

**路径参数**:
- `chatId`: string - 聊天ID

**成功响应** (200):
```json
{
  "success": true
}
```

**错误响应**:
- `401`: 未认证
- `404`: 聊天不存在
- `500`: 服务器内部错误

---

#### 12. AI自动生成聊天标题
**接口**: `POST /api/chats/{chatId}/generate-title`

**路径参数**:
- `chatId`: string - 聊天ID

**请求参数**: 无

**成功响应** (200):
```json
{
  "chat": {
    "id": "string",
    "title": "string",
    "created_at": "string (ISO 8601)",
    "updated_at": "string (ISO 8601)"
  },
  "generatedTitle": "string"
}
```

**错误响应**:
- `400`: 没有消息可生成标题
- `401`: 未认证
- `404`: 聊天不存在
- `502`: AI模型错误
- `503`: AI服务不可用
- `500`: 服务器内部错误

---

### 📝 消息管理相关

#### 13. 获取聊天消息
**接口**: `GET /api/chats/{chatId}/messages`

**路径参数**:
- `chatId`: string - 聊天ID

**查询参数**:
- `limit`: number (可选，默认50) - 每页数量
- `offset`: number (可选，默认0) - 偏移量

**成功响应** (200):
```json
{
  "messages": [
    {
      "id": "string",
      "role": "string (user/assistant)",
      "content": "string",
      "created_at": "string (ISO 8601)",
      "updated_at": "string (ISO 8601)"
    }
  ],
  "total": "number",
  "hasMore": "boolean"
}
```

**错误响应**:
- `401`: 未认证
- `404`: 聊天不存在
- `500`: 服务器内部错误

---

#### 14. 添加新消息
**接口**: `POST /api/chats/{chatId}/messages`

**路径参数**:
- `chatId`: string - 聊天ID

**请求参数**:
```json
{
  "role": "string (user/assistant)",
  "content": "string"
}
```

**成功响应** (200):
```json
{
  "message": {
    "id": "string",
    "role": "string",
    "content": "string",
    "created_at": "string (ISO 8601)",
    "updated_at": "string (ISO 8601)"
  }
}
```

**错误响应**:
- `400`: 参数错误
- `401`: 未认证
- `404`: 聊天不存在
- `500`: 服务器内部错误

---

#### 15. 更新消息内容
**接口**: `PUT /api/chats/{chatId}/messages/{messageId}`

**路径参数**:
- `chatId`: string - 聊天ID
- `messageId`: string - 消息ID

**请求参数**:
```json
{
  "content": "string"
}
```

**成功响应** (200):
```json
{
  "success": true,
  "message": "Message updated successfully"
}
```

**错误响应**:
- `400`: 参数错误或只能编辑用户消息
- `401`: 未认证
- `404`: 聊天或消息不存在
- `500`: 服务器内部错误

---

#### 16. 批量删除消息
**接口**: `POST /api/messages/delete`

**请求参数**:
```json
{
  "messageIds": ["string"],
  "chatId": "string"
}
```

**成功响应** (200):
```json
{
  "success": true,
  "deletedCount": "number"
}
```

**错误响应**:
- `400`: 参数错误
- `401`: 未认证
- `404`: 聊天不存在
- `500`: 服务器内部错误

---

#### 17. 删除指定消息及之后的消息
**接口**: `DELETE /api/messages/delete`

**查询参数**:
- `messageId`: string - 消息ID
- `chatId`: string - 聊天ID

**成功响应** (200):
```json
{
  "success": true,
  "deletedCount": "number"
}
```

**错误响应**:
- `400`: 参数错误
- `401`: 未认证
- `404`: 聊天或消息不存在
- `500`: 服务器内部错误

---

### 🤖 AI聊天相关

#### 18. AI聊天对话
**接口**: `POST /api/chat`

**请求参数**:
```json
{
  "messages": [
    {
      "role": "string (user/assistant/system)",
      "content": "string"
    }
  ],
  "deepThinking": "boolean (可选，是否启用深度思考模式)"
}
```

**成功响应** (200):
返回 Server-Sent Events (SSE) 流式响应

**错误响应**:
- `500`: AI服务错误或内部服务器错误

**注意**: 此接口返回流式响应，需要使用 SSE 客户端处理。

---

### 📎 文件上传相关

#### 19. 文件上传
**接口**: `POST /api/upload`

**请求参数**:
- Content-Type: `multipart/form-data`
- `files`: File[] - 上传的文件

**成功响应** (200):
```json
{
  "success": true,
  "files": [
    {
      "name": "string",
      "originalName": "string",
      "size": "number",
      "type": "string",
      "url": "string",
      "uploadedAt": "string (ISO 8601)"
    }
  ],
  "message": "string"
}
```

**错误响应**:
- `400`: 没有文件上传
- `500`: 服务器内部错误

---

## 错误码说明

| 状态码 | 说明 |
|--------|------|
| 200 | 请求成功 |
| 201 | 创建成功 |
| 400 | 请求参数错误 |
| 401 | 未认证或认证失败 |
| 404 | 资源不存在 |
| 409 | 资源冲突（如用户名已存在） |
| 500 | 服务器内部错误 |
| 502 | AI模型错误 |
| 503 | AI服务不可用 |

## 使用示例

### JavaScript/TypeScript 示例

```javascript
// 登录示例
const login = async (usernameOrEmail, password) => {
  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        usernameOrEmail,
        password
      })
    });
    
    const data = await response.json();
    
    if (response.ok) {
      console.log('登录成功:', data.user);
      return data;
    } else {
      console.error('登录失败:', data.error);
      throw new Error(data.error);
    }
  } catch (error) {
    console.error('网络错误:', error);
    throw error;
  }
};

// 获取聊天列表示例
const getChats = async (limit = 20, offset = 0) => {
  try {
    const response = await fetch(`/api/chats?limit=${limit}&offset=${offset}`);
    const data = await response.json();
    
    if (response.ok) {
      return data;
    } else {
      throw new Error(data.error);
    }
  } catch (error) {
    console.error('获取聊天列表失败:', error);
    throw error;
  }
};

// 发送消息示例
const sendMessage = async (chatId, content) => {
  try {
    const response = await fetch(`/api/chats/${chatId}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        role: 'user',
        content
      })
    });
    
    const data = await response.json();
    
    if (response.ok) {
      return data.message;
    } else {
      throw new Error(data.error);
    }
  } catch (error) {
    console.error('发送消息失败:', error);
    throw error;
  }
};

// AI聊天示例（SSE）
const chatWithAI = (messages, onMessage, onError, onComplete) => {
  fetch('/api/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      messages,
      deepThinking: false
    })
  })
  .then(response => {
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    
    function readStream() {
      reader.read().then(({ done, value }) => {
        if (done) {
          onComplete();
          return;
        }
        
        const chunk = decoder.decode(value);
        onMessage(chunk);
        readStream();
      }).catch(onError);
    }
    
    readStream();
  })
  .catch(onError);
};
```

## 注意事项

1. **认证**: 除了注册和登录接口外，其他所有接口都需要认证
2. **CORS**: 在生产环境中需要正确配置跨域设置
3. **速率限制**: 建议在生产环境中添加API速率限制
4. **文件上传**: 上传的文件会保存在 `/public/uploads/` 目录下
5. **AI服务**: AI聊天功能依赖外部AI服务，需要确保服务可用性
6. **数据库**: 需要正确配置MySQL数据库连接
7. **环境变量**: 确保设置正确的JWT密钥和数据库配置

## 数据库模型

### 用户表 (users)
```sql
CREATE TABLE users (
  id VARCHAR(36) PRIMARY KEY,
  username VARCHAR(50) UNIQUE NOT NULL,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 聊天表 (chats)
```sql
CREATE TABLE chats (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  title VARCHAR(255) NOT NULL DEFAULT 'New Chat',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### 消息表 (messages)
```sql
CREATE TABLE messages (
  id VARCHAR(36) PRIMARY KEY,
  chat_id VARCHAR(36) NOT NULL,
  role ENUM('user', 'assistant', 'system') NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
);
```

---

*文档版本: 1.0*  
*最后更新: 2025年6月14日*