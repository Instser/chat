# 07-19 21:14 - Gemini联网请求认证格式修改为Bearer

## 修改概述

根据用户要求，将项目中Gemini联网请求的认证格式从URL查询参数改为Bearer头部认证，保持其他逻辑和tool参数不变。

**追加修改**: 根据用户进一步要求，移除了"generationConfig"参数，简化请求体结构。

## 修改内容

### 1. GeminiNetworkServiceImpl.java 修改

#### 1.1 认证方式变更

**修改前**:
```java
// 设置请求头
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);

// 构建请求URL，使用配置的API密钥
String requestUrl = String.format("%s/models/%s:generateContent?key=%s",
    geminiConfig.getBaseUrl(), GEMINI_MODEL_CODE, geminiConfig.getApiKey());
```

**修改后**:
```java
// 设置请求头，使用Bearer认证
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.setBearerAuth(geminiConfig.getApiKey());

// 构建请求URL，不再使用key参数
String requestUrl = String.format("%s/models/%s:generateContent",
    geminiConfig.getBaseUrl(), GEMINI_MODEL_CODE);
```

#### 1.2 具体变更点

1. **请求头变更**: 
   - 新增 `headers.setBearerAuth(geminiConfig.getApiKey())`
   - 自动设置 `Authorization: Bearer <api_key>` 头部

2. **URL简化**:
   - 移除URL中的 `?key=` 查询参数
   - URL格式从 `xxx:generateContent?key=xxx` 改为 `xxx:generateContent`

3. **保持不变的部分**:
   - `tools` 参数（包含googleSearch）保持不变
   - 基本请求体结构保持不变

4. **追加修改 - 移除generationConfig**:
   - 移除 `generationConfig` 参数设置
   - 移除 `temperature` 和 `maxOutputTokens` 配置
   - 进一步简化请求体结构

## 技术对比

### 认证方式对比

| 方面 | 修改前 (URL参数) | 修改后 (Bearer头部) |
|------|------------------|---------------------|
| **认证位置** | URL查询参数 | HTTP头部 |
| **格式** | `?key=<api_key>` | `Authorization: Bearer <api_key>` |
| **安全性** | 较低（URL可能被日志记录） | 较高（头部通常不被记录） |
| **标准性** | 非标准 | HTTP标准认证方式 |
| **兼容性** | 特定API | 通用REST API标准 |

### 请求格式对比

**修改前的完整请求**:
```
POST http://your-gemini-proxy.example.com:5321/v1beta/models/gemini-2.5-flash:generateContent?key=gp_your_gemini_key_...
Content-Type: application/json

{
  "contents": [{"parts": [{"text": "用户查询"}]}],
  "tools": [{"googleSearch": {}}],
  "generationConfig": {"temperature": 0.7, "maxOutputTokens": 2048}
}
```

**最终修改后的完整请求**:
```
POST http://your-gemini-proxy.example.com:5321/v1beta/models/gemini-2.5-flash:generateContent
Content-Type: application/json
Authorization: Bearer gp_your_gemini_key_...

{
  "contents": [{"parts": [{"text": "用户查询"}]}],
  "tools": [{"googleSearch": {}}]
}
```

## 与用户提供的curl示例对比

### 相同点 ✅
- 使用 `Authorization: Bearer` 头部认证
- 使用 `Content-Type: application/json`
- 基本的 `contents` 结构相同

### 差异点 📋
- **功能差异**: 项目版本包含联网工具，curl示例是纯文本生成
- **用途差异**: 项目专门用于联网AI查询，curl示例用于简单对话
- **参数差异**: 移除generationConfig后，项目版本更接近curl示例的简洁性

## 配置文件

当前使用的配置保持不变：

```yaml
gemini:
  api:
    key: ${GEMINI_API_KEY:your-gemini-api-key}
    base-url: ${GEMINI_BASE_URL:http://your-gemini-proxy.example.com:5321/v1beta}
    timeout: ${GEMINI_TIMEOUT:10000}
    retry-count: ${GEMINI_RETRY_COUNT:3}
```

## 优势分析

### 1. 安全性提升
- Bearer认证是HTTP标准认证方式
- API密钥不会出现在URL中，降低泄露风险
- 符合REST API最佳实践

### 2. 兼容性改善
- 与标准OAuth2/Bearer认证兼容
- 更容易与其他API网关集成
- 符合现代API设计规范

### 3. 维护性
- 代码更清晰，认证逻辑集中在头部设置
- 更容易进行认证相关的调试和监控
- 符合Spring Security等框架的标准模式

## 测试验证

### 1. 编译验证
- ✅ Maven编译成功，无语法错误
- ✅ 所有依赖正确解析

### 2. 功能验证建议
1. **联网查询测试**: 验证修改后的认证方式是否正常工作
2. **错误处理测试**: 验证认证失败时的错误处理
3. **日志检查**: 确认API密钥不会出现在请求URL日志中

## 影响评估

### 1. 向后兼容性
- ⚠️ **不兼容**: 如果API服务器只支持URL参数认证，需要确认服务器支持Bearer认证
- ✅ **配置不变**: 不需要修改配置文件
- ✅ **接口不变**: 对外接口和调用方式完全不变

### 2. 部署注意事项
- 确认目标API服务器支持Bearer认证
- 验证认证格式是否正确
- 监控认证相关的错误日志

## 最新修改 (2025-07-19 21:20)

### 移除generationConfig参数

根据用户进一步要求"移除'generationConfig'参数"，进行了以下修改：

#### 修改内容
```java
// 修改前：包含生成配置
requestBody.put("tools", List.of(tool));

// 添加生成配置
Map<String, Object> generationConfig = new HashMap<>();
generationConfig.put("temperature", 0.7);
generationConfig.put("maxOutputTokens", 2048);
requestBody.put("generationConfig", generationConfig);

return requestBody;

// 修改后：移除生成配置
requestBody.put("tools", List.of(tool));

return requestBody;
```

#### 效果
- **简化请求体**: 移除了temperature和maxOutputTokens配置
- **更接近标准**: 与用户提供的curl示例更加接近
- **减少复杂性**: 让API使用默认的生成参数

#### 最终请求体结构
```json
{
  "contents": [{"parts": [{"text": "用户查询"}]}],
  "tools": [{"googleSearch": {}}]
}
```

## 修改完成时间

- **认证格式修改**: 2025年7月19日 21:14 (北京时间)
- **移除generationConfig**: 2025年7月19日 21:20 (北京时间)

## 后续建议

1. **测试验证**: 在测试环境验证新的认证方式
2. **监控设置**: 监控认证失败的情况
3. **文档更新**: 更新API调用相关的技术文档
4. **错误处理**: 考虑添加更详细的认证错误处理逻辑
