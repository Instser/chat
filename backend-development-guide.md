# 后端开发文档

## 目录
1. [项目概述](#项目概述)
2. [技术栈](#技术栈)
3. [数据库设计](#数据库设计)
4. [API 接口文档](#api-接口文档)
5. [认证与授权](#认证与授权)
6. [部署指南](#部署指南)
7. [环境配置](#环境配置)
8. [错误处理](#错误处理)
9. [性能优化](#性能优化)
10. [安全考虑](#安全考虑)

## 项目概述

这是一个基于 Next.js 的 ChatGPT 克隆项目的后端系统。原项目采用 Next.js 全栈开发，现需要将后端功能独立出来，实现前后端分离架构。

### 核心功能模块
- 用户认证与授权
- 聊天会话管理
- 消息管理
- 文件上传
- AI 对话接口

## 技术栈

### 推荐后端技术栈
- **运行时**: Node.js (v18+)
- **框架**: Express.js / Fastify / Koa.js
- **数据库**: MySQL 8.0+
- **ORM**: Prisma / TypeORM / Sequelize
- **认证**: JWT (jsonwebtoken)
- **密码加密**: bcryptjs
- **文件上传**: Multer
- **API文档**: Swagger/OpenAPI
- **日志**: Winston
- **进程管理**: PM2
- **容器化**: Docker

### 数据库连接
```javascript
// 数据库配置 (基于原项目)
const dbConfig = {
  host: process.env.DB_HOST || '203.0.113.16',
  port: parseInt(process.env.DB_PORT || '3306'),
  user: process.env.DB_USER || 'tousechat',
  password: process.env.DB_PASSWORD || 'tousechat',
  database: process.env.DB_NAME || 'tousechat',
  connectionLimit: 10,
  charset: 'utf8mb4',
  timezone: '+00:00'
}
```

## 数据库设计

### 表结构

#### 1. 用户表 (users)
```sql
CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) UNIQUE NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### 2. 会话表 (sessions)
```sql
CREATE TABLE sessions (
  id VARCHAR(255) PRIMARY KEY,
  user_id INT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

#### 3. 聊天表 (chats)
```sql
CREATE TABLE chats (
  id VARCHAR(36) PRIMARY KEY,
  user_id INT NOT NULL,
  title VARCHAR(255) NOT NULL DEFAULT 'New Chat',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

#### 4. 消息表 (messages)
```sql
CREATE TABLE messages (
  id VARCHAR(36) PRIMARY KEY,
  chat_id VARCHAR(36) NOT NULL,
  role ENUM('user', 'assistant') NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
);
```

### 索引优化
```sql
-- 消息表复合索引
CREATE INDEX idx_messages_chat_updated ON messages(chat_id, updated_at);

-- 聊天表复合索引
CREATE INDEX idx_chats_user_updated ON chats(user_id, updated_at);

-- 会话表复合索引
CREATE INDEX idx_sessions_user_expires ON sessions(user_id, expires_at);
```

## API 接口文档

### 1. 认证相关接口

#### 用户注册
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "testuser",
  "email": "test@example.com", 
  "password": "password123"
}
```

**响应**:

```json
{
  "success": true,
  "user": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com"
  }
}
```

#### 用户登录
```http
POST /api/auth/login
Content-Type: application/json

{
  "usernameOrEmail": "testuser",
  "password": "password123"
}
```

**响应**:
```json
{
  "success": true,
  "user": {
    "id": 1,
    "username": "testuser", 
    "email": "test@example.com"
  },
  "token": "your-jwt-token"
}
```

#### 获取当前用户信息
```http
GET /api/auth/me
Authorization: Bearer <token>
```

#### 用户登出
```http
POST /api/auth/logout
Authorization: Bearer <token>
```

#### 修改密码
```http
PUT /api/auth/change-password
Authorization: Bearer <token>
Content-Type: application/json

{
  "currentPassword": "oldpassword",
  "newPassword": "newpassword"
}
```

#### 更新用户资料
```http
PUT /api/auth/update-profile
Authorization: Bearer <token>
Content-Type: application/json

{
  "username": "newusername",
  "email": "newemail@example.com"
}
```

### 2. 聊天管理接口

#### 获取聊天列表
```http
GET /api/chats?limit=20&offset=0
Authorization: Bearer <token>
```

**响应**:
```json
{
  "chats": [
    {
      "id": "chat-uuid",
      "title": "Chat Title",
      "created_at": "2024-01-01T00:00:00Z",
      "updated_at": "2024-01-01T00:00:00Z"
    }
  ],
  "total": 100,
  "hasMore": true
}
```

#### 创建新聊天
```http
POST /api/chats
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "New Chat"
}
```

#### 获取特定聊天信息
```http
GET /api/chats/{chatId}
Authorization: Bearer <token>
```

#### 更新聊天标题
```http
PUT /api/chats/{chatId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "Updated Chat Title"
}
```

#### 删除聊天
```http
DELETE /api/chats/{chatId}
Authorization: Bearer <token>
```

#### 生成聊天标题
```http
POST /api/chats/{chatId}/generate-title
Authorization: Bearer <token>
```

### 3. 消息管理接口

#### 获取聊天消息
```http
GET /api/chats/{chatId}/messages?limit=50&offset=0
Authorization: Bearer <token>
```

**响应**:
```json
{
  "messages": [
    {
      "id": "message-uuid",
      "role": "user",
      "content": "Hello",
      "created_at": "2024-01-01T00:00:00Z",
      "updated_at": "2024-01-01T00:00:00Z"
    }
  ],
  "total": 20,
  "hasMore": false
}
```

#### 添加新消息
```http
POST /api/chats/{chatId}/messages
Authorization: Bearer <token>
Content-Type: application/json

{
  "role": "user",
  "content": "Hello, how are you?"
}
```

#### 更新消息
```http
PUT /api/chats/{chatId}/messages/{messageId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "content": "Updated message content"
}
```

#### 删除消息
```http
DELETE /api/chats/{chatId}/messages/{messageId}
Authorization: Bearer <token>
```

#### 批量删除消息
```http
POST /api/messages/delete
Authorization: Bearer <token>
Content-Type: application/json

{
  "messageIds": ["msg1", "msg2"],
  "chatId": "chat-uuid"
}
```

#### 删除指定消息之后的所有消息
```http
DELETE /api/messages/delete?messageId=msg-uuid&chatId=chat-uuid
Authorization: Bearer <token>
```

### 4. AI 对话接口

#### 流式对话
```http
POST /api/chat
Authorization: Bearer <token>
Content-Type: application/json

{
  "messages": [
    {
      "role": "user",
      "content": "Hello"
    }
  ],
  "deepThinking": false
}
```

**响应**: 流式文本响应 (SSE)

### 5. 文件上传接口

#### 上传文件
```http
POST /api/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

files: [File, File, ...]
```

**响应**:
```json
{
  "success": true,
  "files": [
    {
      "name": "file.jpg",
      "originalName": "file.jpg", 
      "size": 12345,
      "type": "image/jpeg",
      "url": "/uploads/timestamp-file.jpg",
      "uploadedAt": "2024-01-01T00:00:00Z"
    }
  ],
  "message": "Successfully uploaded 1 file(s)"
}
```

## 认证与授权

### JWT 实现
```javascript
const JWT_SECRET = process.env.JWT_SECRET || 'your-super-secret-jwt-key'
const JWT_EXPIRES_IN = '7d'

// 生成 token
function generateToken(payload) {
  return jwt.sign(payload, JWT_SECRET, { expiresIn: JWT_EXPIRES_IN })
}

// 验证 token
function verifyToken(token) {
  try {
    return jwt.verify(token, JWT_SECRET)
  } catch (error) {
    return null
  }
}
```

### 密码加密
```javascript
const bcrypt = require('bcryptjs')

// 加密密码
async function hashPassword(password) {
  const saltRounds = 12
  return await bcrypt.hash(password, saltRounds)
}

// 验证密码
async function verifyPassword(password, hash) {
  return await bcrypt.compare(password, hash)
}
```

### 中间件示例 (Express)
```javascript
// 认证中间件
async function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization']
  const token = authHeader && authHeader.split(' ')[1]
  
  if (!token) {
    return res.status(401).json({ error: 'Access token required' })
  }
  
  const payload = verifyToken(token)
  if (!payload) {
    return res.status(403).json({ error: 'Invalid or expired token' })
  }
  
  req.user = await getUserById(payload.userId)
  if (!req.user) {
    return res.status(403).json({ error: 'User not found' })
  }
  
  next()
}
```

## 部署指南

### 1. 使用 Express.js 的基本项目结构
```
backend/
├── src/
│   ├── controllers/
│   │   ├── auth.js
│   │   ├── chats.js
│   │   ├── messages.js
│   │   └── upload.js
│   ├── middleware/
│   │   ├── auth.js
│   │   ├── cors.js
│   │   └── errorHandler.js
│   ├── models/
│   │   ├── User.js
│   │   ├── Chat.js
│   │   └── Message.js
│   ├── routes/
│   │   ├── auth.js
│   │   ├── chats.js
│   │   ├── messages.js
│   │   └── upload.js
│   ├── services/
│   │   ├── ai.js
│   │   ├── database.js
│   │   └── storage.js
│   ├── utils/
│   │   ├── validation.js
│   │   └── helpers.js
│   └── app.js
├── uploads/
├── logs/
├── .env
├── .env.example
├── package.json
├── Dockerfile
└── docker-compose.yml
```

### 2. package.json 示例
```json
{
  "name": "chatgpt-clone-backend",
  "version": "1.0.0",
  "description": "ChatGPT Clone Backend API",
  "main": "src/app.js",
  "scripts": {
    "start": "node src/app.js",
    "dev": "nodemon src/app.js",
    "test": "jest",
    "build": "echo 'No build step required'",
    "lint": "eslint src/"
  },
  "dependencies": {
    "express": "^4.18.2",
    "mysql2": "^3.6.0",
    "bcryptjs": "^2.4.3",
    "jsonwebtoken": "^9.0.2",
    "cors": "^2.8.5",
    "helmet": "^7.0.0",
    "express-rate-limit": "^6.10.0",
    "multer": "^1.4.5",
    "winston": "^3.10.0",
    "joi": "^17.9.2",
    "uuid": "^9.0.0",
    "@ai-sdk/openai": "latest",
    "ai": "latest",
    "dotenv": "^16.3.1"
  },
  "devDependencies": {
    "nodemon": "^3.0.1",
    "jest": "^29.6.2",
    "eslint": "^8.45.0",
    "supertest": "^6.3.3"
  }
}
```

### 3. 基本 Express 应用 (src/app.js)
```javascript
const express = require('express')
const cors = require('cors')
const helmet = require('helmet')
const rateLimit = require('express-rate-limit')
require('dotenv').config()

const authRoutes = require('./routes/auth')
const chatRoutes = require('./routes/chats')
const messageRoutes = require('./routes/messages')
const uploadRoutes = require('./routes/upload')
const { initDatabase } = require('./services/database')
const errorHandler = require('./middleware/errorHandler')

const app = express()
const PORT = process.env.PORT || 3000

// 安全中间件
app.use(helmet())
app.use(cors({
  origin: process.env.FRONTEND_URL || 'http://localhost:3001',
  credentials: true
}))

// 限流
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100 // limit each IP to 100 requests per windowMs
})
app.use(limiter)

// 解析中间件
app.use(express.json({ limit: '10mb' }))
app.use(express.urlencoded({ extended: true }))

// 静态文件
app.use('/uploads', express.static('uploads'))

// 路由
app.use('/api/auth', authRoutes)
app.use('/api/chats', chatRoutes)
app.use('/api/messages', messageRoutes)
app.use('/api/upload', uploadRoutes)

// 健康检查
app.get('/health', (req, res) => {
  res.json({ status: 'OK', timestamp: new Date().toISOString() })
})

// 错误处理
app.use(errorHandler)

// 启动服务器
async function startServer() {
  try {
    await initDatabase()
    app.listen(PORT, () => {
      console.log(`Server running on port ${PORT}`)
    })
  } catch (error) {
    console.error('Failed to start server:', error)
    process.exit(1)
  }
}

startServer()

module.exports = app
```

### 4. Docker 部署

#### Dockerfile
```dockerfile
FROM node:18-alpine

WORKDIR /app

# 复制 package 文件
COPY package*.json ./

# 安装依赖
RUN npm ci --only=production

# 复制源代码
COPY src/ ./src/

# 创建上传目录
RUN mkdir -p uploads logs

# 暴露端口
EXPOSE 3000

# 启动命令
CMD ["npm", "start"]
```

#### docker-compose.yml
```yaml
version: '3.8'

services:
  backend:
    build: .
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=production
      - DB_HOST=mysql
      - DB_PORT=3306
      - DB_USER=chatapp
      - DB_PASSWORD=chatpass
      - DB_NAME=chatdb
      - JWT_SECRET=your-super-secret-jwt-key
    depends_on:
      - mysql
    volumes:
      - ./uploads:/app/uploads
      - ./logs:/app/logs

  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    environment:
      - MYSQL_ROOT_PASSWORD=rootpass
      - MYSQL_DATABASE=chatdb
      - MYSQL_USER=chatapp
      - MYSQL_PASSWORD=chatpass
    volumes:
      - mysql_data:/var/lib/mysql

volumes:
  mysql_data:
```

## 环境配置

### .env.example
```env
# 应用配置
NODE_ENV=development
PORT=3000
FRONTEND_URL=http://localhost:3001

# 数据库配置
DB_HOST=localhost
DB_PORT=3306
DB_USER=your_database_user
DB_PASSWORD=your_database_password
DB_NAME=your_database_name

# JWT 配置
JWT_SECRET=your_super_secret_jwt_key_at_least_32_characters_long

# AI 配置
AI_BASE_URL=http://203.0.113.15:3000/v1
AI_API_KEY=a2366021253
AI_MODEL=qwen3-235b-a22b

# 文件上传配置
UPLOAD_MAX_SIZE=10485760
UPLOAD_ALLOWED_TYPES=image/jpeg,image/png,image/gif,text/plain,application/pdf

# 日志配置
LOG_LEVEL=info
LOG_FILE=logs/app.log
```

## 错误处理

### 统一错误处理中间件
```javascript
// middleware/errorHandler.js
function errorHandler(err, req, res, next) {
  console.error(err.stack)

  // 数据库连接错误
  if (err.code === 'ER_ACCESS_DENIED_ERROR') {
    return res.status(500).json({
      error: 'Database connection failed',
      code: 'DB_CONNECTION_ERROR'
    })
  }

  // JWT 错误
  if (err.name === 'JsonWebTokenError') {
    return res.status(401).json({
      error: 'Invalid token',
      code: 'INVALID_TOKEN'
    })
  }

  // 验证错误
  if (err.name === 'ValidationError') {
    return res.status(400).json({
      error: err.message,
      code: 'VALIDATION_ERROR'
    })
  }

  // 默认错误
  res.status(500).json({
    error: 'Internal server error',
    code: 'INTERNAL_ERROR'
  })
}

module.exports = errorHandler
```

## 性能优化

### 1. 数据库连接池
```javascript
const mysql = require('mysql2/promise')

const pool = mysql.createPool({
  host: process.env.DB_HOST,
  port: process.env.DB_PORT,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME,
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0,
  acquireTimeout: 60000,
  timeout: 60000,
  charset: 'utf8mb4'
})
```

### 2. 缓存策略
```javascript
const Redis = require('redis')
const client = Redis.createClient({
  host: process.env.REDIS_HOST || 'localhost',
  port: process.env.REDIS_PORT || 6379
})

// 缓存用户会话
async function cacheUserSession(userId, userData) {
  await client.setex(`user:${userId}`, 3600, JSON.stringify(userData))
}

// 获取缓存的用户数据
async function getCachedUser(userId) {
  const cached = await client.get(`user:${userId}`)
  return cached ? JSON.parse(cached) : null
}
```

### 3. 分页优化
```javascript
// 使用游标分页替代 OFFSET
async function getMessagesCursor(chatId, cursor = null, limit = 50) {
  let query = `
    SELECT id, role, content, created_at 
    FROM messages 
    WHERE chat_id = ?
  `
  let params = [chatId]

  if (cursor) {
    query += ` AND created_at < ?`
    params.push(cursor)
  }

  query += ` ORDER BY created_at DESC LIMIT ?`
  params.push(limit)

  const [rows] = await pool.execute(query, params)
  return rows
}
```

## 安全考虑

### 1. 输入验证
```javascript
const Joi = require('joi')

const userRegistrationSchema = Joi.object({
  username: Joi.string().alphanum().min(3).max(50).required(),
  email: Joi.string().email().required(),
  password: Joi.string().min(6).required()
})

function validateRegistration(req, res, next) {
  const { error } = userRegistrationSchema.validate(req.body)
  if (error) {
    return res.status(400).json({ 
      error: error.details[0].message 
    })
  }
  next()
}
```

### 2. SQL 注入防护
```javascript
// 始终使用参数化查询
async function getUserByEmail(email) {
  const [rows] = await pool.execute(
    'SELECT * FROM users WHERE email = ?',
    [email]
  )
  return rows[0]
}
```

### 3. 限流和 DDoS 保护
```javascript
const rateLimit = require('express-rate-limit')

// 登录限流
const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 5, // 最多 5 次尝试
  message: 'Too many login attempts, please try again later',
  standardHeaders: true,
  legacyHeaders: false,
})

app.use('/api/auth/login', loginLimiter)
```

### 4. CORS 配置
```javascript
const corsOptions = {
  origin: function (origin, callback) {
    const allowedOrigins = [
      'http://localhost:3001',
      'https://yourdomain.com'
    ]
    
    if (!origin || allowedOrigins.indexOf(origin) !== -1) {
      callback(null, true)
    } else {
      callback(new Error('Not allowed by CORS'))
    }
  },
  credentials: true,
  optionsSuccessStatus: 200
}

app.use(cors(corsOptions))
```

## 迁移步骤

### 1. 数据迁移
保持现有的数据库结构不变，确保数据无缝迁移。

### 2. API 接口适配
前端需要更新 API 调用：
- 更改 base URL
- 添加 Authorization header
- 处理 CORS

### 3. 前端适配示例
```javascript
// 前端 API 客户端
const API_BASE_URL = 'http://localhost:3000/api'

class ApiClient {
  constructor() {
    this.token = localStorage.getItem('auth-token')
  }

  async request(endpoint, options = {}) {
    const url = `${API_BASE_URL}${endpoint}`
    const config = {
      headers: {
        'Content-Type': 'application/json',
        ...options.headers
      },
      ...options
    }

    if (this.token) {
      config.headers.Authorization = `Bearer ${this.token}`
    }

    const response = await fetch(url, config)
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    
    return response.json()
  }

  async login(credentials) {
    const response = await this.request('/auth/login', {
      method: 'POST',
      body: JSON.stringify(credentials)
    })
    
    this.token = response.token
    localStorage.setItem('auth-token', response.token)
    return response
  }

  async getChats(params = {}) {
    const query = new URLSearchParams(params).toString()
    return this.request(`/chats?${query}`)
  }
}
```

这份文档提供了将你的 Next.js 全栈项目迁移到独立后端的完整指南。根据你的具体需求，可以选择使用 Express.js、Fastify 或其他 Node.js 框架来实现。